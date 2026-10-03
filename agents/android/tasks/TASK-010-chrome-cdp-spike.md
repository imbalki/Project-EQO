# TASK-010: Chrome DevTools (CDP) spike (S3)

- Status: todo
- Depends on: 007, 008; needs a physical device with Chrome
- Area: android
- Gate: 5 (DEV-09)
- Models: author `gpt-6.1-sol` (profile `eqo-trial`), reviewer `glm-5.3-flash`
- Branch: agent/android/<issue>-cdp

## Goal
Navigate Chrome and fill a test form through CDP after the documented debug preparation.

## Scope
`:browser-cdp` from ClosePaw. Informed-consent screen, debug flag preparation, Chrome cold restart, verified debugging socket, then per-function readiness. Chrome must create its own socket; forwarding does not create it.

## Acceptance criteria
- [ ] Consent shown before any preparation
- [ ] After restart, the endpoint is verified before any action
- [ ] Navigate and fill a local test form end to end
- [ ] Cleanup leaves no open devtools port
- [ ] Failures surface as typed setup errors

## Evidence required
Device logs plus a short screen recording.

## Notes
Capability is shown to the user only after its own readiness check passes.
