SCAM = "This is CBI. You will be arrested. Transfer Rs 50000 immediately to fraud@upi and do not tell anyone."

def _sms(client):
    return client.post("/sms/", json={"sender": "9444444444", "body": SCAM}).json()

def _shot(client):
    return client.post("/screenshots/", files={"file": ("s.png", b"img-bytes", "image/png")},
                       data={"ocr_text": SCAM}).json()

def test_case_collects_evidence_and_iocs(client, mock_db):
    sms, shot = _sms(client), _shot(client)
    case = client.post("/cases/", json={"title": "Test case", "evidence": [
        {"type": "sms", "id": sms["id"]}, {"type": "screenshot", "id": shot["id"]}]}).json()
    assert case["evidence_count"] == 2 and case["risk_band"] == "CRITICAL"
    assert "upi:fraud@upi" in case["ioc_values"] and "phone:9444444444" in case["ioc_values"]
    assert mock_db.custody_events.count_documents({"action": "attached_to_case", "case_id": case["id"]}) == 2

def test_case_report_is_a_pdf(client):
    sms = _sms(client)
    case = client.post("/cases/", json={"evidence": [{"type": "sms", "id": sms["id"]}]}).json()
    r = client.get(f"/cases/{case['id']}/report.pdf")
    assert r.status_code == 200 and r.headers["content-type"] == "application/pdf"
    assert r.content.startswith(b"%PDF")

def test_case_with_missing_evidence_is_rejected(client):
    r = client.post("/cases/", json={"evidence": [{"type": "sms", "id": "000000000000000000000000"}]})
    assert r.status_code == 404
