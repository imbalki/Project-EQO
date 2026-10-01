# TASK-005: EQO identity, branding gate, NOTICE and provenance

- Status: todo
- Depends on: 004
- Area: android
- Gate: 8 (DEV-04, DEV-16)
- Models: author `glm-5.3-flash`, reviewer `mimo-v2.6-pro`
- Branch: agent/android/<issue>-rebrand

## Goal
Everything the user sees says EQO. Upstream names live only in a Legal / Open-source notices screen and the NOTICE file.

## Scope
Packages and application id to `ai.eqo.*`; deep-link scheme to `eqo://`. Add `scripts/check-branding.sh` as a CI step. Create `NOTICE` (Apache-2.0 for OpenDroid, ClosePaw, Shizuku; MIT for Shizuku-API; keep ClosePaw's NOTICE content) and a per-file provenance map. The Shizuku application id and manager permission strings are handled in TASK-007, not here.

## Acceptance criteria
- [ ] Build and tests green after the rename
- [ ] Branding scan finds no upstream product names in user-visible strings, launcher label or icons
- [ ] Deep link `eqo://` resolves; content provider authority valid
- [ ] Provenance map covers 100% of extracted files

## Evidence required
Scan output, screenshots of the app label and notices screen.

## Notes
Trademark interpretation of Shizuku's naming terms needs the owner's sign-off before any public distribution. A private study build is not blocked by it.
