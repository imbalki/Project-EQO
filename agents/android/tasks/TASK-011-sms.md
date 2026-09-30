# TASK-011: SMS tool

- Status: todo
- Depends on: 006
- Area: android
- Branch: agent/android/<issue>-sms

## Goal
Let the agent read and send SMS with runtime permissions and explicit confirmation to send.

## Scope
`tools/SmsTool`, manifest permissions, permission request flow.

## Acceptance criteria
- [ ] Read recent messages with permission
- [ ] Send requires user confirmation (see TASK-013)
- [ ] Graceful behavior when permission denied

## Notes
Read `../AGENT.md` and `../context/` first.
