from fastapi import APIRouter, Depends
from pymongo.database import Database
from app.db.mongo import get_db
from app.core.auth import require_investigator

router = APIRouter(tags=["stats"])

@router.get("/stats", dependencies=[Depends(require_investigator)])
def stats(db: Database = Depends(get_db)):
    bands = {"LOW": 0, "MEDIUM": 0, "HIGH": 0, "CRITICAL": 0}
    for d in db.sms_messages.find({}, {"analysis.risk_band": 1}):
        bands[d["analysis"]["risk_band"]] += 1
    for d in db.screenshots.find({}, {"analysis.risk_band": 1}):
        bands[d["analysis"]["risk_band"]] += 1
    for d in db.calls.find({}, {"live_risk.band": 1}):
        bands[d["live_risk"]["band"]] += 1
    return {
        "sms": db.sms_messages.count_documents({}),
        "calls": db.calls.count_documents({}),
        "screenshots": db.screenshots.count_documents({}),
        "cases": db.cases.count_documents({}),
        "iocs": db.iocs.count_documents({}),
        "bands": bands,
    }
