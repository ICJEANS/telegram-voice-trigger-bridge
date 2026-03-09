# Telegram Voice Trigger Bridge - Development Plan

## Goal
모바일에서 음성 입력을 STT로 변환하고,
문장이 `AI야` 또는 `AI`로 시작할 때만 뒤의 명령 본문을 Telegram으로 전송하는 앱/서비스를 만든다.

## Scope (MVP)
- Android 클라이언트 1개
- 음성 인식(STT)
- 트리거 감지: `AI야`, `AI`
- 본문 추출 후 Telegram Bot API 전송
- 전송 성공/실패 UI 표시

## Architecture
1. **Audio Input Layer**: 마이크 입력
2. **STT Layer**: 온디바이스 또는 API 기반 인식
3. **Trigger Parser**:
   - startsWith("AI야") 또는 startsWith("AI")
   - 트리거 제거 후 prompt 추출
4. **Telegram Sender**:
   - `sendMessage` API 호출
5. **Config Storage**:
   - bot token, chat id, trigger words

## Milestones
1. 프로젝트 초기화 및 문서화
2. Telegram 송신 모듈 구현
3. STT 모듈 구현
4. Trigger 파서 구현
5. 모바일 UI 연결 (record → parse → send)
6. 테스트/로그/예외처리 강화
7. 배포 빌드

## Security Notes
- Bot token은 코드 하드코딩 금지
- 로컬 secure storage 사용
- 디버그 로그에 토큰/민감정보 출력 금지

## Next Step
- 기술 스택 확정 (Kotlin native vs Flutter)
- MVP skeleton 코드 생성
