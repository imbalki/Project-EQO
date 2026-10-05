# TASK-073: serialize ActionLoop resume and terminal transitions

Board card: `t_24ca6a4c`. Branch: `agent/android/73-actionloop-settle`.
Base: fetched `origin/main` at `91b3a9a7e1e65af0538cda99f30f8587c8f0e02f`.
Implementation commit: `77a1399`.

## Defect and change

`resume` held `lock` across the PAUSED/terminal guards, confirmation callback,
RUNNING state write and notification. `terminalize` previously performed its
terminal CAS and state write outside that monitor. A confirmation callback could
hold resume after its guards, let the runner publish terminal state, then overwrite
that state with RUNNING. This is distinct from TASK-072's PAUSED-history false
positive. The production fix does not change command/dispatch semantics. After
the first full gate hit the known legacy-classifier failure, this branch reused
TASK-072's already-authored fixture correction as a separate cherry-picked commit;
no safety assertion was weakened.

`applyCommand` now holds the same monitor across pause/takeover guards, reason,
state and notification. `terminalize` holds it across CAS, terminal state and
notification. `emitPlanStatus` serializes both history insertion and callback with
that monitor, not only history insertion. Existing resume, queue and report locks
are retained. Callbacks participate in transition ordering and must not wait for
another thread to acquire this monitor; there is no coroutine suspension inside
these transition functions.

## Deterministic regression and RED/GREEN

Two tests were added to the existing `ActionLoopResumeTest`: confirmed resume
cannot overwrite stop, and confirmed resume cannot overwrite cancel. A real loop
is paused through its public command/run path. `onResumeConfirmed` signals entry
then waits on a release latch while holding the resume monitor. A separate worker
invokes the private `terminalize` boundary by reflection, staging the runner's
already-dequeued terminal operation without adding a production test hook. This
is intentionally a transition-boundary test, not a claim of a public-API-only
end-to-end reproduction. Polling the queue would acquire the monitor first and
mask the old race if the terminal command were not already dequeued.

The worker must reach `Thread.State.BLOCKED` before the latch is released; there
is no fixed timing sleep. On unchanged production it has already written terminal
state and is blocked in status-history insertion. On fixed production it blocks
before the CAS/state writes. Futures propagate worker exceptions and have bounded
waits. Assertions cover final report/current state, terminal outcome, exact status
sequence and terminal callback state, no dispatch, no in-flight apply, NotExecuted
records and post-terminal resume rejection.

RED invocation, unchanged production:

    ./gradlew :core-agent:testDebugUnitTest --tests '*ActionLoopResumeTest*' --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m

Exit 1; XML: 6 tests, 2 failures, 0 errors, 0 skipped. Both failures are the new
state invariant, not a timeout or setup failure:

    resume must not resurrect CANCELLED expected:<CANCELLED> but was:<RUNNING>
    resume must not resurrect STOPPED expected:<STOPPED> but was:<RUNNING>

GREEN invocation after shared-lock fix:

    ./gradlew :core-agent:ktlintFormat :core-agent:testDebugUnitTest --tests '*ActionLoopResumeTest*' :core-agent:ktlintCheck :core-agent:detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m

Exit 0; XML: 6 tests, 0 failures/errors/skips. Actual test output:

    terminal=CANCELLED report=CANCELLED events=[(RUNNING, RUNNING), (PAUSED, PAUSED), (RUNNING, RUNNING), (CANCELLED, CANCELLED)]
    terminal=STOPPED report=STOPPED events=[(RUNNING, RUNNING), (PAUSED, PAUSED), (RUNNING, RUNNING), (CANCELLED, STOPPED)]

No new suppressions, test skips, baselines or safety-assertion relaxations.

## Immediately-before-dispatch ownership audit

`runApply` retains `awaitDispatchReady` immediately before in-flight tracking and
`async(start = CoroutineStart.UNDISPATCHED)` executor entry. Only the single run
coroutine calls `applyCommand`/`terminalize`. External pause/stop/cancel/takeover
only enqueue under `lock`; they do not write pause or terminal state. Concurrent
resume can only change PAUSED to RUNNING. Thus no competing writer can pause or
terminalize between this runner's final readiness check and inline executor entry.
The inline executor path was not changed or wrapped in a monitor held across
arbitrary executor code. Existing paused/suspended-gate/terminal dispatch guards
and command semantics remain intact. The regression's reflection worker is test
instrumentation, not a production external terminalization entry point.

## Full gate

Final corrected invocation exited 0: `BUILD SUCCESSFUL in 1h 59s`,
901 actionable tasks (232 executed, 669 up-to-date). Exact invocation (one Gradle
invocation at a time):

    ./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m
    scripts/check-branding.sh
    scripts/check.sh

Both repository scripts exited 0 after fixture integration; branding enumerated
335 Kotlin files and 335 provenance rows, and `check.sh` ended `OK`.

The first exact full gate (before fixture integration) failed after 1h31m35s:
core-agent had 74 tests / 1 failure, the known legacy classifier in threaded-round9
(`no apply starts after terminal emission expected 0 but was 1`). Its full log
and XML are retained. There was no unchanged lucky rerun. Authored TASK-072 commit
`24baad3a43b77d556f24f952c057120212128a20` was cherry-picked as
`9daa9c69448a85f352d96ebc4e1017063d7d043b`, bringing the corrected detector,
deterministic negative tests and ordered 2000-round stress. The original
`ActionLoopRaceTest.assertInvariants` body is byte-for-byte unchanged.

Final XML aggregation: 111 suites, 663 tests, 0 failures, 0 errors, 1 pre-existing
assumption skip (662 passed). The sole skip is core-llm's `ModelDownloadSchedulingTest`
`foreground info omits service type before Android Q`: pre-Q is unreachable with
minSdk30. Its test file is byte-for-byte identical to base `91b3a9a`; this card did
not introduce or change that assumption. All 78 core-agent tests passed with no
skips, including all six resume tests and all four dispatch-trace tests.

Ordered stress XML contains exactly 2000 distinct rounds, ending at round2000,
with all final-state, dispatch, in-flight, terminal-count and record assertions
passing. 73 rounds would have triggered the legacy PAUSED-history false positive.
Corrected detector's deterministic negative tests still reject dispatch after
each real terminal status and dispatch while paused. This stress pass complements,
not substitutes for, the latch-controlled RED/GREEN proof of the state-write fix.

## Review and downstream integration

Raw RED/GREEN XML/logs, initial failed-gate XML/log, final full-gate XML/logs and
script logs are bundled as `task-073-verification.zip` for same-card security
review. New code is isolated in `77a1399043363b78c7cedaf2bd6ae131a48da954`;
TASK-072 should cherry-pick that production/test fix only (it already owns the
fixture correction `24baad3`). It must integrate only after this card's security
review approval, then rerun its own corrected 2000-round ordered stress and full
gate. No PR is requested or created.

Hotspot: `android/Phase-One/evidence/task-005-provenance-map.md` carries TASK-072's
two imported authored test rows; retain these alongside sibling additions.
