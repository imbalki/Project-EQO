# TASK-012: Action loop with Pause, Stop and takeover (S5)

- Status: todo
- Depends on: 006, 009
- Area: android
- Gate: 7 (DEV-12)
- Models: author `mimo-v2.6-pro`, reviewer `glm-5.3-flash`, race-test review `mimo-v2.6-pro`
- Branch: agent/android/<issue>-action-loop

## Goal
One Kotlin loop: permission check, approval, execute, observe, verify, repeat. The user can pause, stop or take over at any moment and always knows what did and did not happen.

## Scope
`:core-agent`. States: running, paused (takeover), stopped, cancelled. Approval before sensitive or irreversible actions; no automatic retry of irreversible actions; no automatic resume after recovery.

## Acceptance criteria
- [ ] Each of pause, stop, takeover transitions within a bounded time (virtual-time test)
- [ ] No action mid-flight at settle; plan reaches terminal status exactly once
- [ ] A verifier confirms postconditions or records a typed partial-apply result
- [ ] Resume only after explicit user confirmation
- [ ] Device scenario: run, user taps, loop pauses, resume, stop

## Evidence required
Race-test output and the device scenario record.

## Notes
Both upstream projects have comments marking past races here. Read them.
