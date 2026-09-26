from .weights import INDICATOR_WEIGHTS

def explain(indicators: list[str]) -> list[dict]:
    return [
        {"indicator": i, "weight": INDICATOR_WEIGHTS.get(i, 0)}
        for i in indicators if i in INDICATOR_WEIGHTS
    ]
