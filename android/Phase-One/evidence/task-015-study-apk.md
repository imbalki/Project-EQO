# TASK-015 evidence — study APK and guided onboarding (S6)

Issue #20. Branch `agent/android/20-study-apk`, worktree `C:\Users\<user>\Claude\worktrees\task-015`.
Base: `origin/main` = `5dc0a5d`.

## 1. What shipped in this change

One sideload APK (`:app`, application id `ai.eqo.app`) whose first-run experience is the
guided onboarding of TASK-015's scope: model key, accessibility, wireless ADB, helper,
Chrome consent — plus the task screen and the Legal / open-source notices screen.

New code (all under `android/app/src/main/`):

| Area | Files | Requirement trace |
|---|---|---|
| Capability readiness model | `kotlin/ai/eqo/study/CapabilityReadiness.kt` | REQ-ADB-10, REQ-ADB-12, REQ-CDP-04, UF-08 (S-19) |
| Recovery guidance per failure class | `kotlin/ai/eqo/study/FailureClass.kt` | REQ-REC-01..10, REQ-TASK-07, UF-11/UF-12 (UF-R1..UF-R8) |
| Study-flow gates | `kotlin/ai/eqo/study/StudyFlowGate.kt` | TASK-008 SF-1 / TASK-010 SF-1 (see §3) |
| Task-screen presentation | `kotlin/ai/eqo/study/TaskPresentation.kt` | REQ-TASK-01..06 |
| ActionLoop production wiring | `kotlin/ai/eqo/task/StudyLoopWiring.kt`, `StudyTaskController.kt` | REQ-TASK-01..07, REQ-SMS-01, TASK-012 B2 |
| Task screen | `kotlin/ai/eqo/task/TaskActivity.kt`, `res/layout/task_screen.xml` | UF-09/UF-10 (S-20..S-23) |
| Automation port | `kotlin/ai/eqo/task/EqoAutomationPort.kt` | REQ-SMS-01/02, REQ-A11Y-04 |
| Setup hub dashboard | `kotlin/ai/eqo/onboarding/SetupHubActivity.kt`, `StudySetup.kt`, `res/layout/setup_hub.xml` | UF-08 (S-19), REQ-ADB-12 |
| Model key screen | `kotlin/ai/eqo/onboarding/ModelKeySetupActivity.kt` | REQ-BYOK-01/02/04 (UF-02, S-04/S-05) |
| Accessibility screen | `kotlin/ai/eqo/onboarding/AccessibilitySetupActivity.kt` | REQ-A11Y-01/02/03 (UF-03, S-06/S-07/S-25) |
| Wireless ADB screen | `kotlin/ai/eqo/onboarding/WirelessAdbSetupActivity.kt` | REQ-ADB-01..06, 11, 12 (UF-04/UF-05, S-08..S-11) |
| Browser consent screen | `kotlin/ai/eqo/onboarding/ChromeConsentActivity.kt` | REQ-CDP-01/04, REQ-PRIV-03/04 (UF-06, S-12..S-14) |
| Legal notices screen | `kotlin/ai/eqo/legal/LegalNoticesActivity.kt` | REQ-INS-03 (UF-14, S-28) |

Screen-state wording follows USER-FLOWS.md §16.2 (`Not set up / Checking… / Ready /
Needs attention / Dependent — re-check needed`), microcopy follows §18 (never "granted by
EQO", two ports two names, honest uncertainty, literal Pause/Stop wording, redaction
limits stated, Android/Chrome OS names verbatim).

### Capability readiness: "none inferred"

`CapabilityStatus` refuses a row written by a probe other than the capability's own
(`probeName` must equal `CapabilityId.probeName`), and `ReadinessSnapshot` never derives
one row from another. Helper readiness comes from the helper binder (`HelperActivationState`),
accessibility from Android's own `ENABLED_ACCESSIBILITY_SERVICES` (read-only), model key
from the credential store + live validation, wireless ADB and browser consent from their
own probes. A helper/ADB break marks only its dependents `Dependent — re-check needed`
(UF §16.2) and never touches the model-key or accessibility rows
(`CapabilityReadinessTest`).

