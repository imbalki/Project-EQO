# TASK-003 evidence: EQO project skeleton and CI (issue #8)

Branch: `agent/android/8-skeleton-ci` (worktree `C:\Users\<user>\Claude\worktrees\task-003`, created from origin/main c31121c by the lead).
Commits: `4db46be` (skeleton), `6225b0c` (ci android job), `eceef2c` (ci hashFiles fix), `d265f62` (gradlew +x). All messages are Conventional Commits ending `Refs #8`.

## Versions chosen and where each came from

| Component | Version | Source of the choice |
|---|---|---|
| Gradle wrapper | 9.7.0, distributionSha256Sum `84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae` | pinned per instructions; the live SHA at https://services.gradle.org/distributions/gradle-9.7.0-bin.zip.sha256 fetched with curl returned exactly this value (exit 0). Wrapper generated with the cached Gradle: `gradle.bat wrapper --gradle-version 9.7.0 --distribution-type bin --gradle-distribution-sha256-sum 84fbba45...` → `BUILD SUCCESSFUL in 23s`, exit code **0**; `gradle/wrapper/gradle-wrapper.properties` line 3 pins the sum |
| AGP | 9.3.1 | mandated pin (TASK-001 TOOLCHAIN.md, verified upstream) |
| Kotlin | 2.4.0 via `kotlin-gradle-plugin:2.4.0` on the root buildscript classpath | mandated pin; AGP 9.3.1 rejected `org.jetbrains.kotlin.android` ("no longer required since AGP 9.0", real failure quoted under Build attempts), so the upstream-approved classpath pin (TOOLCHAIN.md build.gradle:18) is used instead |
| JDK | 21 (`jvmToolchain(21)`, JavaVersion.VERSION_21) | mandated |
| SDK | compileSdk 36, targetSdk 36, minSdk 30 | mandated (ADR-0003 D-007) |
| ktlint Gradle plugin | org.jlleitschuh.gradle.ktlint **14.2.0** | `gh release list --repo JLLeitschuh/ktlint-gradle --limit 6`: `v14.2.0 Latest` (2026-03-12); note the plugin's own version is not the ktlint engine version (1.8.0) — first attempt with `1.8.0` failed plugin resolution ("was not found in any of the following sources", exit 1) |
| detekt | **1.23.8** | `gh release list --repo detekt/detekt --limit 12` filtered for non-prerelease: `v1.23.8 Latest` (2025-02-21); 2.0.x exist only as alpha |
| CI actions | actions/checkout v7.0.1, actions/setup-java v6.0.1, gradle/actions wrapper-validation + setup-gradle v6.4.0, actions/upload-artifact v7.0.1 | `gh release list --limit 3` for each repo, latest stable non-prerelease picked; tag existence double-checked via `gh api repos/<owner>/<repo>/git/ref/tags/<tag>` (all returned an object sha) |
| JUnit | 4.13.2 (junit:junit from Maven Central) | stock unit-test dependency so `testDebugUnitTest` has a real test |

## Files created (all under the directories the task names)

- `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle/libs.versions.toml` (version catalog), `android/.gitignore`
- `android/gradle/wrapper/` (jar + properties with pinned distributionSha256Sum), `android/gradlew`, `android/gradlew.bat`
- `android/app/build.gradle.kts` — `applicationId ai.eqo.app`, namespace `ai.eqo`
- `android/app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/ai/eqo/MainActivity.kt` (Compose-free launcher Activity), `app/src/main/res/layout/main.xml`, `app/src/main/res/values/strings.xml` (EQO label), `app/src/main/res/drawable/ic_launcher.xml`
- `android/app/src/test/kotlin/ai/eqo/MainActivityTest.kt` — one real JUnit test
- `android/app/lint.xml` — see "Lint baseline decision"
- `android/Phase-One/evidence/task-003-skeleton-ci.md` (this file)
- `.github/workflows/ci.yml` — android job enabled (see CI section)

## Build attempts (new skeleton, in-place)

1. `./gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug` attempt 1 → **exit 1**, `BUILD FAILED in 3m 32s`. Root cause (lint, warningsAsErrors=true, 6 errors): OldTargetApi, AndroidGradlePluginVersion, NewerVersionAvailable (x2), GradleDependency (compileSdk 36 vs 37), MissingApplicationIcon. Full text report quoted verbatim from `app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt`.
   - Fix for MissingApplicationIcon: added a vector drawable and `android:icon="@drawable/ic_launcher"`.
   - The four version-currency checks disabled in `app/lint.xml` with a comment citing TOOLCHAIN.md — the mandated pins (Gradle 9.7.0/AGP 9.3.1/Kotlin 2.4.0/compileSdk 36) are by definition older than what lint sees as latest. Not a blanket ignore: every other check still fails the build.
