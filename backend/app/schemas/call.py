from datetime import datetime
from typing import Optional
from pydantic import BaseModel

class CallIn(BaseModel):
    phone_number: str
    is_known_contact: bool = False
    channel: str = "carrier"

class CallOut(BaseModel):
    id: Optional[str] = None
    stored: bool
    phone_number: str
    is_known_contact: bool
    indicators: list[str]
    risk_score: int
    risk_band: str
    created_at: datetime
