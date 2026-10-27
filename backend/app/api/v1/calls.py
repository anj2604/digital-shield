from datetime import datetime, timezone
from bson import ObjectId
from bson.errors import InvalidId
from fastapi import APIRouter, Depends, HTTPException
from pymongo.database import Database
from app.db.mongo import get_db
from app.schemas.call import CallIn, CallOut
from app.services.threat_intel.lookup import is_reported
from app.services.risk_engine.scorer import calculate_risk, risk_band

router = APIRouter(prefix="/calls", tags=["calls"])

def _to_out(d):
    return {
        "id": str(d["_id"]), "stored": True,
        "phone_number": d["number"], "is_known_contact": False,
        "indicators": d["live_risk"]["indicators"],
        "risk_score": d["live_risk"]["score"],
        "risk_band": d["live_risk"]["band"],
        "created_at": d["started_at"],
    }

@router.post("/", response_model=CallOut)
def analyze_call(payload: CallIn, db: Database = Depends(get_db)):
    now = datetime.now(timezone.utc)

    # FR6: saved contacts are let through and never stored or recorded
    if payload.is_known_contact:
        return {
            "id": None, "stored": False, "phone_number": payload.phone_number,
            "is_known_contact": True, "indicators": [], "risk_score": 0,
            "risk_band": "LOW", "created_at": now,
        }

    indicators = ["unknown_sender"]
    if is_reported(db, "phone", payload.phone_number):
        indicators.append("reported_ioc")
    score = calculate_risk(indicators)
    band = risk_band(score)

    doc = {
        "number": payload.phone_number,
        "channel": payload.channel,
        "is_known_contact": False,
        "started_at": now,
        "ended_at": None,
        "chunk_count": 0,
        "live_risk": {"score": score, "band": band, "indicators": indicators},
    }
    inserted = db.calls.insert_one(doc)
    doc["_id"] = inserted.inserted_id
    return _to_out(doc)

@router.get("/", response_model=list[CallOut])
def list_calls(limit: int = 50, db: Database = Depends(get_db)):
    return [_to_out(d) for d in db.calls.find().sort("started_at", -1).limit(limit)]

@router.get("/active")
def active_call(db: Database = Depends(get_db)):
    d = db.calls.find_one({"ended_at": None}, sort=[("started_at", -1)])
    return {"active": d is not None, "call": _to_out(d) if d else None}

@router.post("/{call_id}/end")
def end_call(call_id: str, db: Database = Depends(get_db)):
    try:
        oid = ObjectId(call_id)
    except InvalidId:
        raise HTTPException(status_code=404, detail="Call not found")
    res = db.calls.update_one({"_id": oid, "ended_at": None},
                              {"$set": {"ended_at": datetime.now(timezone.utc)}})
    if res.matched_count == 0:
        raise HTTPException(status_code=404, detail="Call not found or already ended")
    return {"id": call_id, "ended": True}
