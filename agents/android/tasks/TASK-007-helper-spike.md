# TASK-007: EQO-branded privileged helper spike (S1)

- Status: todo
- Depends on: 003, 005; needs a physical device
- Area: android
- Gate: 2 (DEV-06, DEV-07)
- Models: author `mimo-v2.6-pro`, reviewer `glm-5.3-flash`, security pass `mimo-v2.6-pro`
- Branch: agent/android/<issue>-helper-spike

## Goal
Prove the helper starts, authorizes EQO and survives binder death, bundled in the single EQO APK with no separate helper app. Largest technical risk: run first among device spikes.

## Scope
`:helper-server` and `:helper-client` forked from Shizuku and Shizuku-API with provenance headers. Per D-004 (as corrected): rename the application id and the `moe.shizuku.manager.permission.*` strings in one lockstep change on server and client, because a one-sided rename silently breaks binder delivery. Class and package names may stay. Replace the "manager app must be installed" hard gate with an EQO app-id allowlist. Package the starter as a loadable library inside the APK.

## Acceptance criteria
- [ ] Starter builds with the pinned NDK
- [ ] Server starts via activation on Android 12+ with no Shizuku manager installed
- [ ] EQO receives the binder; a privileged test call returns the shell uid
- [ ] Negative test: mismatched permission string fails loudly
- [ ] Binder death leads to a clean state and a re-activation prompt
- [ ] Server survives an EQO app restart and exits cleanly when activation is revoked

## Evidence required
Device logs and the commands that produced them.

## Notes
Server behavior without the manager is untested upstream. If this spike fails, stop and report; do not work around it silently.
