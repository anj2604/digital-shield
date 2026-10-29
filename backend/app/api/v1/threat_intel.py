from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from pymongo.database import Database
from app.db.mongo import get_db
from app.core.auth import require_investigator
from app.services.threat_intel.lookup import normalize, report_ioc

router = APIRouter(prefix="/threat-intel", tags=["threat-intel"])
ALLOWED = {"phone", "email", "url", "upi", "domain"}

class ReportIn(BaseModel):
    type: str
    value: str

@router.post("/report")
def report(payload: ReportIn, db: Database = Depends(get_db)):
    if payload.type not in ALLOWED:
        raise HTTPException(status_code=400, detail=f"type must be one of {sorted(ALLOWED)}")
    norm = report_ioc(db, payload.type, payload.value, source="user")
    if not norm:
        raise HTTPException(status_code=400, detail="Empty value")
    return db.iocs.find_one({"type": payload.type, "value": norm}, {"_id": 0})

@router.get("/lookup")
def lookup(type: str, value: str, db: Database = Depends(get_db)):
    norm = normalize(type, value)
    doc = db.iocs.find_one({"type": type, "value": norm}, {"_id": 0})
    return {"type": type, "value": norm,
            "reported": bool(doc and doc.get("report_count", 0) >= 1), "record": doc}

@router.get("/iocs", dependencies=[Depends(require_investigator)])
def list_iocs(limit: int = 100, db: Database = Depends(get_db)):
    return list(db.iocs.find({}, {"_id": 0}).sort("report_count", -1).limit(limit))
