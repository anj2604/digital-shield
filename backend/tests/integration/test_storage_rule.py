import mongomock
from fastapi.testclient import TestClient
from app.main import app
from app.db.mongo import get_db

mock_db = mongomock.MongoClient()["test_shield"]
app.dependency_overrides[get_db] = lambda: mock_db
client = TestClient(app)

SCAM = "This is CBI. You will be arrested. Transfer Rs 50000 immediately to fraud@upi and do not tell anyone."

def setup_function():
    mock_db.sms_messages.delete_many({})
    mock_db.calls.delete_many({})

def test_unknown_sms_is_stored():
    data = client.post("/sms/", json={"sender": "9999999999", "body": SCAM, "is_known_contact": False}).json()
    assert data["stored"] is True
    assert data["risk_band"] == "CRITICAL"
    assert mock_db.sms_messages.count_documents({}) == 1

def test_saved_contact_sms_is_not_stored():
    data = client.post("/sms/", json={"sender": "9876543210", "body": SCAM, "is_known_contact": True}).json()
    assert data["stored"] is False and data["id"] is None
    assert data["risk_band"] == "CRITICAL"
    assert mock_db.sms_messages.count_documents({}) == 0

def test_unknown_call_is_stored():
    data = client.post("/calls/", json={"phone_number": "9999999999"}).json()
    assert data["stored"] is True and data["risk_score"] == 35
    assert mock_db.calls.count_documents({}) == 1

def test_saved_contact_call_is_not_stored():
    data = client.post("/calls/", json={"phone_number": "9876543210", "is_known_contact": True}).json()
    assert data["stored"] is False and data["risk_band"] == "LOW"
    assert mock_db.calls.count_documents({}) == 0
