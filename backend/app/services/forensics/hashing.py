import hashlib
from datetime import datetime, timezone

def now_ms() -> datetime:
    # MongoDB keeps milliseconds only, so trim now; otherwise re-hashing later would not match
    now = datetime.now(timezone.utc)
    return now.replace(microsecond=(now.microsecond // 1000) * 1000)

def iso_ms(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%dT%H:%M:%S.") + f"{dt.microsecond // 1000:03d}Z"

def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def sha256_text(text: str) -> str:
    return sha256_bytes(text.encode("utf-8"))

def sms_fingerprint(sender: str, body: str, received_at: datetime) -> str:
    return sha256_text(f"{sender}\n{body}\n{iso_ms(received_at)}")
