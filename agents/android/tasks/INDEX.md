# Android task index (Phase 1)

Status values: `todo`, `in-progress`, `blocked`, `in-review`, `done`.
Pick the first `todo` task whose dependencies are all `done`. Set it to `in-progress` in the same commit that starts work.

| ID | Title | Depends on | Status |
|----|-------|-----------|--------|
| TASK-001 | Bootstrap Android project | - | todo |
| TASK-002 | CI: build, lint, test | 001 | todo |
| TASK-003 | Compose app shell and chat UI | 001 | todo |
| TASK-004 | Settings and secure key storage | 001 | todo |
| TASK-005 | LlmClient interface and OpenRouter client | 001 | todo |
| TASK-006 | Agent loop with tool calling | 005 | todo |
| TASK-007 | On-device Gemma via LiteRT | 005 | todo |
| TASK-008 | Room memory store | 001 | todo |
| TASK-009 | LLM fact extraction | 006, 008 | todo |
| TASK-010 | Composio client and meta tools | 004, 006 | todo |
| TASK-011 | SMS tool | 006 | todo |
| TASK-012 | Voice loop (mic button, STT, TTS) | 003, 006 | todo |
| TASK-013 | Confirmation UI for outward actions | 003, 006 | todo |
| TASK-014 | Model download and freemium key flow | 004, 007 | todo |
| TASK-015 | Release build and GitHub Releases APK | 002 | todo |
