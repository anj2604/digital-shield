from datetime import datetime
from typing import Optional
from pydantic import BaseModel

class SmsIn(BaseModel):
    sender: str
    body: str
    is_known_contact: bool = False

class SmsOut(BaseModel):
    id: Optional[str] = None
    stored: bool
    sender: str
    body: str
    received_at: datetime
    indicators: list[str]
    risk_score: int
    risk_band: str
    extracted_iocs: dict
