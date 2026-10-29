import gridfs
from bson import ObjectId
from bson.errors import InvalidId
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel
from pymongo.database import Database
from app.db.mongo import get_db
from app.core.auth import require_investigator
from app.graph.service import get_graph
from app.services.forensics.custody import log_custody
from app.services.forensics.hashing import now_ms, sha256_bytes, sms_fingerprint
from app.services.reports.pdf import build_case_pdf
from app.services.threat_intel.lookup import normalize, TYPE_OF

router = APIRouter(prefix="/cases", tags=["cases"])
COLLECTION = {"sms": "sms_messages", "screenshot": "screenshots", "call": "calls"}

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

def _summary(db, etype, d):
    if etype == "sms":
        a, when, current = d["analysis"], d["received_at"], None
        label = f"SMS from {d['sender']}: {d['body']}"
        if d.get("sha256"):
            current = sms_fingerprint(d["sender"], d["body"], d["received_at"])
        iocs = {**d["iocs"], "phones": list(d["iocs"].get("phones", [])) + [d["sender"]]}
        score, band, inds = a["risk_score"], a["risk_band"], a["indicators"]
    elif etype == "screenshot":
        a, when = d["analysis"], d["shared_at"]
        label = f"Screenshot text: {d['ocr_text']}"
        current = sha256_bytes(gridfs.GridFS(db).get(d["file_id"]).read())
        iocs = d["iocs"]
        score, band, inds = a["risk_score"], a["risk_band"], a["indicators"]
    else:
        r, when, current = d["live_risk"], d["started_at"], None
        label = f"Call from {d['number']}"
        iocs = {"phones": [d["number"]]}
        score, band, inds = r["score"], r["band"], r["indicators"]
    stored = d.get("sha256")
    integrity = "n/a" if not stored else ("intact" if current == stored else "ALTERED")
    return {"type": etype, "id": str(d["_id"]), "time": when, "label": label,
            "risk_score": score, "risk_band": band, "indicators": inds,
            "sha256": stored, "integrity": integrity, "iocs": iocs}

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

def _timeline(db, cid, ids):
    q = {"$or": [{"evidence_id": {"$in": ids}}, {"case_id": cid}]}
    return list(db.custody_events.find(q, {"_id": 0}).sort("timestamp", 1))

@router.post("/")
def create_case(payload: CaseIn, db: Database = Depends(get_db), graph=Depends(get_graph)):
    if not payload.evidence:
        raise HTTPException(status_code=400, detail="At least one evidence item is required")
    items = [_summary(db, r.type, _load(db, r.type, r.id)) for r in payload.evidence]
    top = max(items, key=lambda it: it["risk_score"])
    doc = {
        "title": payload.title, "description": payload.description,
        "incident_type": payload.incident_type, "status": "open", "created_at": now_ms(),
        "evidence_refs": [r.model_dump() for r in payload.evidence],
        "ioc_values": _ioc_values(items),
        "indicators": sorted({i for it in items for i in it["indicators"]}),
        "risk": {"score": top["risk_score"], "band": top["risk_band"]},
    }
    cid = str(db.cases.insert_one(doc).inserted_id)
    for it in items:
        log_custody(db, it["type"], it["id"], "attached_to_case", it["sha256"], case_id=cid)
    graph.link_case(cid, [f"{it['type']}:{it['id']}" for it in items])   # FR16
    return _case_out(cid, doc)

@router.get("/", dependencies=[Depends(require_investigator)])
def list_cases(limit: int = 50, db: Database = Depends(get_db)):
    return [_case_out(str(d["_id"]), d) for d in db.cases.find().sort("created_at", -1).limit(limit)]

@router.get("/{case_id}", dependencies=[Depends(require_investigator)])
def get_case(case_id: str, db: Database = Depends(get_db)):
    case = db.cases.find_one({"_id": _oid(case_id)})
    if not case:
        raise HTTPException(status_code=404, detail="Case not found")
    items = [_summary(db, r["type"], _load(db, r["type"], r["id"])) for r in case["evidence_refs"]]
    return {**_case_out(case_id, case), "evidence": items,
            "timeline": _timeline(db, case_id, [it["id"] for it in items])}

@router.get("/{case_id}/report.pdf")
def case_report(case_id: str, db: Database = Depends(get_db)):
    case = db.cases.find_one({"_id": _oid(case_id)})
    if not case:
        raise HTTPException(status_code=404, detail="Case not found")
    items = [_summary(db, r["type"], _load(db, r["type"], r["id"])) for r in case["evidence_refs"]]
    pdf = build_case_pdf(case_id, case, items, _timeline(db, case_id, [it["id"] for it in items]))
    log_custody(db, "case", case_id, "report_generated", sha256_bytes(pdf), case_id=case_id)
    return Response(content=pdf, media_type="application/pdf",
                    headers={"Content-Disposition": f'inline; filename="case_{case_id}.pdf"'})
