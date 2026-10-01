# ADR-0003: Android 11 floor (minSdk 30) and no LiquidAI Leap SDK

- Status: accepted
- Date: 2026-10-01
- Deciders: owner

## Context

- Nothing built so far depends on Android 12: no EQO Android code exists yet (no combined APK, no local build - `android/Phase-One/FEASIBILITY-REPORT.md` sections 6 and 7), so moving the floor costs nothing today.
- OpenDroid, the upstream base, ships `minSdk 26`, so an Android 11 floor is compatible with the base and only requires raising the base's minSdk to 30.
- ClosePaw, the donor, ships `minSdk 31` only because of the proprietary LiquidAI Leap SDK (`closepaw/app/build.gradle.kts:13-20`, "Required by LiquidAI Leap SDK"). EQO does not use the Leap SDK, so the donor's minSdk 31 is a donor fact, not an EQO requirement.
- Wireless-debugging pairing - the basis of EQO's mandatory authenticated wireless ADB - exists from Android 11. Android 10 cannot pair over wireless debugging, so an Android 10 floor is not acceptable.
- Assumption A-1 (Android code lives in a separate `Project-EQO-Android` repo) is superseded by ADR-0002, on PR #22: Android code lives in `android/` in this repo.

## Decision

- EQO's Android floor is **Android 11 = API 30 = minSdk 30** (previously Android 12 / minSdk 31).
- The **LiquidAI Leap SDK is excluded from EQO entirely**: no EQO code, dependency or documentation may use `ai.liquid.*` / `leap-sdk`, except the exclusion rule itself (`agents/android/context/CONSTRAINTS.md`).
- Test matrices carry an API 30 lane alongside 31, 33, 34, 35, 36. The device gate is Android 11 (physical) plus Android 12 and 13 (emulator and/or physical), each result labelled emulator or physical.

## Consequences

Recorded, not decided in this ADR:

- LiteRT-LM / Gemma minSdk must be verified in TASK-013; if it is above 30, local inference is disabled on lower API levels.
- ClosePaw donor code may use APIs above API 30; compile and lint in TASK-003, TASK-004 and TASK-007 surface them.
- Real-device gates: the owner's physical phone is a Realme Narzo 20 (Realme UI 2.0, Android 11) and counts as the OEM-skin device. Android 12/13 evidence comes from emulator and/or physical devices; every result must be labelled emulator or physical, and the two must never be presented as equal.
- Donor and upstream facts stay facts: ClosePaw's own `minSdk 31` and OpenDroid's `minSdk 26` are not EQO requirements.

Also recorded as D-007 in `android/Phase-One/DECISIONS.md`.
