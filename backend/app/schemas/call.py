from pydantic import BaseModel
from datetime import datetime

class CallIn(BaseModel):
    phone_number: str
    is_known_contact: bool = False

class CallOut(BaseModel):
    id: int
    phone_number: str
    is_known_contact: bool
    indicators: list[str]
    risk_score: int
    risk_band: str
    created_at: datetime

    class Config:
        from_attributes = True
