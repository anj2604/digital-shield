from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from app.db.session import SessionLocal
from app.models.call import Call
from app.schemas.call import CallIn, CallOut
from app.services.threat_intel.lookup import is_reported
from app.services.risk_engine.scorer import calculate_risk, risk_band

router = APIRouter(prefix="/calls", tags=["calls"])

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

@router.post("/", response_model=CallOut)
def analyze_call(payload: CallIn, db: Session = Depends(get_db)):
    indicators = []

    if payload.is_known_contact:
        score = 0
        band = "LOW"
    else:
        indicators.append("unknown_sender")
        if is_reported(payload.phone_number):
            indicators.append("reported_ioc")
        score = calculate_risk(indicators)
        band = risk_band(score)

    record = Call(
        phone_number=payload.phone_number,
        is_known_contact=1 if payload.is_known_contact else 0,
        indicators=indicators,
        risk_score=score,
        risk_band=band,
    )
    db.add(record)
    db.commit()
    db.refresh(record)
    return record
