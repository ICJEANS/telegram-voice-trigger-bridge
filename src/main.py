from __future__ import annotations

import argparse
import os
from typing import Iterable

from voice_bridge.telegram_sender import TelegramSender
from voice_bridge.trigger import extract_prompt


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Voice trigger bridge -> Telegram")
    p.add_argument("--text", required=True, help="STT output text")
    p.add_argument("--dry-run", action="store_true", help="Do not call Telegram API")
    return p.parse_args()


def run(text: str, dry_run: bool = False) -> str:
    triggers = [t.strip() for t in os.getenv("TRIGGERS", "AI야,AI").split(",") if t.strip()]
    prompt = extract_prompt(text, triggers)
    if not prompt:
        return "NO_TRIGGER"

    if dry_run:
        return f"SEND:{prompt}"

    token = os.getenv("TELEGRAM_BOT_TOKEN")
    chat_id = os.getenv("TELEGRAM_CHAT_ID")
    if not token or not chat_id:
        raise RuntimeError("Missing TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID")

    sender = TelegramSender(token, chat_id)
    sender.send(prompt)
    return "SENT"


if __name__ == "__main__":
    args = parse_args()
    print(run(args.text, dry_run=args.dry_run))
