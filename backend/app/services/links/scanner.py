import re
from urllib.parse import urlparse, parse_qs
from app.services.risk_engine.scorer import risk_band
from app.services.threat_intel.lookup import is_reported

SHORTENERS = {"bit.ly", "tinyurl.com", "t.co", "goo.gl", "cutt.ly", "is.gd", "rb.gy",
              "shorturl.at", "ow.ly", "tiny.cc", "s.id", "t.ly"}
RISKY_TLDS = {"xyz", "top", "club", "online", "site", "live", "icu", "buzz", "shop",
              "click", "link", "cfd", "sbs"}
BRANDS = ["sbi", "hdfc", "icici", "axis", "kotak", "rbi", "cbi", "police", "npci", "paytm",
          "phonepe", "gpay", "uidai", "aadhaar", "incometax", "customs", "cybercrime"]
OFFICIAL = ["sbi.co.in", "onlinesbi.sbi", "hdfcbank.com", "icicibank.com", "axisbank.com",
            "kotak.com", "rbi.org.in", "cbi.gov.in", "npci.org.in", "paytm.com", "phonepe.com",
            "uidai.gov.in", "incometax.gov.in", "cybercrime.gov.in", "gov.in", "nic.in"]
LURE_WORDS = ["kyc", "verify", "update", "login", "refund", "reward", "blocked", "suspend",
              "penalty", "warrant", "fine"]
AUTHORITY_WORDS = ["police", "cbi", "rbi", "court", "fine", "penalty", "refund", "customs", "kyc"]

VERDICT = {
    "LOW": "No strong warning signs found. Still be careful before entering details.",
    "MEDIUM": "Some warning signs. Do not enter passwords, OTPs or card details here.",
    "HIGH": "Likely unsafe. Do not open this link or pay.",
    "CRITICAL": "Very likely a scam. Do not open or pay. Report it.",
}

def _f(code, label, weight):
    return {"code": code, "label": label, "weight": weight}

def _result(text, kind, host, findings, **extra):
    score = min(sum(f["weight"] for f in findings), 100)
    band = risk_band(score)
    return {"input": text, "kind": kind, "host": host, "findings": findings,
            "risk_score": score, "risk_band": band, "verdict": VERDICT[band], **extra}

def _scan_upi(text, db):
    q = parse_qs(urlparse(text).query)
    vpa = (q.get("pa") or [""])[0].lower()
    name = (q.get("pn") or [""])[0]
    amount = (q.get("am") or [""])[0]
    note = (q.get("tn") or [""])[0]
    findings = [_f("payment_qr", "Scanning this QR SENDS money. You never scan a QR to receive money", 15)]
    if amount:
        findings.append(_f("preset_amount", f"Amount already filled in: Rs {amount}", 15))
    if db is not None and vpa and is_reported(db, "upi", vpa):
        findings.append(_f("reported", "This UPI ID was reported in scams", 40))
    if any(w in f"{name} {note}".lower() for w in AUTHORITY_WORDS):
        findings.append(_f("authority_words", "Payee name or note mentions police, court, fine or refund", 25))
    return _result(text, "upi_payment", None, findings,
                   upi={"vpa": vpa, "name": name, "amount": amount, "note": note})

def scan(raw, db=None):
    text = (raw or "").strip()
    if text.lower().startswith("upi://"):
        return _scan_upi(text, db)

    url = text if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*://", text) else "http://" + text
    p = urlparse(url)
    host = (p.hostname or "").lower()
    if not host or "." not in host:
        return {"input": text, "kind": "text", "host": None, "findings": [], "risk_score": 0,
                "risk_band": "LOW", "verdict": "This is plain text, not a link."}

    official = any(host == d or host.endswith("." + d) for d in OFFICIAL)
    tld = host.rsplit(".", 1)[-1]
    findings = []
    if p.scheme == "http":
        findings.append(_f("no_https", "Not encrypted (http instead of https)", 10))
    if re.fullmatch(r"\d{1,3}(\.\d{1,3}){3}", host):
        findings.append(_f("ip_host", "Uses a raw IP address instead of a website name", 25))
    if host in SHORTENERS:
        findings.append(_f("shortener", "Link shortener hides the real destination", 20))
    if "xn--" in host:
        findings.append(_f("punycode", "Uses look-alike characters in the address", 25))
    if tld in RISKY_TLDS:
        findings.append(_f("risky_tld", f"Unusual domain ending (.{tld})", 15))
    if host.count(".") >= 4:
        findings.append(_f("many_subdomains", "Unusually long, nested address", 10))
    if "@" in p.netloc:
        findings.append(_f("at_sign", "Contains '@', which can hide the real website", 20))
    brand = next((b for b in BRANDS if b in host), None)
    if brand and not official:
        findings.append(_f("brand_lookalike", f"Mentions '{brand}' but is not an official {brand} website", 30))
    lure = [w for w in LURE_WORDS if w in url.lower()]
    if lure and not official:
        findings.append(_f("lure_words", "Uses pressure words: " + ", ".join(lure[:3]), 15))
    if len(url) > 120:
        findings.append(_f("long_url", "Very long address", 5))
    if db is not None and (is_reported(db, "url", text) or is_reported(db, "url", url)
                           or is_reported(db, "domain", host)):
        findings.append(_f("reported", "Reported by other users", 40))
    return _result(text, "url", host, findings, official=official)
