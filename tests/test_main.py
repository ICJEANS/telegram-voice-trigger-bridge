from main import run


def test_run_dry_trigger(monkeypatch):
    monkeypatch.setenv("TRIGGERS", "AI야,AI")
    out = run("AI야 작업 시작", dry_run=True)
    assert out.startswith("SEND:")


def test_run_no_trigger(monkeypatch):
    monkeypatch.setenv("TRIGGERS", "AI야,AI")
    assert run("일정 알려줘", dry_run=True) == "NO_TRIGGER"
