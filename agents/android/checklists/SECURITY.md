# Security checklist
- [ ] Keys in Keystore-backed storage only
- [ ] No message content or tokens in logs
- [ ] Network calls only to endpoints the user enabled
- [ ] Outward actions require confirmation
- [ ] Manifest permissions are the minimum needed
- [ ] Release APK signed with CI-held keystore

## TASK-014 recorded deviation (D-005, issue #19) — 2026-10-02

NOTE (unreviewed bundled script, D-005): the Phase One donor ClosePaw app
still ships an unreviewed Python bridge script (`closepaw_bridge_py`) in its
own APK; that asset is excluded from EQO's `android/` build (TASK-004), the
`termux_shell` tool is not exposed to the model, and Termux's RUN_COMMAND
permission is not requested (guarded by `SmsPermissionsManifestTest`). The
script itself has never gone through a security review — recorded deviation,
removal deferred to a later phase.
