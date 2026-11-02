import re

# Common UPI handles; only these count, so normal speech ("call me at home") is not turned into an ID
HANDLES = (r"(upi|ybl|ibl|axl|apl|paytm|okaxis|oksbi|okhdfcbank|okicici|axisbank|hdfcbank|icici|sbi|"
           r"kotak|ptsbi|ptyes|ptaxis|pthdfc|freecharge|jio|airtel|indus|federal|idfcbank|yesbank)")

def normalize_spoken(text: str) -> str:
    """Turn spoken UPI IDs into written form: 'fraud at UPI' -> 'fraud@upi',
    'rbi dot verify at the rate paytm' -> 'rbi.verify@paytm'."""
    t = re.sub(r"(?<=\w)\s+dot\s+(?=\w)", ".", text, flags=re.I)
    t = re.sub(r"\b([\w.\-]{3,})\s+(?:at\s+the\s+rate(?:\s+of)?|at)\s+" + HANDLES + r"\b",
               lambda m: f"{m.group(1)}@{m.group(2).lower()}", t, flags=re.I)
    return t
