def test_call_start_active_end(client):
    created = client.post("/calls/", json={"phone_number": "9999999999"}).json()
    active = client.get("/calls/active").json()
    assert active["active"] is True and active["call"]["id"] == created["id"]
    assert client.post(f"/calls/{created['id']}/end").json()["ended"] is True
    assert client.get("/calls/active").json()["active"] is False

def test_ending_unknown_call_returns_404(client):
    assert client.post("/calls/000000000000000000000000/end").status_code == 404
