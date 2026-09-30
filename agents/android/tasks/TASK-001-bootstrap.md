# TASK-001: Bootstrap Android project

- Status: todo
- Depends on: none
- Area: android
- Branch: agent/android/<issue>-bootstrap

## Goal
Create the Gradle project that builds and launches an empty Compose activity.

## Scope
`android/` only: Gradle Kotlin DSL, version catalog, `app` module, package `app.eqo`, ktlint, detekt, .gitignore additions if needed.

## Acceptance criteria
- [ ] `./gradlew assembleDebug` succeeds
- [ ] App launches to a blank Material 3 screen
- [ ] `./gradlew ktlintCheck detekt test` runs (one sample test)
- [ ] Package layout matches `context/ARCHITECTURE.md`
- [ ] Decision on DI approach recorded in `decisions/`

## Notes
Read `../AGENT.md` and `../context/` first.
