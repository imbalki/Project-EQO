# TASK-001: Pin and verify the Android build toolchain

- Status: todo
- Depends on: -
- Area: android (environment)
- Gate: prerequisite for gate 1
- Models: author `glm-5.3-flash`, reviewer `mimo-v2.6-flash`
- Branch: agent/android/<issue>-toolchain

## Goal
One documented, reproducible toolchain on the owner's Windows PC and in CI, so every later task builds the same way.

## Scope
JDK 21 (Temurin), Android SDK (platform 36, build-tools, platform-tools), NDK 29, CMake 3.31+, Gradle via the project wrapper. Versions come from the verified upstream build files, not guesses. Record exact versions in `docs/android/TOOLCHAIN.md` (Project-EQO-Android).

## Acceptance criteria
- [ ] `java -version`, `sdkmanager --list_installed`, `adb version` outputs captured in `Phase-One/evidence/toolchain.txt`
- [ ] SDK licenses accepted by the owner (recorded, not assumed)
- [ ] A throwaway Gradle build of the pinned OpenDroid commit reaches the compile step on this machine
- [ ] `docs/android/TOOLCHAIN.md` lists every pinned version and where it came from

## Evidence required
Command output, not summaries. If a download is blocked, record the exact error.

## Notes
Phase One report found no JDK, no SDK and no attached device on the earlier machine. Do not claim any build result until a command exits 0.
