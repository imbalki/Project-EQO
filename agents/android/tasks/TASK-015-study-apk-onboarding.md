# TASK-015: Study APK and guided onboarding (S6)

- Status: todo
- Depends on: 007 to 014
- Area: android
- Gate: 1 to 8 (integration)
- Models: author `mimo-v2.6-pro` (integration), `glm-5.3-flash` (screens), copy `deepseek-v4.1-flash`; reviewer `glm-5.3-flash`
- Branch: agent/android/<issue>-study-apk

## Goal
One sideload APK a first-time user can set up by following the app, with clear status for each capability.

## Scope
Onboarding: key, accessibility, wireless ADB, helper, Chrome consent. Task screen with progress, approvals, Pause, Stop, takeover. Recovery guidance per failure class. Legal / Open-source notices screen. Requirement IDs and flows are in `Phase-One/docs/PRD.md` and `USER-FLOWS.md`; trace to them.

## Acceptance criteria
- [ ] Release APK builds reproducibly and installs on Android 11 (physical Realme Narzo 20, Realme UI 2.0 = the OEM-skin device) plus Android 12 and 13 (emulator and/or physical; label which)
- [ ] A person who has not seen the project completes setup using only the app
- [ ] Every capability shows its own readiness state; none inferred
- [ ] Branding scan clean; no secrets in logs

## Evidence required
Install log, short recording of a first-run walkthrough.

## Notes
Sideload only. Play Protect and Android restricted-settings behavior are real constraints, not something to bypass.
