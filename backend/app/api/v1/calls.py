from datetime import datetime, timezone
from fastapi import APIRouter, Depends
from pymongo.database import Database
from app.db.mongo import get_db
from app.schemas.call import CallIn, CallOut
from app.services.threat_intel.lookup import is_reported
from app.services.risk_engine.scorer import calculate_risk, risk_band

router = APIRouter(prefix="/calls", tags=["calls"])

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
    if is_reported(payload.phone_number):
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
    return {
        "id": str(inserted.inserted_id), "stored": True,
        "phone_number": payload.phone_number, "is_known_contact": False,
        "indicators": indicators, "risk_score": score, "risk_band": band,
        "created_at": now,
    }
