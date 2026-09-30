# TASK-007: On-device Gemma via LiteRT

- Status: todo
- Depends on: 005
- Area: android
- Branch: agent/android/<issue>-local-gemma

## Goal
Implement `LocalGemmaClient` so the default path works with no network.

## Scope
`llm/` package, model file loading from app storage, LiteRT dependency.

## Acceptance criteria
- [ ] Gemma E4B loads and returns a completion on a real device
- [ ] Implements `LlmClient`, including tool-call output format
- [ ] Clear error when the model file is missing
- [ ] Performance notes (tokens/sec, RAM) added to `context/`

## Notes
Read `../AGENT.md` and `../context/` first.
