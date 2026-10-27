from datetime import datetime, timezone

def log_custody(db, evidence_type, evidence_id, action, sha256, actor="system", case_id=None):
    db.custody_events.insert_one({
        "evidence_type": evidence_type,
        "evidence_id": str(evidence_id),
        "case_id": case_id,
        "action": action,
        "actor": actor,
        "timestamp": datetime.now(timezone.utc),
        "sha256": sha256,
    })
