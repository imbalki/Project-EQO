# TASK-073: shared accessibility scroll and EqoRun diagnostics

Base f8159c82fd6dbbe1913d0736621573e047dedac7 on origin/agent/android/71-run-latch-fix. Fresh detached worktree C:/Users/<user>/Claude/worktrees/task-073. No phone actions or PR; lead owns retest/PR.

## Operator scope cut

08:45 steering supersedes the original sample brief. All sample plan, wording, pacing and added sample-test changes were dropped. TaskActivity, sample regressions and resources remain at the parent version. Only shared execution/permission/diagnostic fixes and generic scroll logic tests remain.

## Grounded generic scroll defect

EqoAutomation selected the first isScrollable node and stopped if it rejected the direction. The raw service likewise returned immediately on a scrollable ancestor and did not visit its children after rejection. A boundary/incorrectly advertised outer container can hide a working nested or later target in any app.

Both now share NodeTreeSearch.scroll: depth-first try advertised scroll containers, continue after rejection into children/later siblings, stop after first acceptance. No-active-root, no-scrollable-node and all-rejected remain distinct failures. ServiceActionOps keeps its Boolean compatibility method with a typed result seam used by GatedServiceActions; real ActionRejected no longer becomes NodeNotFound. Takeover/service gates remain; no forced success, gesture fallback or auto-resume. Action acceptance is not independent proof of visible motion.

Generic fake-tree tests exercise rejected ancestor/working child, rejected first sibling/working later sibling, backward direction, stop after acceptance, missing root and rejected containers. Existing disabled-service/takeover/security assertions remain.

This is a verified generic defect, not proof of the phone's exact failure. If its root is unavailable, no container advertises scrollability, or all actions reject, failure remains honest. EqoRun distinguishes these cases for lead retest.

## Shared execution/diagnostics

ActionLoop invokes the real permission callback for every dispatch/retry, including null requiredPermission (one-line conceptual gate fix). StudyPermissionCheck only queries helper for explicit HELPER_BINDER, not accessibility-only steps. Empty/failed observations no longer report unconditional executor success. EqoAutomationPort preserves fixed failure codes and nonretryable disabled/unbound/secure/takeover/empty failures.

INFO EqoRun logs synthetic step ordinals, plan/step status, ExecuteResult kind, allowlisted fixed reason/note codes, verification classification, takeover cause, existing pacing boundaries and generic scroll outcome. No raw screen text, action parameters, SMS/recipient, target strings, exception messages, arbitrary executor detail or plan IDs. Timeout/cancel results classified by ActionLoop also have fixed unknown-effect codes. Labels are fixed strings, not reflective class names. Logs are diagnostics, never authorization.

Lead may capture `adb logcat -v threadtime -s EqoRun:I` during real-flow retest; worker did not invoke adb. Exactly two production UserResumeConfirmation mint handlers and OEM/dialog guards remain unchanged.

## Verification history

Pre-cut targeted app: 28 tests, zero failures/errors/skips. Core-agent: 63 tests, one existing threaded race assertion failure. Its non-RUNNING event check includes PAUSED before explicit resume, so failure is not yet proof of actual post-terminal dispatch. Assertions are unchanged; QA follow-up t_07c2490c investigates. Removed sample-only tests are not part of final change.

Scoped source was normal-pushed at b1da177, then formatter output and strengthened raw typed-scroll route guard at c8afe5d26227224838b3b4b4b4c25265d0f7f256; remote readback verified. No JVM stopped. The already-running pre-cut gate hit a mixed-snapshot debug test compile when scope changed; the later stable targeted build recompiled that interface successfully. A native-Python wrapper accidentally invoked WSL bash without Java/Git; no Gradle tests started there. Actual scoped checks ran directly in Git Bash with explicit installed JDK/SDK.

Final scoped targeted command: `:platform-a11y:testDebugUnitTest :app:testDebugUnitTest --tests ai.eqo.task.StudyLoopWiringTest`. Passed, exit 0: platform-a11y 68 tests and wiring 11 tests, zero failures/errors/skips. No added sample-specific regression tests survive.

One stable full gate ran `assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --continue`, max-workers=1, 1024m Gradle/Kotlin heaps, in-process Kotlin. Finished exit 1. Debug/release assembly, all lintDebug and ktlintCheck tasks completed without failure. JUnit XML: 649 tests, 1 failure, 0 errors, 1 skipped. By module: adb-pairing 82, app 87, browser-cdp 35, core-agent 63, core-llm 262, core-security 47, helper-server 5, platform-a11y 68. Only test failure: ActionLoopRaceTest threaded-round 7, no apply starts after terminal emission expected 0 but was 1. QA child has exact evidence and a reference to existing security terminal-settle work; this worker did not prove the race cause or suppress assertions. Existing skipped case is ModelDownloadSchedulingTest's pre-Q foreground-info service type.

App detekt also found two new issues: EqoAutomationPort function-count threshold and scroll expression line length. Minimal build-only correction moves the existing stateless gated service helper outside the class (failure reset remains inside back/home). An initial line wrap was rejoined by ktlint; a local forward variable now keeps the expression short. Final `:app:compileDebugKotlin :app:ktlintCheck :app:detekt` PASSED, exit 0, after that correction. The targeted suite and full gate are not rerun. No lint suppression or threshold change.

`bash scripts/check-branding.sh`, `bash scripts/check.sh`, and `git diff --check` passed, exit 0, with 327 Kotlin/provenance rows. Logs in the worktree: scoped-format-073.log, scoped-targeted-073.log, scoped-gate-073.log, scoped-repo-073.log, scoped-detekt-fix-073.log, scoped-detekt-final-073.log. Full gate is NOT claimed green. Lead retains phone retest/PR ownership.

Pre-existing combined helper+a11y permission early-return concern is separately routed to security t_63331155.
