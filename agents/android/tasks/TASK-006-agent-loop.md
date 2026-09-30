# TASK-006: Agent loop with tool calling

- Status: todo
- Depends on: 005
- Area: android
- Branch: agent/android/<issue>-agent-loop

## Goal
Write the agent loop, about 300 lines of Kotlin, with JSON tool-call parsing and no framework.

## Scope
`agent/` and `tools/` interfaces, `ToolRegistry`.

## Acceptance criteria
- [ ] Loop runs prompt -> model -> tool calls -> results -> final answer
- [ ] Max-step limit and cancellation supported
- [ ] Malformed tool JSON handled without crashing
- [ ] Unit tests with a fake `LlmClient` and fake tools

## Notes
Read `../AGENT.md` and `../context/` first.
