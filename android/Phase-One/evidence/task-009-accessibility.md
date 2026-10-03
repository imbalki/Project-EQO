# TASK-009 evidence: one accessibility service with takeover detection

Branch: `agent/android/14-accessibility` (worktree `C:\Users\<user>\Claude\worktrees\task-009`)
Issue: imbalki/project-eqo#14. Device: Realme Narzo 20 (RMX2193), Android 11 / API 30,
Realme UI 2.0, adb serial `<DEVICE_SERIAL>` - **physical device** (not emulator).
Env: `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`,
`ANDROID_HOME=C:\Users\<user>\Android\Sdk`.

All quoted command output below is real; every claimed result carries its exit code.

## What was built (all in `:platform-a11y`)

| Piece | File | Acceptance criterion |
|---|---|---|
| Single service registration | `platform-a11y/src/main/AndroidManifest.xml`, `src/main/res/xml/accessibility_service_config.xml` | 1 |
| Merged-manifest guard (build-failing) | `src/test/.../EqoSingleServiceManifestTest.kt` | 1 |
| Typed action layer observe/tap/scroll/type | `src/main/.../EqoAutomation.kt`, `A11yNode.kt`, `A11yResult.kt` | 2, 4 |
| Takeover detection + loop pause | `src/main/.../TakeoverDetector.kt`, `EQOAccessibilityService.kt` (touch probe), `AgentLoop.kt` (pause gate in `executePlanLoop`) | 3 |
| Typed error, no silent retry when accessibility is disabled | `EqoAutomation.runAction`, `GenericAppAutomator.kt` (retry only for the typed `NodeNotFound` cold-start case) | 4 |
| Android 13+ restricted-settings repair guidance (user grants, EQO never grants) | `src/main/.../AccessibilitySetupGuide.kt`, `EqoNeverGrantsTest.kt` | scope |
| Unit tests with fake node trees | `src/test/.../FakeNode.kt`, `EqoAutomationFakeTreeTest.kt`, `TakeoverDetectorTest.kt` | evidence |

## Design notes

1. **One service, two donors.** OpenDroid registers
   `.accessibility.OpenDroidAccessibilityService` and ClosePaw registers its own
   `AccessibilityPlatform` service in their donor manifests. EQO merges both into
   exactly one service, declared once in `:platform-a11y`'s manifest (the module
   both donors' code lives in). `EqoSingleServiceManifestTest` parses **every**
   `android/**/src/main/AndroidManifest.xml` and fails the build unless exactly one
   `<service>` carries the `android.accessibilityservice.AccessibilityService`
   action. The test-verified service attributes: `exported=false`,
   `permission=android.permission.BIND_ACCESSIBILITY_SERVICE`.
