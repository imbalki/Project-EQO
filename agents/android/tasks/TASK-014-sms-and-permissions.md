# TASK-014: SMS compose-only and permission narrowing

- Status: todo
- Depends on: 004
- Area: android
- Gate: 8
- Models: author `glm-5.3-flash`, reviewer `mimo-v2.6-pro`
- Branch: agent/android/<issue>-sms-permissions

## Goal
EQO asks only for permissions it needs, and the Termux bridge cannot run (D-005).

## Scope
SMS: compose first; send only after explicit recipient and content confirmation. Remove READ_SMS and RECEIVE_SMS from the manifest; no inbox reading. Keep the bundled ClosePaw Python bridge asset as a recorded deviation, but disable the `termux_shell` tool and do not request the Termux RUN_COMMAND permission.

## Acceptance criteria
- [ ] Merged manifest has no READ_SMS or RECEIVE_SMS
- [ ] No code path launches the Python bridge or calls Termux (test plus grep)
- [ ] `termux_shell` is not exposed to the model
- [ ] Added line in the security review noting the unreviewed bundled script

## Evidence required
Merged-manifest dump and test output.

## Notes
Removal of the bridge asset is deferred, not rejected.
