from datetime import datetime, timezone

SEED_NUMBERS = ["9999999999", "9888888888"]
TYPE_OF = {"phones": "phone", "emails": "email", "urls": "url", "upi_ids": "upi"}

def normalize(ioc_type, value):
    v = (value or "").strip().lower()
    if ioc_type == "phone":
        digits = "".join(ch for ch in v if ch.isdigit())
        return digits[-10:]
    return v

def report_ioc(db, ioc_type, value, source="user"):
    norm = normalize(ioc_type, value)
    if not norm:
        return None
    now = datetime.now(timezone.utc)
    db.iocs.update_one(
        {"type": ioc_type, "value": norm},
        {"$inc": {"report_count": 1}, "$setOnInsert": {"first_seen": now},
         "$set": {"last_seen": now}, "$addToSet": {"sources": source}},
        upsert=True,
    )
    return norm

def is_reported(db, ioc_type, value):
    norm = normalize(ioc_type, value)
    return bool(norm) and db.iocs.find_one(
        {"type": ioc_type, "value": norm, "report_count": {"$gte": 1}}) is not None

def any_reported(db, sender, iocs):
    if sender and is_reported(db, "phone", sender):
        return True
    return any(is_reported(db, t, v) for key, t in TYPE_OF.items() for v in iocs.get(key, []))

def learn_from(db, sender, iocs, source="auto"):
    if sender:
        report_ioc(db, "phone", sender, source)
    for key, t in TYPE_OF.items():
        for v in iocs.get(key, []):
            report_ioc(db, t, v, source)

def seed_defaults(db):
    for n in SEED_NUMBERS:
        if not db.iocs.find_one({"type": "phone", "value": n}):
            report_ioc(db, "phone", n, source="seed")
