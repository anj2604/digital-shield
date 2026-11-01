import hashlib

CHUNK1 = b"This is a CBI officer. You are under digital arrest. Do not disconnect the call."
CHUNK2 = b"Transfer 50000 rupees immediately to clear your name."

def _call(client, number="9333333333"):
    return client.post("/calls/", json={"phone_number": number}).json()["id"]

def _chunk(client, call_id, seq, data, sha=None):
    return client.post(f"/calls/{call_id}/chunks",
                       files={"file": (f"c{seq}.wav", data, "audio/wav")},
                       data={"seq": str(seq), "sha256": sha or hashlib.sha256(data).hexdigest(), "duration_s": "10"})

def test_chunk_raises_live_risk(client):
    cid = _call(client)
    r = _chunk(client, cid, 0, CHUNK1).json()
    assert "authority_impersonation" in r["new_indicators"]
    assert r["live_risk"]["band"] in ("HIGH", "CRITICAL")

def test_chunks_accumulate_to_critical(client):
    cid = _call(client)
    _chunk(client, cid, 0, CHUNK1)
    _chunk(client, cid, 1, CHUNK2)
    live = client.get(f"/calls/{cid}/live").json()
    assert live["live_risk"]["band"] == "CRITICAL" and live["chunk_count"] == 2
    assert len(live["chunks"]) == 2

def test_tampered_chunk_rejected(client, mock_db):
    cid = _call(client)
    assert _chunk(client, cid, 0, CHUNK1, sha="0" * 64).status_code == 400
    assert mock_db.audio_chunks.count_documents({}) == 0

def test_chunk_for_unknown_call_is_404(client):
    assert _chunk(client, "000000000000000000000000", 0, CHUNK1).status_code == 404

def test_chunk_goes_into_custody_log(client, mock_db):
    cid = _call(client)
    _chunk(client, cid, 0, CHUNK1)
    assert mock_db.custody_events.count_documents({"evidence_type": "audio_chunk", "action": "received"}) == 1

def test_recording_is_split_into_15s_windows(client):
    data = (CHUNK1 + b"|" + b"Your Aadhaar is linked to money laundering." + b"|" + CHUNK2)
    r = client.post("/calls/recording", files={"file": ("rec.m4a", data, "audio/mp4")},
                    data={"phone_number": "9444455555"}).json()
    assert r["chunks"] == 2                       # segments at 0 s, 8 s -> window 0; 16 s -> window 1
    assert r["live_risk"]["band"] == "CRITICAL"
    assert r["timeline"][0]["score_after"] <= r["timeline"][1]["score_after"]
