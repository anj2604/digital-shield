def codes(r):
    return {f["code"] for f in r["findings"]}

def test_new_domain_adds_warning(client):
    r = client.post("/links/scan", json={"input": "https://fresh-refund-portal.com/claim"}).json()
    assert "new_domain" in codes(r) and r["osint"]["age_days"] == 4

def test_old_domain_has_no_age_warning(client):
    r = client.post("/links/scan", json={"input": "https://example.com"}).json()
    assert "new_domain" not in codes(r)

def test_osint_endpoint_for_investigators(client):
    assert client.get("/osint/domain", params={"name": "fresh-site.com"}).json()["age_days"] == 4
