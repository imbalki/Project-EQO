# TASK-010: Composio client and meta tools

- Status: todo
- Depends on: 004, 006
- Area: android
- Branch: agent/android/<issue>-composio

## Goal
Connect the agent to Composio's hosted API so it can discover, authenticate and run app tools at runtime.

## Scope
`tools/composio/` package: search_tools, authenticate_app, execute_tool.

## Acceptance criteria
- [ ] Meta tools exposed to the agent loop
- [ ] OAuth handoff opens the system browser and returns to the app
- [ ] Errors and rate limits handled
- [ ] Tests against MockWebServer

## Notes
Read `../AGENT.md` and `../context/` first.
