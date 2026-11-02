import gridfs
from bson import ObjectId
from bson.errors import InvalidId
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel
from pymongo.database import Database
from app.core.auth import require_investigator
from app.db.mongo import get_db
from app.graph.service import get_graph
from app.services.forensics.custody import log_custody
from app.services.forensics.hashing import now_ms, sha256_bytes, sha256_text, sms_fingerprint, text_fingerprint
from app.services.nlp.ioc_extractor import extract_iocs
from app.services.reports.pdf import build_case_pdf
from app.services.threat_intel.lookup import normalize, TYPE_OF

router = APIRouter(prefix="/cases", tags=["cases"])
COLLECTION = {"sms": "sms_messages", "screenshot": "screenshots", "call": "calls",
              "recording": "calls", "link": "link_scans"}
GRAPH_TYPE = {"recording": "call"}   # a recorded call is the call's evidence node in the graph

class EvidenceRef(BaseModel):
    type: str
    id: str

class CaseIn(BaseModel):
    title: str = "Suspected digital arrest scam"
    description: str = ""
    incident_type: str = "digital_arrest"
    evidence: list[EvidenceRef]

def _oid(v):
    try:
        return ObjectId(v)
    except InvalidId:
        raise HTTPException(status_code=404, detail=f"Not found: {v}")

def _load(db, etype, eid):
    if etype not in COLLECTION:
        raise HTTPException(status_code=400, detail=f"Unknown evidence type: {etype}")
    d = db[COLLECTION[etype]].find_one({"_id": _oid(eid)})
    if not d:
        raise HTTPException(status_code=404, detail=f"{etype} {eid} not found")
    return d

def _merge_iocs(a, b):
    out = {k: list(v) for k, v in a.items()}
    for k, v in b.items():
        out.setdefault(k, [])
        out[k] += [x for x in v if x not in out[k]]
    return out

def _integrity(stored, ok):
    return "n/a" if not stored else ("intact" if ok else "ALTERED")

def _summary(db, etype, d):
    extra_ids = []
    if etype == "sms":
        a, when = d["analysis"], d["received_at"]
        label = f"SMS from {d['sender']}: {d['body']}"
        stored = d.get("sha256")
        ok = stored and sms_fingerprint(d["sender"], d["body"], d["received_at"]) == stored
        iocs = _merge_iocs(d["iocs"], {"phones": [d["sender"]]})
        score, band, inds = a["risk_score"], a["risk_band"], a["indicators"]
    elif etype == "screenshot":
        a, when = d["analysis"], d["shared_at"]
        label = f"Screenshot text: {d['ocr_text']}"
        stored = d.get("sha256")
        ok = sha256_bytes(gridfs.GridFS(db).get(d["file_id"]).read()) == stored
        iocs = d["iocs"]
        score, band, inds = a["risk_score"], a["risk_band"], a["indicators"]
    elif etype == "recording":
        cid, r, when = str(d["_id"]), d["live_risk"], d["started_at"]
        chunks = list(db.audio_chunks.find({"call_id": cid}).sort("seq", 1))
        extra_ids = [str(c["_id"]) for c in chunks]
        transcript = " ".join(c.get("transcript", "") for c in chunks).strip()
        fs = gridfs.GridFS(db)
        if d.get("recording"):                      # the phone's own recording: one file
            stored = d["recording"]["sha256"]
            ok = sha256_bytes(fs.get(d["recording"]["file_id"]).read()) == stored
        elif chunks:                                # live recording: every chunk is re-checked
            stored = sha256_text("".join(c["sha256"] for c in chunks))
            ok = all(sha256_bytes(fs.get(c["file_id"]).read()) == c["sha256"] for c in chunks)
        else:
            stored, ok = None, False
        label = f"Recorded call from {d['number']} ({len(chunks)} part(s)): {transcript or '(no speech transcribed)'}"
        iocs = _merge_iocs(extract_iocs(transcript), {"phones": [d["number"]]})
        score, band, inds = r["score"], r["band"], r["indicators"]
    elif etype == "link":
        when, upi = d["scanned_at"], d.get("upi")
        label = (f"UPI QR paying {upi['vpa']} (Rs {upi.get('amount') or '-'})" if upi else f"Link: {d['input']}")
        stored = d.get("sha256")
        ok = stored and text_fingerprint(d["input"], d["scanned_at"]) == stored
        iocs = {"upi_ids": [upi["vpa"]]} if upi and upi.get("vpa") else ({"urls": [d["input"]]} if d.get("kind") == "url" else {})
        score, band, inds = d["risk_score"], d["risk_band"], [f["code"] for f in d.get("findings", [])]
    else:                                           # call without a recording
        r, when = d["live_risk"], d["started_at"]
        label = f"Call from {d['number']}"
        stored, ok = None, False
        iocs = {"phones": [d["number"]]}
        score, band, inds = r["score"], r["band"], r["indicators"]
    return {"type": etype, "id": str(d["_id"]), "time": when, "label": label,
            "risk_score": score, "risk_band": band, "indicators": inds,
            "sha256": stored, "integrity": _integrity(stored, ok), "iocs": iocs, "extra_ids": extra_ids}

