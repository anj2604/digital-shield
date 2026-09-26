from app.services.risk_engine.scorer import calculate_risk, risk_band
from app.services.risk_engine.explainer import explain

def test_critical_case():
    indicators = ["authority_impersonation", "arrest_threat", "financial_coercion"]
    score = calculate_risk(indicators)
    assert score == 60
    assert risk_band(score) == "HIGH"

def test_low_risk_case():
    indicators = ["unknown_sender"]
    score = calculate_risk(indicators)
    assert score == 10
    assert risk_band(score) == "LOW"

def test_max_score_caps_at_100():
    indicators = list({
        "authority_impersonation", "arrest_threat", "urgency",
        "financial_coercion", "credential_request", "isolation",
        "call_control_coercion", "unknown_sender", "suspicious_url",
        "reported_ioc"
    })
    score = calculate_risk(indicators)
    assert score == 100
    assert risk_band(score) == "CRITICAL"

def test_explain_returns_correct_weights():
    breakdown = explain(["arrest_threat", "urgency"])
    assert {"indicator": "arrest_threat", "weight": 20} in breakdown
    assert {"indicator": "urgency", "weight": 15} in breakdown
