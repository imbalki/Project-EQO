# TASK-002: Reproduce the upstream OpenDroid build and capture real lint findings

- Status: todo
- Depends on: 001
- Area: android (study)
- Gate: 1 (S0 build parity, DEV-01)
- Models: author `glm-5.3-flash`, reviewer `mimo-v2.6-pro`
- Branch: agent/android/<issue>-upstream-build

## Goal
Know the true state of the base app before building on it. Upstream Android Lint failed in CI and its logs were unreadable (HTTP 401), so the real findings are unknown.

## Scope
Read-only study checkout of `yashab-cyber/opendroid` at `6ff5a061`. Never edit upstream. Output lives under `Phase-One/evidence/` only.

## Acceptance criteria
- [ ] `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` run on a clean checkout; exit codes recorded
- [ ] Authentic lint report archived (xml + text) with provenance; every finding listed with id, file, line, severity, taken only from that report
- [ ] If anything stays unknown, the report says exactly what
- [ ] No baseline regenerated blindly

## Evidence required
Raw command output and report files.

## Notes
Feeds the lint baseline in TASK-003.
