# TASK-015: Release build and GitHub Releases APK

- Status: todo
- Depends on: 002
- Area: android
- Branch: agent/android/<issue>-release

## Goal
Produce a signed release APK and attach it to a GitHub Release on tag push.

## Scope
`android/` signing config via CI secrets, `.github/workflows/release.yml` (ask first).

## Acceptance criteria
- [ ] Tag `vX.Y.Z` builds a signed APK
- [ ] Keystore only in CI secrets
- [ ] APK attached to the GitHub Release

## Notes
Read `../AGENT.md` and `../context/` first.