## 2. Task screen wiring (the carry-over items)

`StudyTaskController` is the FIRST production constructor of `core-agent`'s `ActionLoop`:

- **(a) real gates.** `permissionCheck = StudyPermissionCheck(...)` — real
  `checkSelfPermission`, the accessibility-service state and the helper-binder plane,
  each denying with its own repair text. `appBlockPolicy = StudyAppBlockPolicy()` — an
  explicit policy object (D-010: no hard-block list; the deny set is empty *by decision*,
  the seam is consulted on every dispatch). Neither `permissionCheck = Granted` nor
  `AppBlockPolicy.ALLOW_ALL` appears in shipped wiring; `StudyLoopWiringTest` pins this.
- **(b) real user confirmation.** `UserResumeConfirmation.forExplicitUserConfirmation` is
  minted in exactly ONE place in app code: the resume button's dialog handler in
  `TaskActivity` (an actual user gesture). `StudyLoopWiringTest` asserts exactly one mint
  site in `app/src/main`; the TASK-012 SF-4 guard
  (`platform-a11y/src/test/java/ai/eqo/core/agent/TakeoverResumeUserOnlyTest.kt`) now
  allowlists precisely that file and still bans every other file in
  app/core-agent/core-llm/core-security/platform-a11y main sources. This is a deliberate,
  reviewed edit of a security guard test, made because the lead's carry-over (b) requires
  the mint to live in app UI code; the guard's invariant (agent-reachable code cannot mint
  a resume confirmation) is unchanged.
- **(c) unverified transports stay out of the shipped flow.** See §3.
- **(d) irreversible/outward actions need approval (TASK-012 B2).** The static
  `SensitivityApprovalPolicy` remains the only approval decision-maker; the approval card
  shows action + target + app with the PRD's 60s countdown (REQ-TASK-02) and an expired
  card is a rejection (never an approval). The only outward verb in this build is
  `compose_sms`, which is compose-only: it opens the messaging app with a filled draft and
  sends nothing (REQ-SMS-01).
- **(e) no `WRITE_SECURE_SETTINGS`.** Nothing writes `Settings.Secure`; the accessibility
  screen only opens Android's own settings screens and reads state
  (`AccessibilitySetupGuide` contract, `EqoNeverGrantsTest` in `:platform-a11y`).

## 3. Security disposition — the two study-flow gates

Both unverified transports are switched off in the shipped study flow, in one named place
(`StudyFlowGate`), and every affected screen says so out loud:

- `WIRELESS_CONNECT_PLANE_IN_STUDY_FLOW = false` — TASK-008 SF-1: the connect-plane TLS
  accepts any server certificate. The shipped app constructs **no** `WirelessAdbActivationRunner`
  / `AdbTlsClient` / `connectWithStls` call at all (`StudyFlowGateTest` greps `app/src/main`
  and fails on any). The guided wireless-ADB screen therefore shows each of the five checks
  (`PAIR, CONNECT, HELPER_START, AUTHORIZE, BINDER_HEALTH`) as its own row reporting
  "did not run — not available in the study build", with the pending work named:
  post-pairing server-key pinning / enrollment. The guidance text (what to tap in Android's
  own settings, pairing code vs pairing port vs connection port) ships in full.
- `CHROME_CDP_IN_STUDY_FLOW = false` — TASK-010 SF-1: the devtools endpoint check is
  name-only. The informed-consent screen ships (REQ-CDP-01 / REQ-PRIV-03/04: what would be
  sent, to where, redaction is best-effort, declining disables browser control only) and
  the consent answer is persisted, but no CDP client is constructed anywhere in app code
  and no page content is ever transmitted.

