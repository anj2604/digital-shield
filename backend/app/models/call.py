from sqlalchemy import Column, Integer, String, DateTime, JSON
from sqlalchemy.sql import func
from app.db.session import Base

class Call(Base):
    __tablename__ = "calls"

    id = Column(Integer, primary_key=True, index=True)
    phone_number = Column(String, index=True)
    is_known_contact = Column(Integer, default=0)  # 0 = false, 1 = true
    indicators = Column(JSON, default=list)
    risk_score = Column(Integer, default=0)
    risk_band = Column(String, default="LOW")
    created_at = Column(DateTime(timezone=True), server_default=func.now())
