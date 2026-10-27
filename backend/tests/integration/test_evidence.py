import hashlib
from bson import ObjectId

SCAM = "This is CBI. You will be arrested. Transfer Rs 50000 immediately to fraud@upi and do not tell anyone."

def test_sms_is_hashed_logged_and_verifiable(client, mock_db):
    data = client.post("/sms/", json={"sender": "9999999999", "body": SCAM, "is_known_contact": False}).json()
    assert len(data["sha256"]) == 64
    assert mock_db.custody_events.count_documents({"evidence_id": data["id"], "action": "received"}) == 1
    assert client.get(f"/evidence/verify/sms/{data['id']}").json()["intact"] is True

def test_tampered_sms_fails_verification(client, mock_db):
    data = client.post("/sms/", json={"sender": "9999999999", "body": SCAM, "is_known_contact": False}).json()
    mock_db.sms_messages.update_one({"_id": ObjectId(data["id"])}, {"$set": {"body": "edited later"}})
    assert client.get(f"/evidence/verify/sms/{data['id']}").json()["intact"] is False

def test_screenshot_upload_hash_and_verify(client):
    img = b"fake-png-bytes"
    data = client.post("/screenshots/", files={"file": ("shot.png", img, "image/png")},
                       data={"ocr_text": SCAM}).json()
    assert data["risk_band"] == "CRITICAL"
    assert data["sha256"] == hashlib.sha256(img).hexdigest()
    assert client.get(f"/evidence/verify/screenshot/{data['id']}").json()["intact"] is True
    assert client.get(f"/screenshots/{data['id']}/image").content == img
