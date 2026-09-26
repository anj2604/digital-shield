from .weights import INDICATOR_WEIGHTS

def calculate_risk(indicators: list[str]) -> int:
    score = sum(INDICATOR_WEIGHTS.get(i, 0) for i in indicators)
    return min(score, 100)

def risk_band(score: int) -> str:
    if score >= 80:
        return "CRITICAL"
    if score >= 50:
        return "HIGH"
    if score >= 25:
        return "MEDIUM"
    return "LOW"