2. **Takeover detection.** `TakeoverDetector` is a pure state machine. Every EQO
   action (`EqoAutomation`) brackets itself with `onAgentActionStarted/Finished`.
   The service feeds it every screen touch it sees through two channels: the
   `TYPE_TOUCH_INTERACTION_START` accessibility event and a 1x1 `TYPE_ACCESSIBILITY_OVERLAY`
   probe window with `FLAG_WATCH_OUTSIDE_TOUCH` (receives `ACTION_OUTSIDE` for touches
   anywhere else on screen). A touch arriving while no `dispatchGesture` of ours is in
   flight is classified `USER`; a `USER` touch while an action is in flight latches the
   detector. `AgentLoop.executePlanLoop` checks the latch before every step: the loop
   stops dispatching, remaining steps stay `PENDING`, the plan becomes
   `PlanStatus.PAUSED`. (Resume UX is TASK-012's scope.)
   *Documented limitation:* while one of our `dispatchGesture` strokes is in flight,
   the probe cannot separate a second concurrent finger from our own stroke, so such
   touches are attributed to the agent. Node actions, observe, typing and retry waits
   are attributed correctly.
3. **Typed error, no silent retry.** `A11yError.AccessibilityDisabled` is returned on
   the **first** attempt whenever the service is unbound (the OS unbinds it the moment
   the user turns the service off mid-task). `GenericAppAutomator` retries ONLY the
   typed `NodeNotFound` (cold-start) case; `AccessibilityDisabled` and
   `TakeoverDetected` are never retried. Unit tests assert zero side effects and zero
   node-tree reads in the disabled case.
4. **EQO never grants.** `AccessibilitySetupGuide` opens Android's own Settings
   screens (`ACTION_APPLICATION_DETAILS_SETTINGS` for the Android 13+ "Allow
   restricted settings" step, `ACTION_ACCESSIBILITY_SETTINGS` for the grant) and
   lists the exact taps the user makes. `EqoNeverGrantsTest` scans every
   `:platform-a11y` source file and fails on any `Settings.Secure.putString*` /
   `enabled_accessibility_services` occurrence, so a grant can never be automated.

## Commands and results

1. Worktree created from origin/main `a567321` on branch `agent/android/14-accessibility`
   (`git worktree add`, exit 0; `git worktree list` shows
   `C:/Users/<user>/Claude/worktrees/task-009 a567321 [agent/android/14-accessibility]`).
2. Device probe (SDK adb `C:\Users\<user>\Android\Sdk\platform-tools\adb.exe` only):
   - `adb devices` -> `<DEVICE_SERIAL>\tdevice` (exit 0)
   - `adb shell getprop ro.build.version.release` -> `11`; `ro.build.version.sdk` -> `30`;
     `ro.product.model` -> `RMX2193` (exit 0) - physical Realme Narzo 20.
   - `adb shell settings get secure enabled_accessibility_services` -> `null`
     (no accessibility service enabled at probe time; this agent never enabled EQO's -
     see "What is not verified").
3. `./gradlew :platform-a11y:ktlintFormat` -> `BUILD SUCCESSFUL in 25s`, `KTLINT_EXIT=0`.
4. Merged-manifest proof (criterion 1): `./gradlew :platform-a11y:processDebugManifest` ->
   `BUILD SUCCESSFUL in 36s`, `MANIFEST_EXIT=0`, then counting services in the AGP-merged
   output `platform-a11y/build/intermediates/merged_manifest/debug/processDebugManifest/AndroidManifest.xml`:
   `1 a11y-action(s), 1 service(s)` (identical count in the aapt_friendly merged manifest).
5. `./gradlew :platform-a11y:ktlintCheck :platform-a11y:testDebugUnitTest :platform-a11y:detekt`:
   the final run was still executing at this hand-off (log
   `C:\Users\<user>\Claude\worktrees\_upstream\t009-test7.log`). Earlier runs of the same
   suite executed 33 tests; the single failure found was fixed (an assertion that
   contradicted the documented substring/tree-order tap contract), the one Kotlin
   compile error in tests was fixed, and every detekt finding on the new code was fixed
   in code (no rule disabled, no new baseline entry). THE FINAL EXIT CODE IS NOT YET
   CLAIMED - it must be quoted from t009-test7.log (or re-run) before this gate counts.

## Device test records

NOT RUN in this run - blocked on the accessibility grant (see below). The acceptance
criteria 2-5 need EQO's accessibility service enabled on RMX2193 and (for "user touch
during an agent action") a real touch from the owner. Per the lead's rule ("never grant
EQO the accessibility permission yourself - guide the user through Android settings
only") this agent did not write `enabled_accessibility_services` and did not enable the
service. `AccessibilitySetupGuide` (built in this task) is the guide to use for that
grant; on Android 11 the "Allow restricted settings" step does not apply.

Second blocker for end-to-end device proof: no APK in the repo currently binds the Hilt
graph the service needs (`ActionSequenceExecutor`, the 14 action-class interfaces and the
`MemoryStore`/`ChatHistoryStore`/`PlanStore`/`ServiceBridge`/`NotificationTapTarget`/
`HabitRoutineTracker` adapters are unbound - there is no `@Module` anywhere yet), so
`EQOAccessibilityService` cannot be instantiated in `:app` (which does not yet
depend on `:platform-a11y`) until app wiring lands. Options for the lead are in the
kanban comment on card t_f05a267a.

## What is not verified

(Updated for the TASK-009 fixes run, 2026-10-03. The "Device test records -
NOT RUN in this run" section above refers to the FIRST blocked run of this task
and is superseded by the run-4 device records and the fixes-run sections.)

- Criterion 2 (observe/tap/scroll/type on device): VERIFIED 00:39:55 on the
  record build (run 4). Not re-run on the fix build - the fix touches only the
  lint surface (TouchProbeView/performClick, build deps) and the owner-prompt
  signals; no criterion-2 unit test changed.
- Criterion 3 (user touch pauses the loop on device): VERIFIED 00:53:56 on the
  record build (run 4, owner's physical finger). NOT re-verified on the fix
  build: the re-check window could not open (the owner-prompt crash above) and
  the owner has parked the phone; the fix build (02:35) is NOT installed. One
  owner touch in a 120s in-process window is still owed - exact steps in the
  card's OWNER ACTION block.
- Criterion 4 (accessibility disabled mid-task typed error on device):
  VERIFIED 00:50:02 on the record build (run 4). Not re-run on the fix build -
  nothing it exercises changed.
- Criterion 5 (another accessibility app enabled does not break EQO): still NOT
  TESTED on device - no second accessibility app was ever enabled on RMX2193.
  No EQO code assumes exclusivity (the service reads `rootInActiveWindow`,
  writes no settings, and suppresses no other service; the manifest guard pins
  exactly one EQO service regardless of what else is enabled).
- The owner-prompt vibration/beep signals: NOT proven on device after the fix
  (fix build not installed). The banner text path is unchanged; the crash guard
  is unit-tested (`OwnerPromptSafetyTest`).
- The upstream pattern
  (`AccessibilityServiceTestHarness` + `A11yProbeActivity` in
  `_upstream/opendroid/app/src/androidTest`) was NOT ported, because its
  `enableService()` writes `settings put secure enabled_accessibility_services` -
  which the lead's rule forbids for this agent. Port it only with lead
  authorization.
- Emulator coverage (Android 12/13 legs of D-007): none in this run; no
  emulator evidence exists, so nothing is labelled emulator. Every device
  record above is from the physical Realme Narzo 20 (RMX2193, Android 11).

## Judgement calls / reviewer attention

- `TakeoverDetector.shared` is a process-wide singleton accessed directly (service +
  AgentLoop) instead of Hilt injection: keeps AgentLoop's constructor (and its detekt
  baseline entry) unchanged and removes one binding from the future app-wiring task.
- `GenericAppAutomator` (donor file) return types changed `Boolean` -> `A11yResult`
  (documented above and in the file header). No call sites existed before this change.
- The 1x1 touch-probe overlay window consumes exactly one screen pixel (0,0).
- `agents/android/tasks/INDEX.md` and the task file left untouched (card scope: touch
  only the directories the task names).

## Device records - physical Realme Narzo 20, Android 11 (2026-10-03, run 4)

All records below are from the in-process device records driver
(EqoDeviceRecordsDriver, logcat tag `EQO_RECORD`) on the owner's Realme Narzo
20 (Android 11, adb serial <DEVICE_SERIAL>). The instrumentation harness
(EqoDeviceRecordsTest) could NOT produce records on this device - see
"Per-device findings" below.

- Criterion 2 (observe/tap/scroll/type on the test app) = PASS, 00:39:55:
  `OBSERVE ok: test app screen text observed through EQO service`,
  `TAP ok: tap('PRESS ME') activated the test button`,
  `TYPE ok: typed 'hello from eqo' through EQO service into the test input`,
  `SCROLL ok: list scrolled 0 -> 1336 through EQO service`.
- Criterion 3 (user touch during an agent action pauses the loop) = PASS,
  00:53:56 (owner's physical finger, 11s into the 00:53:45-00:55:15 window):
  `TAKEOVER ok (mode=physical-owner-touch): user touch during agent action
  latched takeover`, then `PAUSE ok: plan-loop gate refused actions with typed
  TakeoverDetected` (both EqoAutomation.observe and GenericAppAutomator
  returned A11yError.TakeoverDetected while latched).
- Criterion 4 (accessibility disabled mid-task) = PASS, 00:50:02 (owner turned
  EQO off in Settings mid-task): `DISABLED ok: typed AccessibilityDisabled in
  0ms / wrapper 5ms, zero retries` - A11yError.AccessibilityDisabled on the
  FIRST attempt through EqoAutomation AND the retry-capable GenericAppAutomator
  wrapper, no silent retry.
- Criterion 5 (another accessibility app enabled) = NOT TESTED, 00:46:38:
  `enabled_accessibility_services (read-only probe):
  'com.opendroid.ai.test/com.opendroid.ai.accessibility.OpenDroidAccessibilityService'`
  -> `COEXISTENCE not tested: no second accessibility app enabled on this
  device`. Per the task wording this is recorded as not tested.

## Per-device findings (Realme Narzo 20, Android 11 - do not generalize)

1. `am instrument` cannot drive the a11y device records on this OEM build.
   Every `am instrument` run starts with `Force stopping com.opendroid.ai.test
   ...: start instr`, which kills the system-bound service; this OEM's
   AccessibilityManagerService then records the service as CRASHED
   (`dumpsys accessibility` -> `Crashed services:{...OpenDroidAccessibilityService}`),
   removes the ServiceRecord with NO pending restart, and does not rebind for
   the rest of the run (both instrumentation attempts waited 90s x 4 tests
   with a live process and got zero binds). Only a package replace (install)
   or the owner's own toggle clears the state. The in-process driver path
   (am start on the exported test activity, no force-stop) keeps the binding
   alive and is the path that produced the records.
2. Touches that land on EQO's floating accessibility overlay icon are NOT
   delivered as takeover events (the icon is a system overlay; touches on it
   generate no TYPE_TOUCH_INTERACTION_START / ACTION_OUTSIDE for the service).
   The owner's icon-tap during the 00:48:09-00:49:39 window recorded `FAIL: no
   user touch during an agent action was detected as takeover`; a tap on the
   blank screen latched immediately (00:53:56). Owner prompts must say to tap
   the screen surface, NOT the icon.
3. Own-gesture touch events arrive late: EQO's own dispatchGesture strokes
   surface as TYPE_TOUCH_INTERACTION_START AFTER the dispatch completes (the
   documented detector limitation's mirror case) and latch the detector as if
   a user touched. The driver clears these latches before non-takeover records
   (gestureNoiseRetry); the takeover record uses observe-only in-flight
   actions so any latch is a real finger.
4. Prompt visibility: the original small grey prompt line was partly hidden
   behind the floating icon and unseen by the owner (two windows expired).
   Owner prompts are now a full-width 28sp bold white-on-red banner at the top
   of the screen with a vibration + beep on every change (lead requirement).
5. The a11y service only stays creatable in the test APK because
   EqoTestHostApplication builds the Hilt test root component eagerly at
   process start (HiltTestApplication only creates it inside a HiltAndroidRule
   test; the system-created service otherwise crashes with "The component was
   not created" and the binding wedges into 30-min crash backoff).

## Gate (run 4, real output)

`./gradlew :platform-a11y:ktlintCheck :platform-a11y:testDebugUnitTest
:platform-a11y:detekt :platform-a11y:assembleDebugAndroidTest` ->
`BUILD SUCCESSFUL`, GATE_EXIT=0 (log: _upstream/t009-gate3.log).
Unit tests: 33 tests, 0 failures, 0 skipped (EqoAutomationFakeTreeTest 17,
EqoNeverGrantsTest 4, EqoSingleServiceManifestTest 3, TakeoverDetectorTest 6,
NeedsInputParamKeyTest 3).

## TASK-009 fixes run (2026-10-03): lintDebug errors + owner-prompt crash

### What the lead's fresh-clone gate found on `774c2ff` (lead's real output)

`./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck
detekt --max-workers=2` from a fresh clone of `774c2ff` -> `BUILD FAILED in
20m41s`, EXIT=1; tests were fine (317 tests, 0 failures, 2 skipped); lint found
4 ERRORS in `:platform-a11y` (`app/lint.xml` + `warningsAsErrors` in force):

1. `platform-a11y/build.gradle.kts:75` `androidTestImplementation("com.google.dagger:hilt-android-testing:2.60.1")` -> `Use version catalog instead [UseTomlInstead]`
2. `platform-a11y/build.gradle.kts:81` (second hard-coded androidTest dependency) -> same rule `[UseTomlInstead]`
3. `OpenDroidAccessibilityService.kt:254` `Custom view TouchProbeView has setOnTouchListener called on it but does not override performClick [ClickableViewAccessibility]`
4. `OpenDroidAccessibilityService.kt:255` `onTouch lambda should call View#performClick when a click is detected [ClickableViewAccessibility]`

### Fixes (no lint-baseline, no severity=ignore, no version bump)

- 1+2: the two androidTest dependencies moved into `gradle/libs.versions.toml`
  at the SAME pins the module already used (`hilt-android-testing` 2.60.1 via
  the existing `hilt` version entry, `androidx.test:runner` 1.7.0) and
  referenced as `libs.<alias>` from `platform-a11y/build.gradle.kts`.
- 3+4: `TouchProbeView` now overrides `performClick()` (delegates to
  `super.performClick()` and returns its result per the Android guideline) and
  the probe's touch listener calls `v.performClick()` on `ACTION_UP` only. The
  listener's `true` return and the `ACTION_OUTSIDE` /
  `TYPE_TOUCH_INTERACTION_START` takeover path are unchanged - pure
  lint/accessibility fix, detector untouched.
- Verified in the fixes run: `./gradlew.bat :platform-a11y:lintDebug
  --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m` -> `BUILD SUCCESSFUL in 1m
  13s`, exit 0; `platform-a11y/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt`
  -> `No issues found.` The repo-wide `lintDebug` of the final gate (below)
  re-verifies this on the committed tree.

### New device finding: owner-prompt signals crashed the records activity

The banner + vibration/beep prompt code landed in `774c2ff` at 01:00:42
(`git log -S 'notifyOwner' -- .../EqoTestTargetActivity.kt` -> `COMMIT 774c2ff
01:00:42`), i.e. AFTER the APK that produced the 00:39-00:53 records
(`firstInstallTime=2026-10-02 23:04:03`). Its first device use (this run) killed
the records activity:

```
10-03 02:16:23.340 E/AndroidRuntime( 4235): FATAL EXCEPTION: main
10-03 02:16:23.340 E/AndroidRuntime( 4235): Process: com.opendroid.ai.test, PID: 4235
10-03 02:16:23.340 E/AndroidRuntime( 4235): java.lang.SecurityException: Requires VIBRATE permission
10-03 02:16:23.340 E/AndroidRuntime( 4235): 	at android.os.Vibrator.vibrate(Vibrator.java:238)
10-03 02:16:23.340 E/AndroidRuntime( 4235): 	at com.opendroid.ai.test.EqoTestTargetActivity.notifyOwner(EqoTestTargetActivity.kt:174)
10-03 02:16:23.340 E/AndroidRuntime( 4235): 	at com.opendroid.ai.test.EqoTestTargetActivity.setPrompt$lambda$0(EqoTestTargetActivity.kt:166)
10-03 02:16:23.340 E/AndroidRuntime( 4235): 	at com.opendroid.ai.test.EqoDeviceRecordsDriver.prompt$lambda$0(EqoDeviceRecordsDriver.kt:82)
```

Same crash on every relaunch (pid 4925, `02:18:33.691`, identical stack). The
test APK declares no VIBRATE permission by design (the records harness adds no
permissions), so `Vibrator.vibrate()` always throws on this build and the
unguarded call closed the owner's touch window before it opened. **Correction to
per-device finding 4 above: the banner is fine, but the "vibration + beep" part
was never proven on device - it crashed on first use.**

Fix (this run): both prompt signals are wrapped in `runCatching { ... }`
(catch-all `Throwable`; NO `VIBRATE` permission added, banner unchanged). Cheap
guard: `OwnerPromptSafetyTest` (`:platform-a11y` `src/test`) fails the build if
any `vibrate(` call in the androidTest sources sits outside a `runCatching`
block (comment-aware scan). The guard demonstrably bites: its first run FAILED
on an unguarded occurrence (the word `vibrate(` inside a code comment; the scan
was made comment-aware) before the green run below.

### Device timeline of the criterion-3 re-check attempt (2026-10-03)

- 02:13:14 read-only bind check (SDK adb `C:\Users\<user>\Android\Sdk\platform-tools\adb.exe`):
  `enabled_accessibility_services` =
  `com.opendroid.ai.test/com.opendroid.ai.accessibility.OpenDroidAccessibilityService`,
  `accessibility_enabled` = `1`, `dumpsys accessibility` ->
  `Bound services:{Service[label=EQO, ...]}`, `Crashed services:{}` - the
  owner's grant was in place.
- 02:16:22 `adb shell am start -n com.opendroid.ai.test/com.opendroid.ai.test.EqoTestTargetActivity --ez runRecords true --es records 2`
  -> `Starting: Intent { cmp=com.opendroid.ai.test/.EqoTestTargetActivity (has extras) }`, AM_EXIT=0 (in-process driver, criterion 3 only, 120s window constant `OWNER_TOUCH_WINDOW_MS = 120000L`). The process died at 02:16:23.340 on the prompt crash above - no touch was ever awaited.
- Subsequent `[EQO_RECORD]` log lines (02:18:37.981 pid 5134, 02:18:47.509 pid 5225, 02:18:51.666 pid 5361, 02:19:11.610 pid 5490; each `DRIVER start: device records, physical Realme Narzo 20, Android 11, selected=[2]` then `AWAITING physical owner touch on screen (agent actions in flight)`) came from further `am start` runs this agent did not issue (this agent issued exactly one, at 02:16:22); each died at the same prompt crash.
- 02:24:35 `adb devices` -> `adb.exe: no devices/emulators found` (phone disconnected / parked by the owner). Per the lead (02:25) NOTHING was reinstalled and no further window was opened.

### The criterion-3 re-check on the fix build: superseded by the re-based build

The fix build exists (`platform-a11y/build/outputs/apk/androidTest/debug/platform-a11y-debug-androidTest.apk`, 70,395,552 bytes, built 2026-10-03 02:35) but was NOT installed on the phone (lead's order: the owner parks the phone; a reinstall may switch the accessibility grant off with nobody at the phone to restore it). UPDATE (2026-10-03 09:41): the re-check was ultimately taken on the RE-BASED `ai.eqo.test` build instead - see the run-5 section at the end of this file (`Criterion-3 re-check on the re-based build: DONE - PASS`). The originally planned re-check is ONE owner touch in a 120s in-process window (`records=2`, `EqoDeviceRecordsDriver`, banner prompt, tap the BLANK screen not the floating icon) after: reinstall of the same instrumentation APK (announced) -> read-only bind re-verify -> lead's "owner ready". The 00:53:56 PASS above remains the criterion-3 device record for the ORIGINAL build.

### Gates (fixes run, real output)

- Pre-gate (module): `./gradlew.bat :platform-a11y:testDebugUnitTest
  :platform-a11y:ktlintCheck :platform-a11y:detekt
  :platform-a11y:assembleDebugAndroidTest --max-workers=2
  -Dorg.gradle.jvmargs=-Xmx1536m` -> `BUILD SUCCESSFUL in 36s`,
  PREGATE_EXIT=0 (log `_upstream/t009-fixes-pregate2.log`). An earlier run of
  the same command ended `BUILD FAILED in 52s`, PREGATE_EXIT=1, with
  `OwnerPromptSafetyTest > everyVibrateCallInTheRecordsHarnessIsGuardedByRunCatching
  FAILED`, `34 tests completed, 1 failed` (log `_upstream/t009-fixes-pregate1.log`)
  - the new guard biting; the scan was made comment-aware and the green run is
  the one quoted.
- Instrumentation APK rebuilt from the fixed code:
  `platform-a11y/build/outputs/apk/androidTest/debug/platform-a11y-debug-androidTest.apk`,
  70,395,552 bytes, 2026-10-03 02:35. NOT installed on the phone (owner parked
  the phone).
- FULL gate, run ONCE: `./gradlew.bat assembleDebug assembleRelease
  testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2
  -Dorg.gradle.jvmargs=-Xmx1536m`, GATE_START 2026-10-03 02:35:54 ->
  `BUILD SUCCESSFUL in 9m 59s`, FULL_GATE_EXIT=0, GATE_END 2026-10-03 02:45:55
  (log `_upstream/t009-fixes-fullgate.log`). `:platform-a11y:lintDebug` - the
  task that failed the lead's gate - EXECUTED in this run and is clean;
  `:platform-a11y:testDebugUnitTest`, `:platform-a11y:ktlintCheck` and
  `:platform-a11y:detekt` show `UP-TO-DATE` (identical inputs ran green minutes
  earlier in the pre-gate).
- Test counts rolled by script from `**/build/test-results/testDebugUnitTest/TEST-*.xml`
  (37 XML files): `TOTAL: tests=318 failures=0 errors=0 skipped=2` (= the lead's
  317 + the new guard test; `:platform-a11y` = 34:
  EqoAutomationFakeTreeTest 17, EqoNeverGrantsTest 4,
  EqoSingleServiceManifestTest 3, OwnerPromptSafetyTest 1,
  TakeoverDetectorTest 6, NeedsInputParamKeyTest 3).
- No-Leap gate: `grep -rniE "liquid|leap-sdk|ai\.liquid" android/
  --include=*.gradle --include=*.kts --include=*.kt --include=*.toml` -> no
  output, exit 1 (gate satisfied).

## Re-base onto main (2026-10-03): ai.eqo rename applied; code-side re-verified

Lead-ordered re-base of TASK-009 onto `origin/main` (TASK-005's `ai.eqo` rename +
TASK-014 + TASK-006 merged). **No phone work in this run** (owner away, device
disconnected; no adb, no installs, no instrumentation) - code side only.

### Heads and safety net (real commands)

- `git fetch origin` (exit 0); pre-rebase head `5d94feec4f8c`, `origin/main`
  `edf94033ddd5` (`git rev-parse --short=12 HEAD` / `origin/main`).
- Safety branch first: `git branch backup/014-pre-rebase 5d94fee` (LOCAL only,
  never pushed).
- `git merge-base a567321 HEAD` -> `a567321866c9` (branch based on old main, as
  documented; 11 commits in `a567321..5d94fee`).

### Rebase and conflict log

`git rebase --onto origin/main a567321 agent/android/14-accessibility`
-> `Successfully rebased and updated refs/heads/agent/android/14-accessibility.`,
exit 0. Two stops, both mechanical rename shapes, both resolved keeping
TASK-009's intent on the renamed tree:

- 2/11 (`0e59a69` typed action layer): 5 file-location conflicts (git's
  directory-rename suggestion `com/opendroid/ai/accessibility/` ->
  `ai/eqo/accessibility/` for the added `A11yNode`/`A11yResult`/`EqoAutomation`/
  `NodeTreeSearch`/`TakeoverDetector`), 1 content conflict in
  `GenericAppAutomator.kt`. Both sides were read before resolving:
  `GenericAppAutomator.kt` is **rename-only** on the main side (proved:
  `git show a567321:<old> | renorm` == `git show origin/main:<new>`,
  byte-identical), and for `EQOAccessibilityService.kt` / `AgentLoop.kt` the
  only main-side deltas are import ORDERING (ktlint), so TASK-009's typed
  `A11yResult` contract was kept over the pre-rename `Boolean` API and main's
  import order was preserved.
- 3/11 (`0be4965` setup guide): file-location conflict for the added
  `AccessibilitySetupGuide.kt`, plus `EqoNeverGrantsTest.kt` landed at the old
  test path (git could not suggest a move: that test dir did not exist at
  `a567321`) - moved by hand to `src/test/java/ai/eqo/accessibility/`.

Commits 4-11 applied silently as patch content at pre-rename names, so a fixup
commit finished the rename. Old -> new SHA map (11 replayed):

| old | new | subject |
|---|---|---|
| `9e6f361` | `61d6211` | register exactly one EQO accessibility service |
| `0e59a69` | `8f0d412` | typed a11y action layer with takeover detection |
| `0be4965` | `3a73764` | guide Android 13+ restricted-settings repair, EQO never grants |
| `f6d591e` | `798355f` | fake node trees for observe/tap/scroll/type and takeover |
| `716d133` | `cefefe5` | evidence - one a11y service, takeover, typed errors |
| `1e30077` | `19edc84` | androidTest Hilt stubs and device records harness |
| `4202e04` | `cb8227a` | eager Hilt test component and in-process device records |
| `774c2ff` | `4812401` | device records for criteria 2-5 + per-device findings |
| `cc50dab` | `458968d` | version catalog deps and TouchProbeView performClick |
| `b9eb033` | `7d5403f` | crash-safe owner prompt signals with guard test |
| `5d94fee` | `40dc4e4` | fixes evidence - lint errors + owner-prompt crash |

Two new commits on top: `e7cb3c6` `refactor(android): apply ai.eqo namespace to
TASK-009 sources and tests` (12 test/androidTest files moved
`com/opendroid/ai/**` -> `ai/eqo/**`, package/import/class references renamed
`OpenDroidAccessibilityService` -> `EQOAccessibilityService`, ktlint import
order re-sorted for the renamed packages, `EqoSingleServiceManifestTest`
retargeted to the new service component with assertions kept, and the two new
XML comments worded without upstream project names) and `df03bf5` (19
provenance-map rows). `// Origin:` provenance headers keep the ORIGINAL
upstream path unchanged (e.g. `EQOAccessibilityService.kt` line 1 still points
at `app/src/main/java/com/opendroid/ai/accessibility/OpenDroidAccessibilityService.kt`),
the adapter "upstream class ..." provenance comments are untouched, and the
`dagger.hilt.android.internal.testing` package of
`EqoEagerTestComponentBootstrap.java` was NOT renamed.

### Proof the rebase is mechanical (step 4)

```
git diff a567321 5d94fee -- '*.kt' '*.java' '*.xml' '*.kts' '*.toml' | grep -E '^[-+]'
  | grep -vE '^(+++|---)' | python renorm3.py norm-text | sort > pre.txt
git diff origin/main HEAD  -- '*.kt' '*.java' '*.xml' '*.kts' '*.toml' | grep -E '^[-+]'
  | grep -vE '^(+++|---)' | python renorm3.py norm-text | sort > post.txt
comm -3 pre.txt post.txt
```

Normalization (card rules, `// Origin:` lines excluded):
`com.opendroid.ai->ai.eqo`, `com/opendroid/ai->ai/eqo`,
`OpenDroid|OPENDROID|opendroid->EQO|EQO|eqo` (the `opendroid_prefs` literal is
kept; `ClosePaw` is not touched - it stays as donor-project attribution).

- pre changed lines (normalized): **3194**; post changed lines (normalized):
  **3193**. Residual lines from `comm -3`: **19** (10 only-pre, 9 only-post).
- **Every residual is part of the two reworded XML comment blocks**: (a)
  `platform-a11y/src/main/AndroidManifest.xml`'s TASK-009 "exactly ONE
  accessibility service" comment and (b)
  `platform-a11y/src/main/res/xml/accessibility_service_config.xml`'s header
  comment. Both were reworded to name no upstream project (branding direction:
  word comments without the upstream project name) - comment TEXT only. The
  `android:name="ai.eqo.accessibility.EQOAccessibilityService"` attribute and
  every other changed line normalize identically on both sides. Nothing else
  differs: no logic, string, assertion, path or build-file drift.

### New names the owner will see on the phone

Real output from
`platform-a11y/build/intermediates/packaged_manifests/debugAndroidTest/processDebugAndroidTestManifest/AndroidManifest.xml`:
`package="ai.eqo.test"`, instrumentation label `Tests for ai.eqo.test`,
`android:name="ai.eqo.test.EqoTestHostApplication"`, and exactly one a11y
`<service android:name="ai.eqo.accessibility.EQOAccessibilityService">`.

- Test package (instrumentation APK applicationId): **`ai.eqo.test`** (was
  `com.opendroid.ai.test`; AGP derives it as namespace `ai.eqo` + `.test`).
- Service component the owner must enable/grant:
  **`ai.eqo.test/ai.eqo.accessibility.EQOAccessibilityService`** (was
  `com.opendroid.ai.test/com.opendroid.ai.accessibility.OpenDroidAccessibilityService`).
  The old grant points at the old component and does not carry over - the
  service must be re-granted under the new name through the
  `AccessibilitySetupGuide` flow (EQO never grants).
- Merged MAIN debug manifest still proves criterion 1:
  `1 a11y-action(s), 1 service(s)`, the service being
  `ai.eqo.accessibility.EQOAccessibilityService`.

### Gates (re-base run, real output)

- FULL gate, run ONCE:
  `./gradlew --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m
  -Pkotlin.daemon.jvmargs=-Xmx1024m assembleDebug assembleRelease
  testDebugUnitTest --rerun lintDebug ktlintCheck detekt
  :platform-a11y:assembleDebugAndroidTest`
  -> `BUILD SUCCESSFUL in 15m 58s`, `512 actionable tasks: 320 executed, 192
  up-to-date`, GATE_EXIT=0. `:platform-a11y:assembleDebugAndroidTest` EXECUTED
  and packaged the test APK from the renamed sources.
- Test counts rolled by script from `**/build/test-results/testDebugUnitTest/TEST-*.xml`
  (55 XML files, all timestamped in this run, 06:58-07:00 - no stale results):
  `TOTAL: tests=374 failures=0 errors=0 skipped=1`. `:platform-a11y` = 34:
  EqoAutomationFakeTreeTest 17, EqoNeverGrantsTest 4,
  EqoSingleServiceManifestTest 3, OwnerPromptSafetyTest 1,
  TakeoverDetectorTest 6, NeedsInputParamKeyTest 3 - **EqoNeverGrantsTest and
  EqoSingleServiceManifestTest both pass and scan the renamed paths** (the
  single-service test passing is itself the proof it found the new component).
- `bash scripts/check-branding.sh` -> `BRANDING GATE PASSED`, exit 0
  (`kt files: 202; provenance rows: 202`, sets identical after `df03bf5`).
- `bash scripts/check.sh` -> `== Secret scan (basic) ==` (no hits) then
  `BRANDING GATE PASSED`, final `OK`, CHECK_EXIT=0 (secret scan + `bash -n` on
  scripts + the branding gate).
- No-Leap gate: `grep -rniE "liquid|leap-sdk|ai\.liquid" android/
  --include=*.gradle --include=*.kts --include=*.kt --include=*.toml` -> no
  output, exit 1 (gate satisfied).
- Secrets gate: `git grep -InE` for `sk-or-v1-...` / `AKIA...` / `ghp_...`
  patterns (`scripts/check.sh` scan) -> no output, exit 1 (gate satisfied).

### What is NOT verified after the re-base (unchanged honesty)

- **Criterion 3 touch record on the fixed AND re-based build: RECORDED
  2026-10-03 09:41-09:42 on the re-based `ai.eqo.test` build (head `2d2f587`) =
  PASS** (run-5 section at the end of this file) - no longer owed. The record
  was taken exactly as planned: freshly installed `ai.eqo.test` build after the
  owner re-granted `ai.eqo.test/ai.eqo.accessibility.EQOAccessibilityService`
  by hand, one tap on the BLANK screen (not the floating icon) in the 120s
  in-process window (`records=2`). The 00:53:56 PASS remains the criterion-3
  device record for the ORIGINAL (pre-rebase, old-names) build.
- **Criterion 5 (another accessibility app enabled does not break EQO): still
  NOT tested** - no second accessibility app was ever enabled on RMX2193.
- No device work of any kind ran in this re-base run (phone disconnected, per
  the lead's rule). All device records in the sections above are from the
  pre-rebase build (the ONLY device record taken on the re-based build is the
  run-5 criterion-3 re-check at the end of this file) and therefore quote the
  OLD component names exactly as
  captured then (`com.opendroid.ai.test/com.opendroid.ai.accessibility.OpenDroidAccessibilityService`).
- The owner-prompt banner + crash-safe prompt path: proven on device in run 5
  (09:41-09:42 window: banner shown, driver completed, no crash - the 02:16:23
  VIBRATE SecurityException did not recur). The vibration/beep signals remain
  NOT proven as signals: the test APK declares no VIBRATE permission by design
  and the calls are wrapped in `runCatching`, so they no-op silently.

## Criterion-3 re-check on the re-based build: DONE - PASS (2026-10-03, run 5)

The one record owed after the re-base, taken on the owner's Realme Narzo 20
(RMX2193, Android 11, adb serial <DEVICE_SERIAL>) with the in-process
`EqoDeviceRecordsDriver` (logcat tag `EQO_RECORD`).

Build: the lead's fresh-clone instrumentation APK of head `2d2f587`
(`git -C _lead-verify9r rev-parse --short HEAD` -> `2d2f587`, branch
`agent/android/14-accessibility`):
`platform-a11y/build/outputs/apk/androidTest/debug/platform-a11y-debug-androidTest.apk`,
70,333,358 bytes, built 2026-10-03 07:34. Package `ai.eqo.test`
(`aapt dump badging` -> `package: name='ai.eqo.test'`), the one a11y service
`ai.eqo.accessibility.EQOAccessibilityService` (label `EQO`).

Device timeline (all via the SDK adb only; zero settings writes, EQO never
grants itself anything):

- 09:12:29 `adb uninstall com.opendroid.ai.test` -> `Success` (pre-rename test
  app and its old-named service removed), then `adb install -r` of the APK
  above -> `Performing Streamed Install / Success`, `pm list packages
  ai.eqo.test` -> `package:ai.eqo.test`, `dumpsys package ai.eqo.test` ->
  `firstInstallTime=2026-10-03 09:12:29`. Immediately after the install:
  `Enabled services:{}`, `Bound services:{}`, `Crashed services:{}` and
  `enabled_accessibility_services` = `null` - the old grant does not carry over
  the rename.
- 09:28:20 OWNER granted the new component by hand (Settings > Additional
  Settings > Accessibility > Downloaded services > EQO > ON). Read-only
  re-verify afterwards: `settings get secure enabled_accessibility_services` ->
  `ai.eqo.test/ai.eqo.accessibility.EQOAccessibilityService`,
  `accessibility_enabled` -> `1`, `dumpsys accessibility` -> `Bound
  services:{Service[label=EQO, feedbackType[FEEDBACK_GENERIC], capabilities=161,
  eventTypes=[TYPE_VIEW_CLICKED, TYPE_VIEW_TEXT_CHANGED, TYPE_WINDOW_STATE_CHANGED,
  TYPE_WINDOW_CONTENT_CHANGED, TYPE_VIEW_SCROLLED, TYPE_TOUCH_INTERACTION_START,
  TYPE_TOUCH_INTERACTION_END], notificationTimeout=100, requestA11yBtn=false]}`,
  `Binding services:{}`, `Crashed services:{}`.
- WINDOW START 2026-10-03 09:41:13 (PC clock; phone clock identical at check
  time: `adb shell date` -> `Sat Oct  3 09:43:57 IST 2026` vs PC
  `2026-10-03 09:43:57`): `adb shell am start -n
  ai.eqo.test/ai.eqo.test.EqoTestTargetActivity --ez runRecords true --es
  records 2` -> `Starting: Intent { cmp=ai.eqo.test/.EqoTestTargetActivity (has extras) }`,
  AM_EXIT=0. In-process driver, criterion 3 only (`records=2`), 120s window
  constant `OWNER_TOUCH_WINDOW_MS = 120000L`, NO `am instrument`, NO
  force-stop. The large red top banner prompt was up for the window.
- WINDOW END (observed) 2026-10-03 09:42:20.487 - takeover latched at
  09:42:20.484, 66.6s into the window (scheduled end would have been 09:43:13).

`[EQO_RECORD]` lines verbatim (live capture, `adb logcat -v time -s
EQO_RECORD:*`, pid 22148):

```
10-03 09:41:13.925 I/EQO_RECORD(22148): DRIVER start: device records, physical Realme Narzo 20, Android 11, selected=[2]
10-03 09:41:13.928 I/EQO_RECORD(22148): AWAITING physical owner touch on screen (agent actions in flight)
10-03 09:42:20.484 I/EQO_RECORD(22148): TAKEOVER ok (mode=physical-owner-touch): user touch during agent action latched takeover
10-03 09:42:20.487 I/EQO_RECORD(22148): PAUSE ok: plan-loop gate refused actions with typed TakeoverDetected
10-03 09:42:20.487 I/EQO_RECORD(22148): takeover resumed by the driver for the remaining records
10-03 09:42:20.487 I/EQO_RECORD(22148): DRIVER done
```

**Criterion 3 on the re-based build = PASS.** The owner's physical finger
tapped the BLANK screen once (not the floating icon) while the observe-only
agent action loop was continuously in flight (observe() dispatches no gesture,
so any latch is a real finger): the takeover detector latched
`mode=physical-owner-touch` and the plan-loop gate then refused actions with
the typed `A11yError.TakeoverDetected` (both `EqoAutomation.observe` and
`GenericAppAutomator.scrapeScreen`). No `FAIL:`, no `TIMEOUT`, no
`DRIVER FAILED` and no crash line in the capture.

Scope of this record (unchanged honesty): this is the ONLY new device record on
the re-based build. **Criterion 2 and criterion 4 remain recorded from the
PRE-FIX build** (00:39:55 / 00:50:02 above, old component names), and
**criterion 5 remains NOT tested** (no second accessibility app on this
device).
