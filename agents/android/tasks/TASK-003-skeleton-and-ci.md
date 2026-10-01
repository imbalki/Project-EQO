# TASK-003: EQO project skeleton and CI

- Status: todo
- Depends on: 001 (002 for the lint baseline)
- Area: android
- Gate: 1 (DEV-02, DEV-03, DEV-14)
- Models: author `glm-5.3-flash`, reviewer `mimo-v2.6-pro`
- Branch: agent/android/<issue>-skeleton-ci

## Goal
A compilable EQO Gradle project with a CI pipeline that fails on new problems.

## Scope
Code lives in the Project-EQO-Android repo (assumption A-1 in `agents/android/README.md`). Gradle Kotlin DSL, version catalog, `:app` with `applicationId` under `ai.eqo`, `minSdk 31` (Android 12), JDK 21, AGP 9.x. CI: assemble debug and release, unit tests, Android Lint with a baseline seeded only from TASK-002 output, ktlint and detekt, upload reports with missing-report = error and at least 30-day retention.

## Acceptance criteria
- [ ] Clean checkout builds twice in a row with `./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug`
- [ ] CI runs the same commands and is green
- [ ] No `abortOnError false` and no blanket lint ignores
- [ ] Wrapper checksum pinned

## Evidence required
CI run link and local command output.

## Notes
Do not edit upstream CI. Use maintained action versions.
