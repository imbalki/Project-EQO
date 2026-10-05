# TASK-076: replay run latch and shared scroll onto main

Base: `821f75206f38e8d0f8c90390bca65063e3e6efaf`.
Source: `f22849be9a900a21099779178a448b968ada27bc`.
Branch: `agent/android/76-run-scroll-rebased`.
Worktree: `C:/Users/<user>/Claude/worktrees/task-076`.

All nine source-only commits were replayed chronologically with cherry-pick. No merge, force push or PR creation. Prior TASK-071/073 evidence is historical; this document records validation on the rebased tree.

## Manual resolutions and semantic adaptations

1. `TaskActivity.kt`, 1528a29: keep the Run/Plan click's explicit confirmation and shared detector reset, but call main's `planRequest()` rather than the removed no-argument `startRun()`. The typed request is still planned, previewed and approved before `startRun(approved)`; no automatic sample is restored. Touch guard remains installed by the subsequent original commits. Resume mint remains solely in the Resume confirmation positive-button handler.
2. `task-005-provenance-map.md`, b1da177: keep main's TASK-068/069 rows and add TASK-073 RunDiagnostics row. The adjacent sections collided only because both insert above TASK-066. No existing provenance rows removed.
3. `EqoAutomationPort.kt`, b1da177: retain main's navigation-port delegation and all typed request app-launch/type-target/chat/email/SMS methods. Place observe/tap/type-text/scroll typed failures and fixed-code EqoRun logging in the delegated `EqoNavigationPort`, which implements `lastFailure()`. Keep messaging in the outer port; do not paste the older, smaller port over main.
4. `StudyLoopWiring.kt`, b1da177: keep main's approved-plan check, explicit handler registry, new verbs, target typing and truthful success receipts. Adapt the original blank-observation failure and typed port failure handling into that dispatcher. Generic fallback remains non-transient for observe AND irreversible actions, preserving main's irreversible-action retry safety.
5. `ActionLoop.kt`, b1da177: retain main's `synchronized(lock)` blocks around applyCommand and plan-status delivery. Add fixed-code diagnostic callbacks inside the existing lock, not the source branch's unsynchronized replacements. Resume/terminal/submit logic and callback ordering are unchanged. Keep the source permission-check invocation for every step and all source diagnostic hooks.
6. `EqoAutomationPort.kt`, f22849b: extract `serviceGlobalAction` outside the delegated navigation class, as in the source static-check fix; keep delegated enter and outer typed/messaging operations. Back/home still clear the navigation failure before applying global actions.
7. Both exact-handler SF-4 guards: change only the terminal Run-handler callee from `startRun()` to `planRequest()`. Continue to require exactly two mint sites, inside the actual Run and Resume positive-button handlers. No file-wide allowance or count reduction.
8. `SampleRunRegressionTest.kt`: main removed the automatic `sampleSteps()` method and `task_draft_body` resource. Keep the original observe/scroll/compose regression steps as a host test fixture with a fixed draft string, exercising the real StudyTaskController/StudyActionExecutor/approval dialog. Keep all order, takeover, obscuration, SMS intent, no-send and completion assertions. Main already protects the typed Plan button: retain that protection, replace the obsolete plain-Run filter=false assertion with filter=true, and add a real obscured DOWN/clean UP assertion that the latch stays set. The subsequent partial-overlay gesture must clear the stale latch while a blank typed request shows guidance and does NOT start an unapproved controller. This is the intended #67 behavior, not a restored sample shortcut.

## Mechanical proof

`task-076-mechanical-comparison.json` compares exact added/deleted lines (ignoring hunk location/context only) from source merge-base to source HEAD versus main to the rebased working tree; regenerate it with `python android/Phase-One/evidence/task-076-verify-rebase.py`. 16 of the 24 source-touched files are mechanically identical. Every non-identical file is covered above: provenance map, EqoAutomationPort, StudyLoopWiring, TaskActivity, SampleRunRegressionTest, StudyLoopWiringTest, ActionLoop, TakeoverResumeUserOnlyTest.

The comparison additionally extracts `resume`, `applyCommand`, `terminalize`, `emitPlanStatus`, and `submit`. Removing ONLY added diagnostic lines gives exact main function bodies for all five. The ordered 2,000-round `ActionLoopDispatchTraceTest` is byte-identical to main.

## Verification

JDK: Temurin 21.0.12.1+1-LTS. Gradle: 9.7.0 (executed wrapper `--version`). Kotlin: 2.4.0 and AGP: 9.3.1 (`android/gradle/libs.versions.toml`, actually used by the builds).

`:app:ktlintFormat :core-agent:ktlintFormat :platform-a11y:ktlintFormat` executed once and passed without changing files.

