# telegram-voice-trigger-bridge

모바일 음성(STT 결과 텍스트)에서 `AI야`/`AI` 트리거를 감지해,
명령 본문만 Telegram으로 전송하는 브리지 프로젝트.

## Features (MVP)
- Trigger parser (`AI야`, `AI`)
- Prompt extraction
- Telegram `sendMessage` 연동
- Dry-run 모드
- pytest 테스트

## Quick Start

```bash
cd projects/telegram-voice-trigger-bridge
uv run --with pytest pytest -q
```

### Dry-run test
```bash
TRIGGERS="AI야,AI" uv run python -m src.main --text "AI야 오늘 일정 알려줘" --dry-run
# SEND:오늘 일정 알려줘
```

### Real send
```bash
export TELEGRAM_BOT_TOKEN="..."
export TELEGRAM_CHAT_ID="..."
export TRIGGERS="AI야,AI"
uv run python -m src.main --text "AI야 오늘 일정 알려줘"
# SENT
```

## Android MVP
- 경로: `android-app/`
- 구현: Kotlin `MainActivity`에서
  1) 음성 입력 시작
  2) STT 결과 수신
  3) `AI야`/`AI` 트리거 파싱
  4) Telegram `sendMessage` 전송

> 이 환경에서는 Android SDK/Gradle 실행기가 없어 APK 빌드는 아직 미실행.
> Android Studio에서 `android-app` 폴더를 열어 빌드하면 된다.

## Files
- `src/voice_bridge/trigger.py`: 트리거 파싱
- `src/voice_bridge/telegram_sender.py`: Telegram API 전송
- `src/main.py`: CLI 엔트리
- `tests/`: 단위 테스트
- `android-app/`: Android Kotlin MVP
- `DEV_PLAN.md`: 개발 단계 계획
