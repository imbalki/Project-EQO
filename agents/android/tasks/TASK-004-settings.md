# TASK-004: Settings and secure key storage

- Status: todo
- Depends on: 001
- Area: android
- Branch: agent/android/<issue>-settings

## Goal
Settings screen for cloud opt-in, OpenRouter key, Composio key and model choice, stored securely.

## Scope
`settings/` package and a settings screen.

## Acceptance criteria
- [ ] Keys stored with Android Keystore-backed encryption
- [ ] Cloud toggle defaults to OFF
- [ ] Keys never appear in logs
- [ ] Unit tests for the settings repository

## Notes
Read `../AGENT.md` and `../context/` first.