**Consequence, stated plainly:** with these gates off, the wireless-ADB and browser rows
cannot reach `Ready` in this build. That is intentional and it is the honest state; it is
also why acceptance criterion 2 ("a person who has not seen the project completes setup
using only the app") cannot be claimed green from this run — it needs the pinning work
above *and* a device (owner away). Flipping a gate is a code change that must land together
with the pinning / socket-owner verification it names.

## 4. Build and host verification

Commands run from `C:\Users\<user>\Claude\worktrees\task-015\android` with
`JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`,
`ANDROID_HOME=C:\Users\<user>\Android\Sdk`:

```
# :app:assembleRelease + :app:testDebugUnitTest + :platform-a11y:testDebugUnitTest
# (single Gradle invocation, from the worktree's android/ directory)
./gradlew :app:testDebugUnitTest :platform-a11y:testDebugUnitTest :app:assembleRelease --console=plain
-> BUILD SUCCESSFUL in 8m 35s
-> 468 actionable tasks: 321 executed, 147 up-to-date
-> test results (build/test-results XMLs): 106 tests, 0 failures, 0 errors
-> release APK produced: app/build/outputs/apk/release/app-release-unsigned.apk (50,847,269 bytes)
```

Earlier verification rounds on the same branch (both red before the fixes they drove):
`:app:compileDebugKotlin` initially failed with `Unresolved reference 'PlanStatus'`
(fixed: the loop's plan status type lives in `ai.eqo.data.models`), and
`:app:processDebugMainManifest` failed with `Namespace 'ai.eqo' is used in multiple
modules ... :platform-a11y` — fixed by giving `:platform-a11y` the unique namespace
`ai.eqo.platform` (one `import ai.eqo.platform.R` change in `EQOAccessibilityService.kt`;
no component name changes).

Toolchain as the lead verifies it: JDK 21 (`jdk-21.0.12.101-hotspot`), Gradle 9.7.0,
AGP 9.3.1, Kotlin 2.4.0, minSdk 30, Conscrypt 2.7.0 (pins in `gradle/libs.versions.toml`).

Reproducibility notes for the release APK:

- `:app` release build is a plain `isMinifyEnabled = false` study build (`versionName 0.1.0`,
  `versionCode 1`); the build is reproducible in the sense that the same commit builds the
  same APK from the pinned toolchain above, without network-sourced code (all deps pinned
  in `gradle/libs.versions.toml`).
- `scripts/check.sh` (secret scan + shell syntax + branding gate) — **exit code 0**:
  `OK: user-visible code carries no upstream product names in any case`,
  `OK: prompt/notification bodies clean`, `OK: resources/manifests clean`,
  `OK: no Leap SDK references`, `kt files: 308; provenance rows: 308`,
  `OK: provenance map covers all Kotlin files and every row path exists in git ls-files`,
  `BRANDING GATE PASSED` / `OK`. The 20 TASK-015 Kotlin files were added to
  `task-005-provenance-map.md` as EQO-NEW rows (the gate enforces exact coverage).
- No secrets in logs: nothing in the new code logs key material — the model key goes
  straight from the EditText into `AndroidProviderCredentialStore` (Keystore-backed AES-GCM)
  and the provider probe runs through `ConnectionTestRunner`, which registers the candidate
  key with `SecretRegistry` for the duration of the probe and never renders it. The
  branding scan covers user-visible strings; the only upstream names in resources are in
  the `notices_body` attribution block (the documented NOTICE context).

### 4.1 TASK-015 lint follow-up (2026-10-04; Refs #20)

The stopped author's WIP was reviewed against `011cfa5` and retained as a coherent
static-analysis cleanup in `a820738`: `RecoveryCatalog.kt` renamed to `FailureClass.kt`
with matching provenance/evidence paths; Kotlin wrapping/import ordering; the API-33
check named with `VERSION_CODES.TIRAMISU`; named countdown/padding constants;
extraction of the nested transport-guard scan without changing its checks; and local,
explained Detekt exemptions for deliberate typed-denial returns, severity ranks,
controller/screen cohesion and safe probe exception-to-state mapping. No unrelated
behavior changes were found or carried forward.

`14b33ce` fixes the reproduced app report's **17 errors**: **1** `PluralsCandidate`,
**13** `UnusedResources`, **2** `UseKtx`, **1** `SetTextI18n`. The approval countdown now
uses `<plurals>` (`one` / `other`) and `getQuantityString` at both initial and tick
render sites. Unreferenced strings were removed, consent persistence uses KTX `edit`
(the same asynchronous `apply` behavior), the SMS URI uses `toUri`, and build info is
one formatted resource. No Android lint baseline, configuration relaxation or new
Android lint suppression was added. The complete gate also found and drove fixes for
KDoc/comment order, expression wrapping and two Detekt maximum-line-length findings.

Safety comparison against reviewed `011cfa5`: `StudyFlowGate` is unchanged and both
transport planes remain false; the real permission/approval policies and controller
wiring have only formatting/comments/Detekt annotations changed. The sole resume
mint remains in the explicit positive-button gesture handler. Approval expiry,
timeout-to-rejection and compose-only dispatch behavior are unchanged. The existing
study-flow, permission/approval and user-only-resume guard tests pass in the full suite.

Final serialized command, run from `android/`:

```
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m
-> BUILD SUCCESSFUL in 6m 55s
-> 897 actionable tasks: 97 executed, 800 up-to-date
../scripts/check-branding.sh
-> BRANDING GATE PASSED; exit 0
../scripts/check.sh
-> secret scan, shell syntax, branding/provenance checks passed; OK; exit 0
```

The scripts live at repository root, so `../scripts/` is the correct path from
`android/`. Provenance: **308 tracked Kotlin files / 308 rows**. XML results across
all modules: **596 tests, 0 failures, 0 errors, 1 skipped** (94 suite XMLs).
Per module: adb-pairing 82, app 46, browser-cdp 35, core-agent 62, core-llm 261
(including the skipped test), core-security 47, helper-server 3, platform-a11y 60.

Observed toolchain: Temurin **21.0.12.1+1-LTS** (installed directory
`jdk-21.0.12.101-hotspot`), Gradle **9.7.0** (`./gradlew --version`), AGP **9.3.1**,
Kotlin **2.4.0**, Conscrypt **2.7.0** (version catalog); helper-server NDK
**29.0.14206865 / r29** (build pin and installed `source.properties`). Debug APK:
`app/build/outputs/apk/debug/app-debug.apk`, **58,522,146 bytes**; unsigned release:
`app/build/outputs/apk/release/app-release-unsigned.apk`, **50,842,441 bytes**.

Earlier follow-up runs were red: initial app lint reproduced the 17 errors; the first
full gate reached ktlint and failed on two style findings; the next run hit a
transient `packageRelease` incremental packaging failure (no cause text reported),
which cleared on the next serialized retry without build configuration changes;
that retry exposed the two Detekt line-length findings. The final full gate above
completed successfully. The terminal wait expired shortly before its completion;
the final log was read back and the wrapper was confirmed no longer running before
any further Gradle invocation. Existing deprecation/experimental-coroutines warnings
remain; they are not new lint errors. No phone/device verification was attempted.

### 4.2 TASK-015 security follow-up: SF-1 / SF-2 (2026-10-04; Refs #20)

SF-1: the RESUME view and the approval/resume-confirmation dialogs now set
`filterTouchesWhenObscured = true`. A small parent dispatch guard surrounds the
RESUME button and each dialog's entire content card, before child click dispatch.
It rejects both `FLAG_WINDOW_IS_OBSCURED` and `FLAG_WINDOW_IS_PARTIALLY_OBSCURED`
(on minSdk 30, without `setHideOverlayWindows`). An obscured DOWN, MOVE, pointer
change or UP poisons that gesture until a new clean DOWN. A clean synthetic CANCEL
is dispatched to clear any already-pressed child; simply dropping an obscured
MOVE/UP would risk a later clean UP completing the click. Consequently the existing
positive-button handlers cannot approve or mint a resume token from that touch.
Clean touch, keyboard/accessibility click, cancellation, countdown, expiry and
controller behavior are retained. The original button/content views, listeners,
IDs and outer layout parameters are retained within the dispatch wrapper.

`ConfirmationTouchGuardTest`: **7 passing JVM tests** pin the Android flag bits,
clean/unrelated flags, either/both obscuration bits, poisoned DOWN/MOVE/UP and
recovery on the next clean gesture. Existing `StudyLoopWiringTest` and
`TakeoverResumeUserOnlyTest` still pass unmodified; the sole mint site remains the
explicit resume-positive-button handler in `TaskActivity`. No on-device overlay
or layout verification is claimed (owner away); those checks remain device work.

SF-2: `app/src/main/AndroidManifest.xml` now explicitly declares only
`android.permission.INTERNET`; no other permission declaration changed.
Before snapshot was captured from the pre-change build at `46d8601`; after snapshot
was captured after the full build below. Source files:
`app/build/intermediates/merged_manifests/{debug,release}/process{Debug,Release}Manifest/AndroidManifest.xml`.
The sorted permission lists below are identical in **both debug and release**;
XML attribute dictionaries were also compared programmatically (identical, ignoring
list order). INTERNET moved earlier in merge order, but the permission set is unchanged.

| Merged permission | Before (debug/release) | After (debug/release) |
|---|---|---|
| `ai.eqo.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | present / present | present / present |
| `ai.eqo.app.helper.permission.MANAGER` | present / present | present / present |
| `android.permission.ACCESS_COARSE_LOCATION` | present / present | present / present |
| `android.permission.ACCESS_FINE_LOCATION` | present / present | present / present |
| `android.permission.ACCESS_NETWORK_STATE` | present / present | present / present |
| `android.permission.FOREGROUND_SERVICE` | present / present | present / present |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | present / present | present / present |
| `android.permission.INTERNET` | present / present | present / present |
| `android.permission.READ_PHONE_STATE` | present / present | present / present |
| `android.permission.RECEIVE_BOOT_COMPLETED` | present / present | present / present |
| `android.permission.WAKE_LOCK` | present / present | present / present |
| `com.google.android.apps.aicore.service.BIND_SERVICE` | present / present | present / present |

Real serialized verification from `android/`, same JDK/SDK as §4:

```
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m
-> BUILD SUCCESSFUL in 3m 54s; exit 0
-> 897 actionable tasks: 66 executed, 831 up-to-date
../scripts/check-branding.sh
-> BRANDING GATE PASSED; 311 tracked Kotlin files / 311 provenance rows; exit 0
../scripts/check.sh
-> secret scan, shell syntax, branding/provenance checks passed; OK; exit 0
```

XML aggregate: **603 tests, 0 failures, 0 errors, 1 skipped**, across **95 suites**.
Per module: adb-pairing 82, app 53, browser-cdp 35, core-agent 62, core-llm 261,
core-security 47, helper-server 3, platform-a11y 60. APKs rebuilt:
`app/build/outputs/apk/debug/app-debug.apk` (**58,522,146 bytes**) and
`app/build/outputs/apk/release/app-release-unsigned.apk` (**50,845,029 bytes**).
No failing gate retries, new lint suppression, dependency or toolchain changes.
Existing Gradle deprecation notices remain. No phone/device steps were run.
Both APK DEX payloads were inspected and contain the new touch-guard classes.
The full gate reused the up-to-date platform-a11y test result, so its source-scanning
resume guard was additionally forced to execute after the edit:

```
./gradlew :platform-a11y:testDebugUnitTest --rerun --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m
-> BUILD SUCCESSFUL in 8s; 59 actionable tasks: 1 executed, 58 up-to-date; exit 0
-> TakeoverResumeUserOnlyTest: 4 tests, 0 failures/errors/skips
```

## 5. Device evidence and test plan — PENDING owner presence

The owner is away and cannot touch the phone (lead note). **No device step was run.** The
two device-side evidence items of the task ("install log", "short recording of a
first-run walkthrough") and the device acceptance criteria are **PENDING owner presence**.
Exact commands for whoever runs them (USB device, Android 12 or 13):

```
# 0. toolchain
export JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
export ANDROID_HOME='C:\Users\<user>\Android\Sdk'

# 1. reproducible release build + install log (acceptance criterion 1)
cd /c/Users/<user>/Claude/worktrees/task-015/android
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk 2>&1 | tee task-015-install.log
adb shell pm list packages | grep ai.eqo.app

# 2. first-run walkthrough recording (evidence requirement)
adb shell screenrecord /sdcard/task-015-first-run.mp4 &
adb shell am start -n ai.eqo.app/.MainActivity
#   walk the app ONLY: Set up EQO -> model key -> accessibility -> wireless debugging
#   -> browser consent -> task screen -> legal notices. Stop the recording, then:
adb pull /sdcard/task-015-first-run.mp4

# 3. per-capability readiness (acceptance criterion 3)
#   verify on screen: every row shows its OWN state; kill the helper / toggle
#   accessibility off mid-run and confirm only the affected row (and its dependents)
#   changes. Nothing may flip a row it does not own.

# 4. approvals, pause, stop, takeover (REQ-TASK-02..05)
#   run the sample task; the compose step must show the approval card with a countdown;
#   let it expire once (step must cancel, not run), approve once (composer opens, nothing
#   sent), pause mid-run (pauses after the current step), take over (pauses visibly),
#   stop (run ends, receipts shown), resume (dialog -> explicit confirmation required).

# 5. recovery classes (REQ-REC-01..10) — fault injection per PRD §7.10:
#   revoke the key (401), throttle (429), cut Wi-Fi (network), disable accessibility
#   mid-task (A11yLost), kill the helper (BinderDead), reboot (AdbAfterReboot).
#   Each must show its named state + repair text and stay paused until an explicit resume.

# 6. no secrets in logs (acceptance criterion 4)
adb logcat -d | tee task-015-logcat.txt
#   grep for the entered key material in task-015-logcat.txt -> must be 0 hits

# 7. Play Protect / restricted settings are real constraints (task Notes):
#   record what Android shows on the sideload install and on the accessibility grant
#   (Android 13 "restricted settings"); do NOT attempt to bypass either.
```

Device acceptance criteria status: all four **PENDING owner presence** (criteria 1 and 2
additionally gated on the §3 pinning work for the wireless-ADB/browser rows). Host-side
criterion 4 (branding scan clean) is green in §4.

## 6. Known gaps for the reviewer

1. The wireless-ADB screen's five check rows report "did not run" in this build (§3). The
   `ActivationSequence` dispatch point is named in `WirelessAdbSetupActivity` for when the
   gate flips; it is intentionally not reachable today.
2. Helper start/authorize/binder checks are wired to the readiness probe
   (`HelperActivationState`, TASK-007) and shown per-check on the wireless-ADB screen, but
   the live `HelperHooks` dispatch is device work (§5 step 4/5).
3. Model-key validation probes one user-entered model id via `ConnectionTestRunner`
   (live 200/401/429/credit/network classification). The "model list loaded" half of
   REQ-BYOK-01 is not in this change.
4. The sample plan on the task screen is a fixed 3-step demo (observe, scroll,
   compose-only SMS draft). User-submitted plans are the next task's scope.
