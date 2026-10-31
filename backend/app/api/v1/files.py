import httpx
from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from pymongo.database import Database
from app.core.auth import require_investigator
from app.core.config import settings
from app.db.mongo import get_db
from app.services.forensics.custody import log_custody
from app.services.forensics.hashing import now_ms
from app.services.risk_engine.scorer import risk_band
from app.services.threat_intel.lookup import is_reported, report_ioc

router = APIRouter(prefix="/files", tags=["files"])

VERDICT = {
    "safe": "No threats found. It is safe to open.",
    "caution": "Some warning signs. Open only if you trust the sender.",
    "blocked": "Dangerous. Do not open or install this file.",
}

class Finding(BaseModel):
    code: str
    label: str
    weight: int = 0

class FileCheckIn(BaseModel):
    sha256: str = Field(pattern=r"^[a-fA-F0-9]{64}$")
    name: str
    size: int = 0
    detected_type: str = "unknown"
    findings: list[Finding] = []
    source: str = "manual"      # "manual" (opened via the app) or "auto" (download watcher)

def virustotal(sha):
    """Optional: looks up the FINGERPRINT only. The file itself is never uploaded."""
    if not settings.vt_api_key:
        return None
    try:
        r = httpx.get(f"https://www.virustotal.com/api/v3/files/{sha}",
                      headers={"x-apikey": settings.vt_api_key}, timeout=8)
        if r.status_code == 404:
            return {"found": False}
        r.raise_for_status()
        s = r.json()["data"]["attributes"]["last_analysis_stats"]
        return {"found": True, "malicious": s.get("malicious", 0), "suspicious": s.get("suspicious", 0)}
    except Exception:
        return None

@router.post("/check")
def check_file(p: FileCheckIn, db: Database = Depends(get_db)):
    sha = p.sha256.lower()
    findings = [{"code": f.code[:40], "label": f.label[:200], "weight": max(0, min(f.weight, 60))}
                for f in p.findings][:20]
    if is_reported(db, "file", sha):
        findings.append({"code": "reported", "label": "This exact file was reported as malicious", "weight": 60})
    vt = virustotal(sha)
    if vt and vt.get("malicious", 0) > 0:
        findings.append({"code": "virustotal",
                         "label": f"{vt['malicious']} antivirus engines flag this file (VirusTotal)",
                         "weight": min(30 + 5 * vt["malicious"], 60)})
    score = min(sum(f["weight"] for f in findings), 100)
    band = risk_band(score)
    decision = "safe" if band == "LOW" else "caution" if band == "MEDIUM" else "blocked"
    doc = {"sha256": sha, "name": p.name[:255], "size": p.size, "detected_type": p.detected_type,
           "source": p.source, "findings": findings, "risk_score": score, "risk_band": band,
           "decision": decision, "virustotal": vt, "scanned_at": now_ms()}
    fid = db.file_scans.insert_one(doc).inserted_id
    log_custody(db, "file", fid, "scanned", sha)                     # FR14
    if band == "CRITICAL":
        report_ioc(db, "file", sha, source="auto")                   # FR11: learn the bad fingerprint
    return {"id": str(fid), "sha256": sha, "name": doc["name"], "risk_score": score, "risk_band": band,
            "decision": decision, "verdict": VERDICT[decision], "findings": findings, "virustotal": vt}

@router.get("/scans", dependencies=[Depends(require_investigator)])
def list_scans(limit: int = 50, db: Database = Depends(get_db)):
    docs = db.file_scans.find().sort("scanned_at", -1).limit(limit)
    return [{**{k: v for k, v in d.items() if k != "_id"}, "id": str(d["_id"])} for d in docs]
