import hashlib, io, wave

def _wav(seconds=1, rate=8000):
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate)
        w.writeframes(b"\x00\x00" * rate * seconds)
    return buf.getvalue()

def _recorded(client, parts=2):
    cid = client.post("/calls/", json={"phone_number": "9444444444"}).json()["id"]
    for seq in range(parts):
        data = _wav()
        client.post(f"/calls/{cid}/chunks", files={"file": (f"c{seq}.wav", data, "audio/wav")},
                    data={"seq": str(seq), "sha256": hashlib.sha256(data).hexdigest(), "duration_s": "1"})
    return cid

def test_investigator_gets_one_joined_wav(client, mock_db):
    cid = _recorded(client)
    r = client.get(f"/calls/{cid}/audio")
    assert r.status_code == 200 and r.headers["content-type"] == "audio/wav"
    assert r.headers["x-integrity"] == "intact" and r.headers["x-parts"] == "2"
    with wave.open(io.BytesIO(r.content)) as w:
        assert w.getnframes() == 16000                     # 2 x 1 second at 8 kHz
    assert r.headers["x-sha256"] == hashlib.sha256(r.content).hexdigest()
    assert mock_db.custody_events.find_one({"evidence_id": cid, "action": "audio_accessed"})

def test_altered_chunk_is_flagged(client, mock_db):
    cid = _recorded(client)
    mock_db.audio_chunks.update_one({"call_id": cid, "seq": 1}, {"$set": {"sha256": "0" * 64}})
    assert client.get(f"/calls/{cid}/audio").headers["x-integrity"] == "ALTERED"

def test_call_without_audio_is_404(client):
    cid = client.post("/calls/", json={"phone_number": "9444444445"}).json()["id"]
    assert client.get(f"/calls/{cid}/audio").status_code == 404

def test_audio_needs_investigator_login(anon_client):
    assert anon_client.get("/calls/0123456789abcdef01234567/audio").status_code in (401, 403)
