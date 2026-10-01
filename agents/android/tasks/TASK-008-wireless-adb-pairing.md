# TASK-008: Wireless ADB pairing on the phone (S2)

- Status: todo
- Depends on: 007; needs devices on Android 11 (physical Realme Narzo 20, Realme UI 2.0), 12 and 13
- Area: android
- Gate: 3 (DEV-08)
- Models: author `mimo-v2.6-pro`, reviewer `glm-5.3-flash`
- Branch: agent/android/<issue>-adb-pairing

## Goal
First-run guided pairing, entirely on the phone (D accepted: no PC-hosted instance in Phase One), gating every privileged feature.

## Scope
Extract ClosePaw's wireless-ADB pairing code into `:adb-pairing`. Guide the user through Developer Options, Wi-Fi, Wireless debugging and the pairing code. Pairing port and connection port are different; handle both. Each check (pair, connect, helper start, authorize, binder health) is a separate, individually failing step.

## Acceptance criteria
- [ ] Fresh install shows "activation required"; privileged entry points refuse with guidance until done
- [ ] Pairing succeeds on Android 11 (physical Realme Narzo 20, Realme UI 2.0 = the OEM-skin device) plus Android 12 and 13 (emulator and/or physical; label which)
- [ ] Wrong code, port confusion, revoke, reboot and Wi-Fi change are each tested and recover with guidance, not silently
- [ ] Android-owned settings names shown verbatim

## Evidence required
Device test records per scenario.

## Notes
Pairing, helper authorization, accessibility and CDP consent are separate auth planes. One passing proves nothing about the others.
