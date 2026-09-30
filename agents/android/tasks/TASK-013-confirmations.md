# TASK-013: Confirmation UI for outward actions

- Status: todo
- Depends on: 003, 006
- Area: android
- Branch: agent/android/<issue>-confirmations

## Goal
Show a clear confirm/deny card before any action that leaves the phone (send SMS, email, calendar write).

## Scope
`ui/` confirmation component, hook into `ToolRegistry` via a `requiresConfirmation` flag.

## Acceptance criteria
- [ ] Tool marked as outward pauses the loop until the user decides
- [ ] Deny returns a result the model can react to
- [ ] Unit test for pause/resume behavior

## Notes
Read `../AGENT.md` and `../context/` first.
