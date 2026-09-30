# TASK-014: Model download and freemium key flow

- Status: todo
- Depends on: 004, 007
- Area: android
- Branch: agent/android/<issue>-model-download

## Goal
First-run choice: download Gemma, enter own OpenRouter key, or use the subsidized key.

## Scope
`settings/`, onboarding screens, download manager with resume.

## Acceptance criteria
- [ ] Resumable model download with progress and checksum
- [ ] Subsidized key read from build config, never committed
- [ ] User can switch modes later in settings

## Notes
Read `../AGENT.md` and `../context/` first.
