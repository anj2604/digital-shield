import io
import qrcode
from fastapi import APIRouter, Depends
from fastapi.responses import Response
from pydantic import BaseModel
from pymongo.database import Database
from app.db.mongo import get_db
from app.services.forensics.hashing import now_ms
from app.services.links.scanner import scan, apply_osint
from app.services.osint.lookup import get_osint

router = APIRouter(prefix="/links", tags=["links"])

class ScanIn(BaseModel):
    input: str
    source: str = "link"   # "link" or "qr"

@router.post("/scan")
def scan_link(payload: ScanIn, db: Database = Depends(get_db), osint=Depends(get_osint)):
    result = scan(payload.input, db)
    if result["kind"] == "url" and not any(f["code"] == "ip_host" for f in result["findings"]):
        try:
            result = apply_osint(result, osint.enrich(result["host"]))   # FR12
        except Exception:
            pass   # OSINT is extra evidence; the scan still works without it
    db.link_scans.insert_one({**result, "source": payload.source, "scanned_at": now_ms()})
    return result

@router.get("/test-qr")
def test_qr(data: str = "upi://pay?pa=fraud@upi&pn=CBI%20Cyber%20Cell&am=50000&tn=Clear%20your%20case"):
    """Demo helper: shows a QR code you can screenshot on the emulator."""
    buf = io.BytesIO()
    qrcode.make(data).save(buf, format="PNG")
    return Response(content=buf.getvalue(), media_type="image/png")
