SCAM = "This is CBI. You will be arrested. Transfer Rs 50000 immediately to fraud@upi and do not tell anyone."

def test_stats_counts(client):
    client.post("/sms/", json={"sender": "9444444444", "body": SCAM})
    client.post("/calls/", json={"phone_number": "9999999999"})
    s = client.get("/stats").json()
    assert s["sms"] == 1 and s["calls"] == 1 and s["bands"]["CRITICAL"] >= 1

def test_activity_feed_newest_first(client):
    client.post("/sms/", json={"sender": "9444444444", "body": SCAM})
    client.post("/calls/", json={"phone_number": "9999999999"})
    feed = client.get("/activity").json()
    assert len(feed) == 2 and feed[0]["kind"] == "call"

def test_trends_cover_the_week(client):
    client.post("/sms/", json={"sender": "9444444444", "body": SCAM})
    t = client.get("/stats/trends", params={"days": 7}).json()
    assert len(t["days"]) == 7 and t["days"][-1]["CRITICAL"] == 1
    assert len(t["top_indicators"]) > 0
