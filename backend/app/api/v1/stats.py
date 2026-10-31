from collections import Counter
from datetime import datetime, timedelta, timezone
from fastapi import APIRouter, Depends
from pymongo.database import Database
from app.core.auth import require_investigator
from app.db.mongo import get_db

router = APIRouter(tags=["stats"], dependencies=[Depends(require_investigator)])
BANDS = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]

def _naive(dt):
    return dt.astimezone(timezone.utc).replace(tzinfo=None) if dt.tzinfo else dt

def _items(db):
    """All stored evidence as (time, kind, band, score, indicators, label)."""
    for d in db.sms_messages.find():
        a = d["analysis"]
        yield d["received_at"], "sms", a["risk_band"], a["risk_score"], a["indicators"], f"SMS from {d['sender']}: {d['body'][:80]}"
    for d in db.calls.find():
        r = d["live_risk"]
        yield d["started_at"], "call", r["band"], r["score"], r["indicators"], f"Call from {d['number']}"
    for d in db.screenshots.find():
        a = d["analysis"]
        yield d["shared_at"], "screenshot", a["risk_band"], a["risk_score"], a["indicators"], f"Screenshot: {d['ocr_text'][:80]}"
    for d in db.file_scans.find():
        yield (d["scanned_at"], "file", d["risk_band"], d["risk_score"],
               [f["code"] for f in d.get("findings", [])], f"File: {d['name']}")

@router.get("/stats")
def stats(db: Database = Depends(get_db)):
    bands = {b: 0 for b in BANDS}
    for _, _, band, _, _, _ in _items(db):
        bands[band] = bands.get(band, 0) + 1
    return {
        "sms": db.sms_messages.count_documents({}),
        "calls": db.calls.count_documents({}),
        "screenshots": db.screenshots.count_documents({}),
        "files": db.file_scans.count_documents({}),
        "cases": db.cases.count_documents({}),
        "iocs": db.iocs.count_documents({}),
        "bands": bands,
    }

@router.get("/activity")
def activity(limit: int = 15, db: Database = Depends(get_db)):
    rows = [{"time": _naive(t), "kind": k, "band": b, "score": s, "label": l}
            for t, k, b, s, _, l in _items(db)]
    rows.sort(key=lambda r: r["time"], reverse=True)
    return rows[:limit]

@router.get("/stats/trends")
def trends(days: int = 7, db: Database = Depends(get_db)):
    days = max(1, min(days, 31))
    today = datetime.now(timezone.utc).date()
    keys = [(today - timedelta(days=days - 1 - i)).isoformat() for i in range(days)]
    per_day = {k: {b: 0 for b in BANDS} for k in keys}
    tactics = Counter()
    for t, _, band, _, inds, _ in _items(db):
        k = _naive(t).date().isoformat()
        if k in per_day:
            per_day[k][band] = per_day[k].get(band, 0) + 1
            tactics.update(i for i in inds if i != "unknown_sender")
    top_iocs = list(db.iocs.find({}, {"_id": 0, "type": 1, "value": 1, "report_count": 1})
                    .sort("report_count", -1).limit(5))
    return {
        "days": [{"day": k, **per_day[k]} for k in keys],
        "top_indicators": [{"indicator": k, "count": v} for k, v in tactics.most_common(6)],
        "top_iocs": top_iocs,
    }
