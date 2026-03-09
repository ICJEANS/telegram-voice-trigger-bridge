from __future__ import annotations


def extract_prompt(text: str, triggers: list[str]) -> str | None:
    s = (text or "").strip()
    if not s:
        return None

    normalized = s.replace(" ", "")

    for t in triggers:
        t = t.strip()
        if not t:
            continue
        # Trigger can be followed by space/punctuation or directly by content.
        if s.startswith(t):
            remainder = s[len(t):].strip(" :,-")
            return remainder or None
        # Space-agnostic check for Korean spacing quirks
        if normalized.startswith(t.replace(" ", "")):
            raw = normalized[len(t.replace(" ", "")):].strip(" :,-")
            return raw or None

    return None
