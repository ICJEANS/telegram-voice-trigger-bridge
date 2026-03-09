from voice_bridge.trigger import extract_prompt


def test_extract_prompt_korean_trigger():
    assert extract_prompt("AI야 오늘 일정 알려줘", ["AI야", "AI"]) == "오늘 일정 알려줘"


def test_extract_prompt_english_trigger():
    assert extract_prompt("AI show me weather", ["AI야", "AI"]) == "show me weather"


def test_extract_prompt_no_trigger():
    assert extract_prompt("오늘 뭐해", ["AI야", "AI"]) is None


def test_extract_prompt_only_trigger():
    assert extract_prompt("AI야", ["AI야", "AI"]) is None
