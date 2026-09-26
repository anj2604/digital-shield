from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from app.db.session import SessionLocal
from app.models.sms_message import SmsMessage
from app.schemas.sms import SmsIn, SmsOut
from app.services.nlp.phrase_classifier import classify_text
from app.services.nlp.ioc_extractor import extract_iocs
from app.services.risk_engine.scorer import calculate_risk, risk_band

router = APIRouter(prefix="/sms", tags=["sms"])

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

@router.post("/", response_model=SmsOut)
def analyze_sms(payload: SmsIn, db: Session = Depends(get_db)):
    indicators = classify_text(payload.body)
    iocs = extract_iocs(payload.body)

    if iocs["urls"]:
        indicators.append("suspicious_url")

    score = calculate_risk(indicators)
    band = risk_band(score)

    record = SmsMessage(
        sender=payload.sender,
        body=payload.body,
        indicators=indicators,
        risk_score=score,
        risk_band=band,
        extracted_iocs=iocs,
    )
    db.add(record)
    db.commit()
    db.refresh(record)
    return record
