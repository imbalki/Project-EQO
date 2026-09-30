# TASK-002: CI: build, lint, test

- Status: todo
- Depends on: 001
- Area: android
- Branch: agent/android/<issue>-ci

## Goal
Run Android build, lint and tests on every PR.

## Scope
`.github/workflows/` (ask first: outside android scope), enable the commented android job in `ci.yml`, cache Gradle.

## Acceptance criteria
- [ ] PR runs assembleDebug, ktlintCheck, detekt, test
- [ ] Failing test fails the check
- [ ] Runtime under 10 minutes with cache

## Notes
Read `../AGENT.md` and `../context/` first.
