from fastapi import APIRouter, Depends
from pymongo.database import Database
from app.db.mongo import get_db
from app.schemas.sms import SmsIn, SmsOut
from app.services.nlp.phrase_classifier import classify_text
from app.services.nlp.ioc_extractor import extract_iocs
from app.services.risk_engine.scorer import calculate_risk, risk_band
from app.services.forensics.hashing import now_ms, sms_fingerprint
from app.services.forensics.custody import log_custody

router = APIRouter(prefix="/sms", tags=["sms"])

@router.post("/", response_model=SmsOut)
def analyze_sms(payload: SmsIn, db: Database = Depends(get_db)):
    indicators = classify_text(payload.body)
    iocs = extract_iocs(payload.body)
    if iocs["urls"]:
        indicators.append("suspicious_url")
    score = calculate_risk(indicators)
    band = risk_band(score)
    now = now_ms()

    result = {
        "id": None, "stored": False, "sha256": None,
        "sender": payload.sender, "body": payload.body, "received_at": now,
        "indicators": indicators, "risk_score": score, "risk_band": band,
        "extracted_iocs": iocs,
    }

    # FR6: saved contacts are analysed and shown, but never written to the database
    if not payload.is_known_contact:
        digest = sms_fingerprint(payload.sender, payload.body, now)
        doc = {
            "sender": payload.sender,
            "body": payload.body,
            "received_at": now,
            "analysis": {"indicators": indicators, "risk_score": score, "risk_band": band},
            "iocs": iocs,
            "sha256": digest,
        }
        inserted = db.sms_messages.insert_one(doc)
        log_custody(db, "sms", inserted.inserted_id, "received", digest)  # FR14
        result.update({"id": str(inserted.inserted_id), "stored": True, "sha256": digest})
    return result

@router.get("/", response_model=list[SmsOut])
def list_sms(limit: int = 50, db: Database = Depends(get_db)):
    docs = db.sms_messages.find().sort("received_at", -1).limit(limit)
    return [
        {
            "id": str(d["_id"]), "stored": True, "sha256": d.get("sha256"),
            "sender": d["sender"], "body": d["body"], "received_at": d["received_at"],
            "indicators": d["analysis"]["indicators"],
            "risk_score": d["analysis"]["risk_score"],
            "risk_band": d["analysis"]["risk_band"],
            "extracted_iocs": d["iocs"],
        }
        for d in docs
    ]
