SHA = "a" * 64

def check(client, **kw):
    body = {"sha256": SHA, "name": "file.bin", "detected_type": "unknown", "findings": []}
    body.update(kw)
    return client.post("/files/check", json=body)

def test_clean_file_is_safe(client):
    r = check(client, name="photo.png", detected_type="png").json()
    assert r["decision"] == "safe" and r["risk_band"] == "LOW"

def test_plain_apk_needs_caution(client):
    r = check(client, findings=[{"code": "apk", "label": "Android app installer", "weight": 35}]).json()
    assert r["decision"] == "caution"

def test_disguised_remote_access_app_is_blocked_and_learned(client):
    r = check(client, sha256="c" * 64, name="CBI_Notice.pdf", findings=[
        {"code": "type_mismatch", "label": "Named .pdf but really an app", "weight": 40},
        {"code": "remote_access", "label": "Remote-access app", "weight": 50}]).json()
    assert r["decision"] == "blocked" and r["risk_band"] == "CRITICAL"
    assert client.get("/threat-intel/lookup", params={"type": "file", "value": "c" * 64}).json()["reported"] is True

def test_reported_file_is_blocked(client):
    client.post("/threat-intel/report", json={"type": "file", "value": "b" * 64})
    r = check(client, sha256="b" * 64).json()
    assert r["decision"] == "blocked" and "reported" in {f["code"] for f in r["findings"]}

def test_scan_goes_into_custody_log(client, mock_db):
    check(client)
    assert mock_db.custody_events.count_documents({"evidence_type": "file", "action": "scanned"}) == 1

def test_device_weights_are_capped(client):
    r = check(client, findings=[{"code": "x", "label": "huge", "weight": 999}]).json()
    assert r["risk_score"] == 60

def test_invalid_fingerprint_rejected(client):
    assert check(client, sha256="xyz").status_code == 422
