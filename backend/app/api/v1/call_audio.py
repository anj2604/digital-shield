import os
import tempfile
import gridfs
from bson import ObjectId
from bson.errors import InvalidId
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from pymongo.database import Database
from app.db.mongo import get_db
from app.graph.service import get_graph
from app.services.forensics.custody import log_custody
from app.services.forensics.hashing import now_ms, sha256_bytes
from app.services.nlp.ioc_extractor import extract_iocs
from app.services.nlp.phrase_classifier import classify_text
from app.services.nlp.spoken import normalize_spoken
from app.services.risk_engine.scorer import calculate_risk, risk_band
from app.services.speech.transcriber import get_transcriber
from app.services.threat_intel.lookup import is_reported, learn_from

router = APIRouter(prefix="/calls", tags=["call audio"])
WINDOW_S = 15

def _oid(v):
    try:
        return ObjectId(v)
    except InvalidId:
        raise HTTPException(status_code=404, detail="Call not found")

def _with_temp(data: bytes, suffix: str, fn):
    fd, path = tempfile.mkstemp(suffix=suffix)
    try:
        os.write(fd, data)
        os.close(fd)
        return fn(path)
    finally:
        try:
            os.remove(path)
        except OSError:
            pass

def _apply(db, graph, call, new_indicators, iocs):
    """FR9: add the new indicators to the call and re-score it."""
    old = call["live_risk"]["indicators"]
    inds = old + [i for i in new_indicators if i not in old]
    score = calculate_risk(inds)
    band = risk_band(score)
    live = {"score": score, "band": band, "indicators": inds}
    db.calls.update_one({"_id": call["_id"]}, {"$set": {"live_risk": live}, "$inc": {"chunk_count": 1}})
    call["live_risk"] = live
    if band == "CRITICAL":
        learn_from(db, call["number"], iocs)                                         # FR11
    graph.link("call", str(call["_id"]), band, score, iocs, sender=call["number"])  # FR16
    return live

@router.post("/{call_id}/chunks")
def upload_chunk(call_id: str, file: UploadFile = File(...), seq: int = Form(...),
                 sha256: str = Form(...), duration_s: float = Form(10.0),
                 db: Database = Depends(get_db), graph=Depends(get_graph), stt=Depends(get_transcriber)):
    """FR8/FR9: one 10-15 s chunk of a live call."""
    call = db.calls.find_one({"_id": _oid(call_id)})
    if not call:
        raise HTTPException(status_code=404, detail="Call not found")
    data = file.file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty chunk")
    digest = sha256_bytes(data)
    if digest != sha256.lower():
        raise HTTPException(status_code=400, detail="Integrity check failed: the chunk changed in transit")

    file_id = gridfs.GridFS(db).put(data, filename=f"call_{call_id}_{seq}.wav",
                                    content_type=file.content_type or "audio/wav")
    text = normalize_spoken(_with_temp(data, ".wav", stt.transcribe))
    found, iocs = classify_text(text), extract_iocs(text)
    cid = db.audio_chunks.insert_one({
        "call_id": call_id, "seq": seq, "offset_s": seq * duration_s, "duration_s": duration_s,
        "file_id": file_id, "sha256": digest, "transcript": text, "indicators": found,
        "received_at": now_ms(), "source": "live",
    }).inserted_id
    log_custody(db, "audio_chunk", cid, "received", digest)                          # FR14
    live = _apply(db, graph, call, found, iocs)
    db.audio_chunks.update_one({"_id": cid}, {"$set": {"score_after": live["score"]}})
    return {"chunk_id": str(cid), "seq": seq, "transcript": text, "new_indicators": found, "live_risk": live}

@router.get("/{call_id}/live")
def live_status(call_id: str, db: Database = Depends(get_db)):
    """Polled by the phone during the call."""
    call = db.calls.find_one({"_id": _oid(call_id)})
    if not call:
        raise HTTPException(status_code=404, detail="Call not found")
    chunks = list(db.audio_chunks.find({"call_id": call_id},
                                       {"_id": 0, "seq": 1, "transcript": 1, "indicators": 1, "score_after": 1})
                  .sort("seq", 1))
    return {"call_id": call_id, "number": call["number"], "ended": call.get("ended_at") is not None,
            "live_risk": call["live_risk"], "chunk_count": call.get("chunk_count", 0), "chunks": chunks[-5:]}

@router.post("/recording")
def upload_recording(file: UploadFile = File(...), phone_number: str = Form(""),
                     db: Database = Depends(get_db), graph=Depends(get_graph), stt=Depends(get_transcriber)):
    """Fallback: a finished recording from the phone's own recorder, analysed in 15 s windows."""
    data = file.file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty recording")
    digest = sha256_bytes(data)
    ext = os.path.splitext(file.filename or "")[1] or ".m4a"
    file_id = gridfs.GridFS(db).put(data, filename=file.filename or "recording",
                                    content_type=file.content_type or "audio/mpeg")
    indicators = ["unknown_sender"]
    if phone_number and is_reported(db, "phone", phone_number):
        indicators.append("reported_ioc")
    score = calculate_risk(indicators)
    now = now_ms()
    call = {"number": phone_number or "unknown", "channel": "device_recording", "is_known_contact": False,
            "started_at": now, "ended_at": now, "chunk_count": 0,
            "live_risk": {"score": score, "band": risk_band(score), "indicators": indicators},
            "recording": {"file_id": file_id, "sha256": digest}}
    call["_id"] = db.calls.insert_one(call).inserted_id
    log_custody(db, "call_recording", call["_id"], "received", digest)

    windows = {}
    for start, _end, text in _with_temp(data, ext, stt.segments):
        windows.setdefault(int(start // WINDOW_S), []).append(text)
    timeline = []
    for w in sorted(windows):
        text = normalize_spoken(" ".join(windows[w]).strip())
        found, iocs = classify_text(text), extract_iocs(text)
        live = _apply(db, graph, call, found, iocs)
        db.audio_chunks.insert_one({
            "call_id": str(call["_id"]), "seq": w, "offset_s": w * WINDOW_S, "duration_s": WINDOW_S,
            "file_id": file_id, "sha256": digest, "transcript": text, "indicators": found,
            "score_after": live["score"], "received_at": now_ms(), "source": "device_recording",
        })
        timeline.append({"seq": w, "offset_s": w * WINDOW_S, "transcript": text,
                         "score_after": live["score"], "band_after": live["band"]})
    return {"call_id": str(call["_id"]), "chunks": len(timeline), "timeline": timeline, "live_risk": call["live_risk"]}


@router.get("/recorded")
def recorded_calls(limit: int = 100, db: Database = Depends(get_db)):
    docs = db.calls.find({"$or": [{"chunk_count": {"$gt": 0}}, {"recording": {"$exists": True}}]}) \
                   .sort("started_at", -1).limit(limit)
    out = []
    for d in docs:
        cid = str(d["_id"])
        first = db.audio_chunks.find_one({"call_id": cid, "transcript": {"$ne": ""}}, sort=[("seq", 1)])
        out.append({"id": cid, "number": d["number"], "chunks": d.get("chunk_count", 0),
                    "source": "device_recording" if d.get("recording") else "live",
                    "risk_score": d["live_risk"]["score"], "risk_band": d["live_risk"]["band"],
                    "started_at": d["started_at"], "preview": (first or {}).get("transcript", "")[:160]})
    return out
