SCAM = "This is CBI. You will be arrested. Transfer Rs 50000 immediately to fraud@upi and do not tell anyone."

def test_reported_upi_raises_sms_risk(client):
    body = "Pay a fine to helpdesk@upi today"
    first = client.post("/sms/", json={"sender": "9111111111", "body": body}).json()
    assert "reported_ioc" not in first["indicators"]
    client.post("/threat-intel/report", json={"type": "upi", "value": "helpdesk@upi"})
    second = client.post("/sms/", json={"sender": "9111111111", "body": body}).json()
    assert "reported_ioc" in second["indicators"]

def test_critical_sms_teaches_threat_intel(client):
    client.post("/sms/", json={"sender": "9222222222", "body": SCAM})
    assert client.get("/threat-intel/lookup", params={"type": "upi", "value": "fraud@upi"}).json()["reported"] is True
    assert client.get("/threat-intel/lookup", params={"type": "phone", "value": "+91 92222 22222"}).json()["reported"] is True

def test_unknown_value_is_not_reported(client):
    assert client.get("/threat-intel/lookup", params={"type": "phone", "value": "9000000000"}).json()["reported"] is False
