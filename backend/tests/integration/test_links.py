def codes(r):
    return {f["code"] for f in r["findings"]}

def test_shortener_with_lure_words(client):
    r = client.post("/links/scan", json={"input": "http://bit.ly/sbi-kyc-update"}).json()
    assert {"shortener", "no_https", "lure_words"} <= codes(r)
    assert r["risk_band"] in ("MEDIUM", "HIGH", "CRITICAL")

def test_lookalike_bank_site(client):
    r = client.post("/links/scan", json={"input": "https://sbi-kyc-verify.xyz/login"}).json()
    assert {"brand_lookalike", "risky_tld"} <= codes(r)
    assert r["risk_band"] in ("HIGH", "CRITICAL")

def test_official_bank_site_is_low(client):
    r = client.post("/links/scan", json={"input": "https://www.onlinesbi.sbi/"}).json()
    assert r["risk_band"] == "LOW" and r["official"] is True

def test_upi_qr_with_reported_id(client):
    client.post("/threat-intel/report", json={"type": "upi", "value": "fraud@upi"})
    r = client.post("/links/scan", json={
        "input": "upi://pay?pa=fraud@upi&pn=CBI%20Cyber%20Cell&am=50000&tn=Clear%20your%20case",
        "source": "qr"}).json()
    assert r["kind"] == "upi_payment" and r["risk_band"] == "CRITICAL"
    assert {"payment_qr", "preset_amount", "reported", "authority_words"} <= codes(r)

def test_ip_address_link(client):
    r = client.post("/links/scan", json={"input": "http://192.168.10.5/pay"}).json()
    assert "ip_host" in codes(r)
