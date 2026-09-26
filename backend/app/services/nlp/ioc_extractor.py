import re

PHONE_RE = re.compile(r"(?:\+91[\-\s]?)?[6-9]\d{9}\b")
EMAIL_RE = re.compile(r"[\w.\-]+@[\w\-]+\.[a-zA-Z]{2,}")
URL_RE = re.compile(r"(?:https?://|www\.)[^\s]+")
UPI_RE = re.compile(r"[\w.\-]{2,}@[a-zA-Z]{2,}(?<!\.com)(?<!\.in)\b")

def extract_iocs(text: str) -> dict:
    phones = PHONE_RE.findall(text)
    emails = EMAIL_RE.findall(text)
    urls = URL_RE.findall(text)

    # UPI IDs look like "name@bank" — exclude anything already matched as an email
    upi_candidates = UPI_RE.findall(text)
    upi_ids = [u for u in upi_candidates if u not in emails]

    return {
        "phones": list(set(phones)),
        "emails": list(set(emails)),
        "urls": list(set(urls)),
        "upi_ids": list(set(upi_ids)),
    }
