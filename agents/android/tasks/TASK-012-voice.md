# TASK-012: Voice loop (mic button, STT, TTS)

- Status: todo
- Depends on: 003, 006
- Area: android
- Branch: agent/android/<issue>-voice

## Goal
Tap mic, speak, get a spoken and written reply.

## Scope
`voice/` package, RECORD_AUDIO permission flow.

## Acceptance criteria
- [ ] Speech recognized and sent to the agent loop
- [ ] Reply spoken via TTS, stoppable
- [ ] Works offline where the device supports on-device STT
- [ ] No wake word

## Notes
Read `../AGENT.md` and `../context/` first.
