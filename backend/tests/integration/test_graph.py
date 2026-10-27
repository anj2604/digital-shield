SCAM = "This is CBI. You will be arrested. Transfer Rs 50000 immediately to fraud@upi and do not tell anyone."

def test_same_number_links_call_and_sms(client):
    client.post("/calls/", json={"phone_number": "9333333333"})
    client.post("/sms/", json={"sender": "9333333333", "body": SCAM})
    g = client.get("/graph/neighbours", params={"type": "phone", "value": "9333333333"}).json()
    assert sorted(n["type"] for n in g["nodes"] if n["kind"] == "evidence") == ["call", "sms"]

def test_shared_upi_links_two_victims(client):
    client.post("/sms/", json={"sender": "9444444444", "body": SCAM})
    client.post("/sms/", json={"sender": "9555555555", "body": SCAM})
    g = client.get("/graph/neighbours", params={"type": "upi", "value": "fraud@upi"}).json()
    assert g["summary"]["evidence_count"] == 2
    phones = {n["value"] for n in g["nodes"] if n["kind"] == "ioc" and n["type"] == "phone"}
    assert {"9444444444", "9555555555"} <= phones

def test_unknown_value_has_empty_graph(client):
    g = client.get("/graph/neighbours", params={"type": "phone", "value": "9000000000"}).json()
    assert g["summary"]["evidence_count"] == 0
