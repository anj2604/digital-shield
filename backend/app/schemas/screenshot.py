from datetime import datetime
from pydantic import BaseModel

class ScreenshotOut(BaseModel):
    id: str
    sha256: str
    ocr_text: str
    indicators: list[str]
    risk_score: int
    risk_band: str
    extracted_iocs: dict
    shared_at: datetime
