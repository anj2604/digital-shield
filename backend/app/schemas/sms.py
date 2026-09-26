from pydantic import BaseModel
from datetime import datetime

class SmsIn(BaseModel):
    sender: str
    body: str

class SmsOut(BaseModel):
    id: int
    sender: str
    body: str
    received_at: datetime
    indicators: list[str]
    risk_score: int
    risk_band: str
    extracted_iocs: dict

    class Config:
        from_attributes = True
