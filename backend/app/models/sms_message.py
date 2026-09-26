from sqlalchemy import Column, Integer, String, DateTime, JSON
from sqlalchemy.sql import func
from app.db.session import Base

class SmsMessage(Base):
    __tablename__ = "sms_messages"

    id = Column(Integer, primary_key=True, index=True)
    sender = Column(String, index=True)
    body = Column(String)
    received_at = Column(DateTime(timezone=True), server_default=func.now())
    indicators = Column(JSON, default=list)
    risk_score = Column(Integer, default=0)
    risk_band = Column(String, default="LOW")
    extracted_iocs = Column(JSON, default=dict)
