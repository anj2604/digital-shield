import io
import qrcode
from fastapi import APIRouter, Depends
from fastapi.responses import Response
from pydantic import BaseModel
from pymongo.database import Database
from app.db.mongo import get_db
from app.graph.service import get_graph
from app.services.forensics.custody import log_custody
from app.services.forensics.hashing import now_ms, text_fingerprint
from app.services.links.scanner import scan, apply_osint
from app.services.osint.lookup import get_osint

router = APIRouter(prefix="/links", tags=["links"])

class ScanIn(BaseModel):
    input: str
    source: str = "link"   # "link" or "qr"

def link_iocs(result):
    if result.get("upi") and result["upi"].get("vpa"):
        return {"upi_ids": [result["upi"]["vpa"]]}
    if result.get("kind") == "url":
        return {"urls": [result["input"]]}
    return {}

@router.post("/scan")
def scan_link(payload: ScanIn, db: Database = Depends(get_db), osint=Depends(get_osint), graph=Depends(get_graph)):
    result = scan(payload.input, db)
    if result["kind"] == "url" and not any(f["code"] == "ip_host" for f in result["findings"]):
        try:
            result = apply_osint(result, osint.enrich(result["host"]))   # FR12
        except Exception:
            pass
    at = now_ms()
    digest = text_fingerprint(result["input"], at)
    lid = db.link_scans.insert_one({**result, "source": payload.source, "scanned_at": at, "sha256": digest}).inserted_id
    log_custody(db, "link", lid, "received", digest)                      # FR14
    iocs = link_iocs(result)
    if iocs:
        graph.link("link", str(lid), result["risk_band"], result["risk_score"], iocs)   # FR16
    return {**result, "id": str(lid)}

@router.get("/scans")
def list_scans(limit: int = 100, db: Database = Depends(get_db)):
    docs = db.link_scans.find().sort("scanned_at", -1).limit(limit)
    return [{"id": str(d["_id"]), "input": d["input"], "kind": d["kind"], "host": d.get("host"),
             "risk_score": d["risk_score"], "risk_band": d["risk_band"], "scanned_at": d["scanned_at"],
             "verdict": d.get("verdict", "")} for d in docs]

@router.get("/test-qr")
def test_qr(data: str = "upi://pay?pa=fraud@upi&pn=CBI%20Cyber%20Cell&am=50000&tn=Clear%20your%20case"):
    """Demo helper: shows a QR code you can screenshot on the emulator."""
    buf = io.BytesIO()
    qrcode.make(data).save(buf, format="PNG")
    return Response(content=buf.getvalue(), media_type="image/png")
