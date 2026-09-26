import re

PATTERNS = {
    "authority_impersonation": [
        r"\bcbi\b", r"\bed\b", r"\brbi\b", r"\bpolice\b",
        r"income tax department", r"cyber cell", r"narcotics",
        r"calling from (the )?(cbi|police|ed|rbi|court)",
    ],
    "arrest_threat": [
        r"\barrest\b", r"warrant", r"you will be arrested",
        r"legal action will be taken", r"non.?bailable",
    ],
    "urgency": [
        r"\bimmediately\b", r"within \d+ (minutes|hours)",
        r"right now", r"before it'?s too late", r"last warning",
        r"account will be (blocked|suspended|frozen) today",
    ],
    "financial_coercion": [
        r"transfer (rs\.?|₹|inr)?\s?\d+", r"pay (a )?fine",
        r"processing fee", r"refundable (deposit|amount)",
        r"clear (your|the) case",
    ],
    "credential_request": [
        r"\botp\b", r"one.?time password", r"share your pin",
        r"cvv", r"debit card number", r"net ?banking password",
    ],
    "isolation": [
        r"do not tell anyone", r"don'?t tell (your )?family",
        r"keep this confidential", r"this is a secret investigation",
    ],
    "call_control_coercion": [
        r"do not disconnect", r"don'?t hang up",
        r"stay on the (line|call)", r"do not cut the call",
    ],
}

COMPILED = {
    label: [re.compile(p, re.IGNORECASE) for p in patterns]
    for label, patterns in PATTERNS.items()
}

def classify_text(text: str) -> list[str]:
    found = []
    for label, patterns in COMPILED.items():
        if any(p.search(text) for p in patterns):
            found.append(label)
    return found
