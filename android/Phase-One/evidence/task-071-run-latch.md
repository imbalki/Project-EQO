# TASK-071: new-run latch hand-back and OEM touch compatibility

The Run click clears an earlier process-wide takeover latch using a newly minted UserResumeConfirmation before starting the sample plan. Background touches during the run still latch and pause; control-button touches do not latch. A stale latch alone yields PAUSED, not STOPPED; STOPPED requires a stop command.

## SF-4 invariant

Starting a new run is an explicit user hand-back. There are deliberately two production mint expressions: the Run button click and the resume confirmation dialog's positive-button click, both in TaskActivity.kt. TakeoverResumeUserOnlyTest pins the exact handlers and rejects minting in agent/service/recovery production code. StudyLoopWiringTest now also requires exactly two expressions in the exact TaskActivity path (scanning Kotlin and Java) and pins the Run resource-id/click chain and protected Resume resource-id/click -> confirmResume -> positive-button chain. No agent-reachable reset or parameterless resume is added.

## Operator's device finding (not a device test by this worker)

The lead reported on 2026-10-05 that 14fcd20 installed on Realme UI 2.0 did not respond to Run. Their window inspection found persistent OEM overlays including RapidReactionWindow, AssistOverlayBottom, ColorOSEdgeFloatBar, AssistPreviewPanel, HColorFullScreenDisplay, VColorFullScreenDisplay and Oppo Drag Foreground View, plus EQO's TouchProbeView. The lead identified PARTIALLY_OBSCURED being set for touches even when the touch point was not covered.

Per the lead's explicit policy decision, ConfirmationTouchGuard now matches framework filterTouchesWhenObscured semantics: FLAG_WINDOW_IS_OBSCURED rejects touch-point obscuration and poisons the gesture until a fresh valid DOWN; FLAG_WINDOW_IS_PARTIALLY_OBSCURED alone is accepted. Dialog views/buttons retain filterTouchesWhenObscured=true and the guard wrapper. The plain Run button is no longer wrapped; starting the plan does not approve SMS, which still requires its separate protected approval dialog. Resume protection remains in place.

Tests deliberately reflect this policy: partially obscured gestures are accepted, touch-point obscured DOWN/MOVE/UP remain rejected, mixed flags remain rejected, Run clears a stale latch, and real SMS approval refuses a touch-point obscured gesture then accepts a partially obscured gesture. The end-to-end sample uses virtual time, production steps/controller/approval UI, fake automation and SMS launching, and verifies ordered observe/scroll/compose_sms, one explicit-default ACTION_SENDTO smsto: draft, and COMPLETED receipt.

## Verification recorded so far

At a84876f the targeted app Takeover/TaskControl/Sample suite passed: 14 tests, zero failures/errors/skips. The initial two new-regression failures were corrected by draining the Robolectric main looper after ACTION_UP (Android posts button clicks); no assertions were removed to fix that scheduling error.

Final OEM-policy verification passed the complete Gradle gate (assembleDebug, assembleRelease, testDebugUnitTest, lintDebug, ktlintCheck, detekt), with --max-workers=1, 1024m Gradle/Kotlin heaps and in-process Kotlin. Parsed XML results: 645 tests, 0 failures, 0 errors, 1 skipped across 107 suites. The skipped case is ModelDownloadSchedulingTest: `foreground info omits service type before Android Q`. App: 87 tests, zero failures/errors/skips. Platform-a11y: 64 tests, zero failures/errors/skips. Both scripts/check-branding.sh and scripts/check.sh passed. Formatting and detekt fixes extracted unchanged assertion groups into helpers; no suppressions or safety assertions were removed.

The lead confirmed Run now responds on their phone at 76169d9, but reported a separate observe UNKNOWN / scroll FAILED / SMS-not-executed result. That follow-up is routed to t_3de89591 (eqo-core-dev), including privacy-safe EqoRun logging, helper-inactive reproduction and honest postcondition/pacing diagnosis. Source inspection shows UNKNOWN can also result from unmatched literal postconditions, not only Interrupted; no phone cause is claimed as proven by this worker.

The headless worker performs no phone actions and opens no PR; the lead owns phone verification and PR creation.
