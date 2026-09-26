from app.services.nlp.ioc_extractor import extract_iocs

def test_extracts_phone_and_upi():
    text = "Contact +91 9876543210 or pay to fraud@upi immediately. Email us at fake@example.com or visit http://fake-kyc.example"
    result = extract_iocs(text)
    assert "9876543210" in result["phones"] or "+91 9876543210" in "".join(result["phones"])
    assert "fake@example.com" in result["emails"]
    assert "http://fake-kyc.example" in result["urls"]
    assert "fraud@upi" in result["upi_ids"]

def test_no_iocs_in_plain_text():
    text = "See you at 7pm for dinner."
    result = extract_iocs(text)
    assert result["phones"] == []
    assert result["emails"] == []
    assert result["urls"] == []
    assert result["upi_ids"] == []
