import hashlib

SCAM_AUDIO = b"This is a CBI officer. You are under digital arrest. Pay to fraud@upi now."

def _chunk(client, cid, seq, data):
    return client.post(f"/calls/{cid}/chunks", files={"file": (f"c{seq}.wav", data, "audio/wav")},
                       data={"seq": str(seq), "sha256": hashlib.sha256(data).hexdigest(), "duration_s": "10"})

def _recorded_call(client):
    cid = client.post("/calls/", json={"phone_number": "9333333333"}).json()["id"]
    _chunk(client, cid, 0, SCAM_AUDIO)
    return cid

def test_recorded_calls_are_listed(client):
    cid = _recorded_call(client)
    rec = client.get("/calls/recorded").json()
    assert rec[0]["id"] == cid and rec[0]["chunks"] == 1 and "CBI" in rec[0]["preview"]

def test_link_scans_are_listed_with_id(client):
    r = client.post("/links/scan", json={"input": "https://sbi-kyc-verify.xyz/login"}).json()
    assert r["id"] and client.get("/links/scans").json()[0]["id"] == r["id"]

def test_case_with_recording_and_link(client):
    cid = _recorded_call(client)
    lid = client.post("/links/scan", json={"input": "https://sbi-kyc-verify.xyz/login"}).json()["id"]
    case = client.post("/cases/", json={"evidence": [{"type": "recording", "id": cid}, {"type": "link", "id": lid}]}).json()
    assert case["evidence_count"] == 2
    assert "phone:9333333333" in case["ioc_values"] and "upi:fraud@upi" in case["ioc_values"]
    assert "url:https://sbi-kyc-verify.xyz/login" in case["ioc_values"]
    detail = client.get(f"/cases/{case['id']}").json()
    assert {e["integrity"] for e in detail["evidence"]} == {"intact"}
    assert client.get(f"/cases/{case['id']}/report.pdf").content.startswith(b"%PDF")

def test_tampered_recording_shows_altered(client, mock_db):
    cid = _recorded_call(client)
    case = client.post("/cases/", json={"evidence": [{"type": "recording", "id": cid}]}).json()
    mock_db.audio_chunks.update_many({}, {"$set": {"sha256": "0" * 64}})
    assert client.get(f"/cases/{case['id']}").json()["evidence"][0]["integrity"] == "ALTERED"