2. Same command attempt 2 → **exit 0**, `BUILD SUCCESSFUL in 2m 11s`, `92 actionable tasks: 48 executed, 44 up-to-date` (log `C:\Users\<user>\Claude\worktrees\_upstream\t003-build-run1.log`).
3. `./gradlew.bat ktlintCheck detekt test` → **exit 0**, `BUILD SUCCESSFUL in 1m 7s`, `32 actionable tasks: 8 executed, 24 up-to-date` (log `C:\Users\<user>\Claude\worktrees\_upstream\t003-static-run1.log`).

## Clean-clone acceptance runs

Fresh clone of the pushed branch:
`git clone --branch agent/android/8-skeleton-ci https://github.com/imbalki/Project-EQO.git C:\Users\<user>\Claude\worktrees\_upstream\t003-clean` → clone succeeded (exit 0), HEAD `4db46bec6cae0f522f34028ba9e88edcace32eed`.

All runs in `android/` of the clone, `JAVA_HOME` Temurin 21, `ANDROID_HOME`+`ANDROID_SDK_ROOT` set:

| Run | Command | Exit | Tail |
|---|---|---|---|
| t003-clean-build-1.log | `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug` | **0** | `BUILD SUCCESSFUL in 55s / 92 actionable tasks: 92 executed` |
| t003-clean-build-2.log | same, immediately again | **0** | `BUILD SUCCESSFUL in 13s / 92 actionable tasks: 2 executed, 90 up-to-date` |
| t003-clean-static.log | `ktlintCheck detekt test` | **0** | `BUILD SUCCESSFUL in 37s / 32 actionable tasks: 8 executed, 24 up-to-date` |

Acceptance criterion "builds twice in a row" met. `testDebugUnitTest` ran the one JUnit test (task list shows `:app:testDebugUnitTest` executed in run 1).

## Gate greps (in the clean clone, exit quoted)

1. No-Leap gate: `grep -rniE "liquid|leap-sdk|ai\.liquid" android/ --include=*.gradle --include=*.kts --include=*.kt --include=*.toml` → **no output, exit 1** (no matches; expected per the gate).
2. Rejects gate: `grep -rniE "abortOnError|lintOptions|ignore\b" android/ --include=*.gradle --include=*.kts` → **no output, exit 1** (no matches).

## Lint baseline decision

The TASK-002 findings (android/Phase-One/evidence/task-002-lint, 40 errors / 2 hints) apply only to the upstream OpenDroid codebase (DefaultLocale, NonObservableLocale, AutoboxingStateCreation, ModifierParameter, UseKtx, SdCardPath, ObsoleteSdkInt in OpenDroid sources). This skeleton contains none of that code, so **no lint baseline file was created**. The 6 skeleton findings from build attempt 1 were individually resolved or narrowly disabled with a cited reason (see above) rather than baselined.

## CI

Targets `.github/workflows/ci.yml` (the upstream repo CI, enabled as the instructions direct — the "Do not edit upstream CI" line in the task body conflicts with the author instruction that explicitly says to enable the android job there; went with the author instruction). Trigger check: `on:` has both `pull_request` and `push: branches: [main]` — **this branch's runs are pull_request-triggered only**, via the lead's draft PR #32.

CI attempts (same commands as local, plus ktlint/detekt):

| Run | SHA | Conclusion | Root cause |
|---|---|---|---|
| [36916894472](https://github.com/imbalki/Project-EQO/actions/runs/36916894472) | 6225b0c0 | failure (0 jobs created) | workflow-file rejection: `if: hashFiles(...)` at job level is not an allowed context → caught locally with actionlint 1.7.12 (`calling function "hashFiles" is not allowed here`), moved to a step-level gate |
| [36921021058](https://github.com/imbalki/Project-EQO/actions/runs/36921021058) | eceef2ca | failure (android job) | `./gradlew: Permission denied` in "Assemble debug and release, unit tests, lint" (exit 126): gradlew committed 100644 from Windows → `git update-index --chmod=+x android/gradlew`, `git ls-files --stage` shows `100755 249efbb032ce46a80c687c0723eb172e85f6a136 0 android/gradlew` |
| [36921186231](https://github.com/imbalki/Project-EQO/actions/runs/36921186231) (PR #32) | d265f62 | **success** | — |

Run 36921186231 (draft PR #32): `repo-checks` success, `android` success (3m 56s). Artifacts verified present: `lint-report` (3070 bytes) and `test-results` (439 bytes), both `expired: false`, expiring 2026-10-31T20:26-53Z = 30 days retention, uploaded with `if-no-files-found: error`.

Actions pinned by exact tag: actions/checkout@v7.0.1, actions/setup-java@v6.0.1, gradle/actions/wrapper-validation@v6.4.0, gradle/actions/setup-gradle@v6.4.0, actions/upload-artifact@v7.0.1.

## What is not verified

- The final merge of draft PR #32 / CI on main: the draft PR is the lead's; only this branch's pull_request run was observed green.
- No instrumented/connected test run (out of scope for this skeleton).
- The ktlint Gradle plugin 14.2.0's bundled `ktlint` CLI version was not independently verified against the 1.8.0 release page; the *plugin* release page is the cited source.
