import gridfs
from bson import ObjectId
from bson.errors import InvalidId
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from fastapi.responses import Response
from pymongo.database import Database
from app.db.mongo import get_db
from app.schemas.screenshot import ScreenshotOut
from app.services.nlp.phrase_classifier import classify_text
from app.services.nlp.ioc_extractor import extract_iocs
from app.services.risk_engine.scorer import calculate_risk, risk_band
from app.services.forensics.hashing import sha256_bytes, now_ms
from app.services.forensics.custody import log_custody

router = APIRouter(prefix="/screenshots", tags=["screenshots"])

def _to_out(d):
    return {
        "id": str(d["_id"]), "sha256": d["sha256"], "ocr_text": d["ocr_text"],
        "indicators": d["analysis"]["indicators"],
        "risk_score": d["analysis"]["risk_score"],
        "risk_band": d["analysis"]["risk_band"],
        "extracted_iocs": d["iocs"], "shared_at": d["shared_at"],
    }

@router.post("/", response_model=ScreenshotOut)
def upload_screenshot(file: UploadFile = File(...), ocr_text: str = Form(""),
                      db: Database = Depends(get_db)):
    data = file.file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty file")
    digest = sha256_bytes(data)
    content_type = file.content_type or "image/png"
    file_id = gridfs.GridFS(db).put(data, filename=file.filename or "screenshot", content_type=content_type)

    indicators = classify_text(ocr_text)
    iocs = extract_iocs(ocr_text)
    if iocs["urls"]:
        indicators.append("suspicious_url")
    score = calculate_risk(indicators)
    band = risk_band(score)

    doc = {
        "file_id": file_id, "content_type": content_type, "size": len(data),
        "sha256": digest, "ocr_text": ocr_text,
        "analysis": {"indicators": indicators, "risk_score": score, "risk_band": band},
        "iocs": iocs, "shared_at": now_ms(),
    }
    inserted = db.screenshots.insert_one(doc)
    doc["_id"] = inserted.inserted_id
    log_custody(db, "screenshot", inserted.inserted_id, "received", digest)  # FR14
    return _to_out(doc)

@router.get("/", response_model=list[ScreenshotOut])
def list_screenshots(limit: int = 50, db: Database = Depends(get_db)):
    return [_to_out(d) for d in db.screenshots.find().sort("shared_at", -1).limit(limit)]

@router.get("/{shot_id}/image")
def get_image(shot_id: str, db: Database = Depends(get_db)):
    try:
        oid = ObjectId(shot_id)
    except InvalidId:
        raise HTTPException(status_code=404, detail="Screenshot not found")
    d = db.screenshots.find_one({"_id": oid})
    if not d:
        raise HTTPException(status_code=404, detail="Screenshot not found")
    data = gridfs.GridFS(db).get(d["file_id"]).read()
    log_custody(db, "screenshot", shot_id, "viewed", sha256_bytes(data))
    return Response(content=data, media_type=d.get("content_type", "image/png"))
