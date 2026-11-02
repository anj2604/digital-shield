from app.services.nlp.spoken import normalize_spoken
from app.services.nlp.ioc_extractor import extract_iocs

def test_spoken_upi_becomes_an_ioc():
    text = normalize_spoken("Pay fifty thousand to fraud at UPI and rbi dot verify at the rate paytm")
    assert set(extract_iocs(text)["upi_ids"]) >= {"fraud@upi", "rbi.verify@paytm"}

def test_normal_speech_is_not_changed():
    assert normalize_spoken("Call me at home tomorrow") == "Call me at home tomorrow"
