SCAM = "This is CBI. You will be arrested. Transfer Rs 50000 immediately to fraud@upi and do not tell anyone."

def test_stats_counts(client):
    client.post("/sms/", json={"sender": "9444444444", "body": SCAM})
    client.post("/calls/", json={"phone_number": "9999999999"})
    s = client.get("/stats").json()
    assert s["sms"] == 1 and s["calls"] == 1
    assert s["bands"]["CRITICAL"] >= 1
