# Temporary hardcoded list until Phase 8 builds the real threat-intel database.
REPORTED_NUMBERS = {
    "9999999999",
    "9888888888",
}

def is_reported(phone_number: str) -> bool:
    digits = "".join(ch for ch in phone_number if ch.isdigit())
    return digits[-10:] in REPORTED_NUMBERS
