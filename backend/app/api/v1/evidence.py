from typing import Optional
import gridfs
from bson import ObjectId
from bson.errors import InvalidId
from fastapi import APIRouter, Depends, HTTPException
from pymongo.database import Database
from app.db.mongo import get_db
from app.core.auth import require_investigator
from app.services.forensics.hashing import sha256_bytes, sms_fingerprint
from app.services.forensics.custody import log_custody

router = APIRouter(prefix="/evidence", tags=["evidence"])

def _oid(value: str) -> ObjectId:
    try:
        return ObjectId(value)
    except InvalidId:
        raise HTTPException(status_code=404, detail="Evidence not found")

@router.get("/verify/{evidence_type}/{evidence_id}")
def verify(evidence_type: str, evidence_id: str, db: Database = Depends(get_db)):
    oid = _oid(evidence_id)
    if evidence_type == "screenshot":
        d = db.screenshots.find_one({"_id": oid})
        if not d:
            raise HTTPException(status_code=404, detail="Evidence not found")
        current = sha256_bytes(gridfs.GridFS(db).get(d["file_id"]).read())
    elif evidence_type == "sms":
        d = db.sms_messages.find_one({"_id": oid})
        if not d:
            raise HTTPException(status_code=404, detail="Evidence not found")
        if "sha256" not in d:
            return {"evidence_type": "sms", "evidence_id": evidence_id, "intact": None,
                    "detail": "Stored before hashing was added"}
        current = sms_fingerprint(d["sender"], d["body"], d["received_at"])
    else:
        raise HTTPException(status_code=400, detail="evidence_type must be sms or screenshot")

    intact = current == d["sha256"]
    log_custody(db, evidence_type, evidence_id, "verified" if intact else "verification_failed", current)
    return {"evidence_type": evidence_type, "evidence_id": evidence_id,
            "stored_sha256": d["sha256"], "current_sha256": current, "intact": intact}

@router.get("/custody", dependencies=[Depends(require_investigator)])
def custody(evidence_id: Optional[str] = None, limit: int = 100, db: Database = Depends(get_db)):
    query = {"evidence_id": evidence_id} if evidence_id else {}
    return list(db.custody_events.find(query, {"_id": 0}).sort("timestamp", 1).limit(limit))