def _ioc_values(items):
    vals = set()
    for it in items:
        for key, t in TYPE_OF.items():
            for raw in it["iocs"].get(key, []):
                v = normalize(t, raw)
                if v:
                    vals.add(f"{t}:{v}")
    return sorted(vals)

def _case_out(cid, doc):
    return {"id": cid, "title": doc["title"], "status": doc["status"],
            "incident_type": doc["incident_type"], "created_at": doc["created_at"],
            "evidence_count": len(doc["evidence_refs"]),
            "risk_score": doc["risk"]["score"], "risk_band": doc["risk"]["band"],
            "ioc_values": doc["ioc_values"], "indicators": doc["indicators"]}

def _timeline(db, cid, items):
    ids = [it["id"] for it in items] + [x for it in items for x in it["extra_ids"]]
    q = {"$or": [{"evidence_id": {"$in": ids}}, {"case_id": cid}]}
    return list(db.custody_events.find(q, {"_id": 0}).sort("timestamp", 1))

def _items(db, refs):
    return [_summary(db, r["type"], _load(db, r["type"], r["id"])) for r in refs]

@router.post("/")
def create_case(payload: CaseIn, db: Database = Depends(get_db), graph=Depends(get_graph)):
    if not payload.evidence:
        raise HTTPException(status_code=400, detail="At least one evidence item is required")
    refs = [r.model_dump() for r in payload.evidence]
    items = _items(db, refs)
    top = max(items, key=lambda it: it["risk_score"])
    doc = {
        "title": payload.title, "description": payload.description,
        "incident_type": payload.incident_type, "status": "open", "created_at": now_ms(),
        "evidence_refs": refs, "ioc_values": _ioc_values(items),
        "indicators": sorted({i for it in items for i in it["indicators"]}),
        "risk": {"score": top["risk_score"], "band": top["risk_band"]},
    }
    cid = str(db.cases.insert_one(doc).inserted_id)
    for it in items:
        log_custody(db, it["type"], it["id"], "attached_to_case", it["sha256"], case_id=cid)
    graph.link_case(cid, [f"{GRAPH_TYPE.get(it['type'], it['type'])}:{it['id']}" for it in items])   # FR16
    return _case_out(cid, doc)

@router.get("/", dependencies=[Depends(require_investigator)])
def list_cases(limit: int = 50, db: Database = Depends(get_db)):
    return [_case_out(str(d["_id"]), d) for d in db.cases.find().sort("created_at", -1).limit(limit)]

@router.get("/{case_id}", dependencies=[Depends(require_investigator)])
def get_case(case_id: str, db: Database = Depends(get_db)):
    case = db.cases.find_one({"_id": _oid(case_id)})
    if not case:
        raise HTTPException(status_code=404, detail="Case not found")
    items = _items(db, case["evidence_refs"])
    return {**_case_out(case_id, case),
            "evidence": [{k: v for k, v in it.items() if k != "extra_ids"} for it in items],
            "timeline": _timeline(db, case_id, items)}

@router.get("/{case_id}/report.pdf")
def case_report(case_id: str, db: Database = Depends(get_db)):
    case = db.cases.find_one({"_id": _oid(case_id)})
    if not case:
        raise HTTPException(status_code=404, detail="Case not found")
    items = _items(db, case["evidence_refs"])
    pdf = build_case_pdf(case_id, case, items, _timeline(db, case_id, items))
    log_custody(db, "case", case_id, "report_generated", sha256_bytes(pdf), case_id=case_id)
    return Response(content=pdf, media_type="application/pdf",
                    headers={"Content-Disposition": f'inline; filename="case_{case_id}.pdf"'})