First targeted build finished (not terminated) after 19m52s. ActionLoop tests, including ordered stress, passed; platform scroll/route/security tests passed. App test compilation exposed the removed sample draft resource; the fixture now owns its draft string. The next targeted run exposed the obsolete plain-Run filter assertion; main's protected Plan button is retained and the test now verifies both blocked touch-point obscuration and accepted partial overlays. Kotlin daemon connection retries fell back automatically and caused substantial build delay.

Final targeted run: BUILD SUCCESSFUL in 1m11s; 186 actionable tasks, 2 executed, 184 up-to-date. XML totals: 94 tests, zero failures/errors/skips (app 33, core-agent 23, platform-a11y 38). Includes `ActionLoop*`, `TakeoverDetector*`, both exact-handler guards, `StudyLoopWiringTest`, `SampleRunRegressionTest`, `TaskControl*`, `NaturalTaskFlowTest`, `ConfirmationTouchGuardTest`, and the typed scroll/route tests. `task-076-targeted-tests.json` records suite-level XML counts/timestamps; up-to-date module results are from the passing executions in the immediately preceding targeted run.

Full gate command from `android/`:

```
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m
```

Repo checks run from the repository root (the scripts are located there, not under android/): `bash scripts/check-branding.sh`, `bash scripts/check.sh`.

## Full gate iterations

Run 1 of the exact full command finished (not terminated) after 4h53m59s: 787 actionable tasks, 610 executed, 177 up-to-date. Every test module passed except `core-llm:testDebugUnitTest` (262 tests, 1 failed, 1 skipped), whose `OpenRouterConnectionTestTest > a successful probe yields Connected with measured latency` saw `Failed(... error=Network ...)` from its local MockWebServer while the Kotlin compiler daemon was in connection-retry fallback. `scripts/check-branding.sh` and `scripts/check.sh` were run afterwards and both passed. The same test class then passed 6/6 in isolation.

Run 2 (after the isolated probe) reached `:app:detekt` and stopped with 4 weighted issues introduced by the manual merge resolutions: `StudyNavigationPort` grew to 11 functions when the typed-failure accessor was added (`TooManyFunctions`), one over-long `StudyLoopWiring` fallback line, one over-long regression-fixture line, and a 69-line regression test (`LongMethod`, max 60). All four are fixed without suppressions: the typed-failure accessor is a read-only `val` property (still defaulted null on the port interface and overridden by the delegated navigation port), the fallback constructor call is wrapped, and the three-step regression fixture moved to a `regressionPlan()` helper that also removes the over-long line. `:app:detekt` and `:app:ktlintCheck` then passed, as did the app targeted tests (StudyLoopWiringTest, SampleRunRegressionTest, TaskControl*, NaturalTaskFlowTest, SamplePracticeTest, ConfirmationTouchGuardTest).

## Final result

Third run of the exact full gate: BUILD SUCCESSFUL in 6m33s (1003 actionable tasks: 52 executed, 951 up-to-date), followed by `scripts/check-branding.sh` and `scripts/check.sh`; `gate-status-3.log` records gradle_exit=0, branding_exit=0, check_exit=0. Branding/provenance: 352 tracked Kotlin files = 352 provenance rows.

Unit-test totals from `*/build/test-results/testDebugUnitTest/TEST-*.xml` (116 suites): 697 tests, 0 failures, 0 errors, 1 skipped.

| module | tests | failures | errors | skipped |
| --- | --- | --- | --- | --- |
| actions-android | 17 | 0 | 0 | 0 |
| adb-pairing | 82 | 0 | 0 | 0 |
| app | 103 | 0 | 0 | 0 |
| browser-cdp | 35 | 0 | 0 | 0 |
| core-agent | 78 | 0 | 0 | 0 |
| core-llm | 262 | 0 | 0 | 1 |
| core-security | 47 | 0 | 0 | 0 |
| helper-server | 5 | 0 | 0 | 0 |
| platform-a11y | 68 | 0 | 0 | 0 |

XML timestamps show which run executed each module: `app` (the only module changed after run 2) re-executed in the final run; `core-llm`, `core-security`, `helper-server`, `platform-a11y` executed in run 2 after the detekt fix landed in `app` only; `actions-android`, `adb-pairing`, `browser-cdp`, `core-agent` executed in run 1. All are runs of the same gate command on this branch, and every unchanged task was UP-TO-DATE in the final run.

Versions actually used: JDK Temurin 21.0.12.1+1-LTS, Gradle 9.7.0 (wrapper), AGP 9.3.1, Kotlin 2.4.0.

Code HEAD: `bdb65023bc9e044dee39b7236a9532a3607ace05` (last code commit; the tip of `agent/android/76-run-scroll-rebased` adds only this evidence file on top of it and is verified equal to `origin/agent/android/76-run-scroll-rebased`). Nothing merged and no PR opened.
