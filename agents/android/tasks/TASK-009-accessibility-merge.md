# TASK-009: One accessibility service with takeover detection

- Status: todo
- Depends on: 004
- Area: android
- Gate: 4 (DEV-11)
- Models: author `mimo-v2.6-pro`, reviewer `glm-5.3-flash`
- Branch: agent/android/<issue>-accessibility

## Goal
Observe, tap, scroll and type on a test app through exactly one EQO accessibility service.

## Scope
`:platform-a11y`. OpenDroid and ClosePaw each register a service; the merged manifest must have one. Guide the Android 13+ restricted-settings repair through Android's own settings screens (user grants, EQO never grants).

## Acceptance criteria
- [ ] Exactly one accessibility service in the merged manifest
- [ ] Observe, tap, scroll, text input proven on a test app on device
- [ ] User touch during an agent action is detected and the loop pauses
- [ ] Accessibility disabled mid-task gives a typed error, no silent retry
- [ ] Another accessibility app enabled does not break EQO

## Evidence required
Device test records; unit tests with fake node trees.

## Notes
Android may block this on some OEM builds; report per device, do not generalize.
