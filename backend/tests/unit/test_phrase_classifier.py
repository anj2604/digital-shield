from app.services.nlp.phrase_classifier import classify_text

def test_detects_multiple_indicators():
    text = "This is CBI. You will be arrested. Transfer Rs 50000 immediately and do not tell anyone."
    result = classify_text(text)
    assert "authority_impersonation" in result
    assert "arrest_threat" in result
    assert "financial_coercion" in result
    assert "urgency" in result
    assert "isolation" in result

def test_harmless_text_returns_nothing():
    text = "Hey, are we still on for dinner tonight?"
    result = classify_text(text)
    assert result == []
