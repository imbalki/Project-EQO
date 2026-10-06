# TASK-015 evidence — study APK and guided onboarding (S6)

Issue #20. Branch `agent/android/20-study-apk`, worktree `C:\Users\<user>\Claude\worktrees\task-015`.
Base: `origin/main` = `5dc0a5d`.

## TASK-068 — real requests and one whole-plan approval (Refs #20)

Owner decision (2026-10-04, lead 21:12): approve the full preview once, not one dialog per step.
This supersedes the fixed-sample/per-step descriptions below for the new task screen.

- Type a request in “What should EQO do?”, tap Plan, then Approve or Reject the plain-language
  preview. No action runs before Approve. The stored Keystore OpenRouter key and the model
  from the successful connection test are reused, including after restart. Existing users
  must re-check/save the model once because the old screen did not persist its model choice.
- Reused OpenRouterProvider and WrappedLLMProvider provide typed errors, bounded retries and
  secret registration/redaction; the client has no logging interceptors or redirects. Only
  https://openrouter.ai is contacted. No request/response body or raw exception is logged.
  The request is sent as quoted JSON data. Screen text remains untrusted/fenced; this build
  sends no screen content to the planner (empty fence), preventing capture of EQO's key UI.
  Observations during the run stay local. No replan, donor AgentLoop or Python bridge runs.
- Closed PlanValidator study schema: observe, scroll, open_app, tap_text, type_text, paste,
  press_back/home/enter, send_whatsapp, send_telegram, compose_sms, compose_email. Unknown
  verbs/params, safety flags, macros, payment/call/direct-SMS-send and malformed JSON fail closed.
- SensitivityApprovalPolicy is unchanged and still evaluates every action. Its sensitive-step
  gate consumes the explicit whole-plan approval only when the full step (ID, verb, parameters,
  execution flags) equals the approved snapshot. The executor checks membership again, including
  nominally non-sensitive steps. Changed or injected steps cannot inherit approval.
- Paste uses accessibility text insertion, not a clipboard export. App names resolve through
  AliasResolver package hints or a unique exact launcher label; ambiguous/missing apps fail.
  WhatsApp/Telegram reuse takeover-gated donor automators in the open chat; typing must succeed
  and the expected package must remain foreground before Send. No contacts lookup. Receipt means
  Send was pressed, NOT proof of delivery. UI automation remains app/version/language dependent.
- SMS uses ACTION_SENDTO smsto:, email ACTION_SENDTO mailto: with URI-encoded subject/body;
  Android resolves only corresponding scheme handlers, not a generic ACTION_SEND/share chooser.
  EQO itself never sends SMS/email. Missing handlers fail. No extra permissions were introduced.
- Taps, typing/paste, Enter and chat sends are marked irreversible for no automatic retry.
  Pause/Stop/Take over/user-gesture Resume, obscured-touch protection and takeover latch remain.
  Closing/recreating the task screen stops the task; it does not restore approval or auto-resume.

### Owner phone steps — NOT RUN by this card

1. Install the debug APK from this branch only when the lead schedules a phone session.
   Release APK is unsigned and is not claimed installable. Open EQO → Set up EQO → model key.
2. Owner enters the OpenRouter key and model; tap Check and save key. Enable EQO accessibility
   yourself in Android Settings for screen automation. Neither contacts nor SMS permissions needed.
3. Open the task screen. Type `send a text to <number> saying hello` using an actual test number.
   Tap Plan. Verify the number/body and that the preview says draft/you send it. Reject first:
   no messaging app opens. Plan again and Approve: a draft opens, no message is sent by EQO.
4. Return to EQO. Type `draft an email to <address> with subject EQO test and body hello`,
   substituting a test address. Plan → verify full address, subject and body → Approve.
   A mail draft opens; the owner chooses whether to send or discard it. Missing mail app must fail.
5. Try blank recipient: `draft an email with subject EQO test and body hello` → fill the address
   in the mail app. Contact names alone are not resolved; type the number/address yourself.
6. For WhatsApp/Telegram use only a test chat. Plan `open WhatsApp, tap <test chat label>, send hello`.
   Review every step: these plans really press Send. Pause/Take over/Stop and explicit Resume
   must retain the same approved steps. If the UI differs or a step fails, verify manually;
   do not rerun a send without checking the chat first.
7. Turn off internet and tap Plan: readable connection failure, no steps run. Remove/reject the
   key: readable setup/auth guidance. Invalid model output: supported-plan failure, no steps run.
   Check logcat for the key/request; this card did not use a live key or perform phone work.

Honest limits: fixed approved sequential plan; no contact access, adaptive replanning, voice,
research/web-search answers, macros or arbitrary donor actions; no delivery verification;
wireless-ADB/CDP study gates remain off. The old 60s per-step approval tests remain for legacy
callers, but this task screen uses a protected whole-plan dialog (cancel/back does not approve).

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

## TASK-062: production accessibility startup repair (Refs #20)

The owner's actual `ai.eqo.app` failed in `Hilt_EQOAccessibilityService.onCreate`: the default Application did not implement Hilt. A registration-only fix exposed 28 missing donor bindings; the lead explicitly approved the safety-preserving explicit runtime instead. `EqoApplication` is now a plain registered Application, not `@HiltAndroidApp`; the service is a plain AccessibilityService. No fake production bindings were introduced.

| Former service injection | Consumer / Phase-One need | Disposition |
|---|---|---|
| `AgentLoop` (donor) | Floating-widget state collector; not the study `ActionLoop` | Remove collector and entire donor widget. No donor loop constructed or started. |
| `SettingsRepository` | Floating-button visibility preference; not study wiring | Remove observer with widget. No automatic work on service bind. |
| `AccessibilityNodeTraversal` | Raw gated find/click, submit fallback and password-filtered screen text; needed | Direct dependency-free constructor retained. No Hilt required. |
| `Lazy<HabitRoutineTracker>` | Window-change app-open recording; not study wiring | Remove recording path. No habit routines reachable from this service. |
| `ServiceBridge` | Widget long-press recording; not study wiring | Remove widget and bridge trigger. Python/recording is not reachable (D-005). |
| `NotificationTapTarget` | Widget click navigation; not study wiring | Remove widget navigation path; study activities keep existing UI navigation. |

`EqoServiceRuntime` supplies the service automation and the existing `TakeoverDetector.shared` latch. The app already obtains exactly this automation via `getInstance().automation`; no parallel or test graph exists. Runtime callbacks explicitly supply bound-service and secure-window state. The existing gated-actions facade, touch/self-gesture attribution, password filter, secure-window filter and untrusted-text fencing remain intact. No automatic resume, permission/approval defaults or transport-gate changes were made.

`EqoServiceRuntimeTest` constructs the holder without Android/Hilt, checks unbound typed refusal and the shared study latch, and source-scans the service/app to reject unsupported injected seams. `RealAccessibilityServiceSmokeTest` checks the real target package/Application and OS-bound component; owner must enable the service in Android Settings first. Starting/binding the protected component programmatically is not a legitimate substitute. The instrumentation test was not run; no phone use by this worker.

Source audit across all modules (`@AndroidEntryPoint`, `@HiltViewModel`, `EntryPoint`, `@InstallIn`, Application assumptions, providers and WorkManager):

- No production `@AndroidEntryPoint` or `@HiltViewModel` remains. `:app` has no `@Inject` entries. `:platform-a11y` retains four constructor annotations: `AccessibilityNodeTraversal` (dependency-free, directly constructed by the service), `AndroidCallFlowVerifier` (dependency-free donor call helper, not the compose-only study path), donor `AgentLoop` (classifier/factory/plan/action/store/settings/lazy re-evaluation dependencies; deliberately not constructed by Phase One), and `VisionEngine` (`LLMProviderFactory`; donor vision path not constructed by the study app). An annotation is not proof that a production graph exists: the donor graph is not enabled.
- `:core-llm/ModelDownloadWorker.WorkerEntryPoint` remains `@EntryPoint` / `@InstallIn(SingletonComponent::class)` and calls `EntryPointAccessors.fromApplication` in its constructor. It needs `ModelDao`, `OkHttpClient`, `ProviderCredentialStore`, `NotificationTapTarget`. This is an unsupported dormant legacy path, NOT repaired by this task. `ModelDownloadWorkRequest.create` has only test callers; the study app has no worker/request/WorkManager reference. Local Gemma is parked (D-009). Do not expose/enqueue the worker before explicitly supplying its dependencies. It is not an app-startup initializer.
- The other `@InstallIn` is `EqoTestBindings` in platform androidTest, never copied into production. The instrumentation test harness still has its own Hilt Application; it no longer injects the real accessibility service.
- Merged debug providers are `androidx.core.content.FileProvider`, `moe.shizuku.manager.ShizukuManagerProvider`, `com.google.mlkit.common.internal.MlKitInitProvider`, and `androidx.startup.InitializationProvider`. The custom helper provider has no Hilt access. AndroidX Startup initializes WorkManager, EmojiCompat, ProcessLifecycle, OkHttp Platform, ProfileInstaller with library defaults; there is no app `Configuration.Provider` or `HiltWorkerFactory`. None supplies the missing donor worker bindings or starts that worker.

The audit also found the study port's inherited raw `performGlobalAction` for back/home. These now use `service.gatedActions.pressBack/pressHome`, and the new source guard rejects reintroducing that takeover bypass. Unknown global verbs return false. Existing action-gate tests exercise typed takeover rejection for the facade.

`ProductionServiceStartupTest` additionally runs the actual service `onCreate` under the plain production `EqoApplication` using Robolectric, not a Hilt test application. The source guard checks the declared app manifest and, when present, the actual merged debug manifest.

### TASK-062 host verification (no device claim)

Toolchain observed with `java -version`, `./gradlew --version`, version catalog and actual `:app:dependencyInsight --configuration debugRuntimeClasspath --dependency hilt-android`: Temurin JDK **21.0.12.1+1-LTS**, Gradle **9.7.0**, AGP attribute **9.3.1**, Kotlin **2.4.0**, resolved Hilt **2.60.1** (transitive module dependency; no app Hilt plugin/root). App Hilt/KSP plugins and app Hilt compiler were removed as part of replacing the donor graph, not replaced with fake bindings.

Exact serialized requested gate from `android/`:
```
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m
-> BUILD SUCCESSFUL in 17s; 897 actionable tasks: 26 executed, 871 up-to-date; exit 0
../scripts/check-branding.sh
-> BRANDING GATE PASSED; 316 tracked Kotlin files / 316 provenance rows; exit 0
../scripts/check.sh
-> secret scan, shell syntax and branding/provenance passed; OK; exit 0
```

XML aggregate: **608 tests, 0 failures, 0 errors, 1 existing skipped**, **97 suites**. New regression tests: `ProductionServiceStartupTest` **1**, `EqoServiceRuntimeTest` **4**, all passing. Existing `StudyLoopWiringTest` **11**, `GatedActionsGateTest` **4**, `TakeoverResumeUserOnlyTest` **4** pass. Per-module totals: adb-pairing 82, app 54, browser-cdp 35, core-agent 62, core-llm 261 (1 skipped), core-security 47, helper-server 3, platform-a11y 64.

Merged debug manifest (`app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`) shows:
```xml
<application android:name="ai.eqo.EqoApplication" ...>
    <service android:name="ai.eqo.accessibility.EQOAccessibilityService"
        android:description="@string/eqo_accessibility_service_description"
        android:exported="false"
        android:label="@string/eqo_accessibility_service_label"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService" />
        </intent-filter>
        <meta-data android:name="android.accessibilityservice"
            android:resource="@xml/accessibility_service_config" />
    </service>
</application>
```
The entire merged service declaration was compared recursively (all attributes and children) with the original declared service: identical. `javap` of built debug/release classes, including ASM-transformed/runtime classes, confirms direct `extends android.accessibilityservice.AccessibilityService`, not `Hilt_EQOAccessibilityService`. Secure-window detection, touch attribution and the entire node/gesture/screenshot methods block were compared against `origin/main`: unchanged.

Earlier verification attempts are not hidden: the registration-only commit failed Hilt compilation; the plain-runtime first build needed its retained Context import; the subsequent complete build/test/lint run failed on one extra blank line at ktlint (after 41m05s on the resource-constrained host); scoped Detekt found three overlong new test-source lines. All corrected without new suppressions or relaxed checks. One compile-only retry observed the in-progress instrumentation edit before its constants were present; the final app instrumentation compilation passed.

Extra verification compiled, but did NOT run, the new `:app:compileDebugAndroidTestKotlin`: passed. Extra existing `:platform-a11y:compileDebugAndroidTestKotlin` failed on eight unresolved `R` references in `EqoTestTargetActivity.kt`; no platform androidTest code was changed by this task. This pre-existing harness namespace regression is triage follow-up **t_e2fcbc7c**; the exact requested main gate above passes independently. Dormant worker dependency triage is **t_e6c24cd3**. No phone use, instrumentation execution or successful real-device startup claim.

Built artifacts: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release-unsigned.apk`. The lead should install the debug artifact; release is unsigned and is not claimed installable. Full logs are retained with the task handoff.

Required device step: enable accessibility on the REAL `ai.eqo.app` and confirm the service stays bound (`adb shell dumpsys accessibility`) and no application `FATAL EXCEPTION` appears in captured logcat. Repeat after toggling off/on. This must precede claiming the study APK works; `ai.eqo.test` Hilt success is not production-app evidence.

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
3. Original TASK-015 did not implement the model list. TASK-070 now supplies the live
   searchable list and manual fallback; see the addendum below. Device walkthrough is pending.
4. The sample plan on the task screen is a fixed 3-step demo (observe, scroll,
   compose-only SMS draft). User-submitted plans are the next task's scope.

## TASK-070: live OpenRouter model picker (Refs #20)

Branch `agent/android/70-model-picker`, based on current `origin/main` `b5fe24c`
(not the unmerged typed-request branch). The setup screen's only production free-text
model field is replaced by a selected-ID display and searchable list. Manual entry
is an explicit fallback available while loading, offline and on error.

The `:core-llm` catalog transport sends an unauthenticated GET only to
`https://openrouter.ai/api/v1/models`, with a dedicated client, no interceptors,
no credentials and no redirects. It bounds the response to 4 MB and the parsed list
to 5,000 rows, ignores unknown fields, skips malformed/duplicate IDs and tolerates
missing pricing/context. No response bodies or model data are logged. The public
endpoint returned 466 rows during fixture capture; tests use two distinct actual
paid/free response rows from `src/test/resources/openrouter-models.json`, not live HTTP.
No dependency or toolchain changes.

Rows show name, ID, provider (ID namespace), context tokens and separate input/output
prices in USD per **million** tokens. Decimal multiplication avoids floating-point
rounding; display uses six fractional places, HALF_UP, removes trailing zeros, says
`free` for zero and `n/a` for absent/invalid/negative prices. Tiny positive prices say
`<$0.000001` rather than falsely saying free. Last validated choice sorts first,
then the short recommended list when present, then names; filtering is case-insensitive
and matches all search terms against name/ID/provider.

The app-private `openrouter_model_catalog` preferences retain JSON, fetched timestamp
and last automatic attempt. Refresh attempts are at most daily (including failed
attempts), or on demand. Offline/error retains the last list and its timestamp;
no-list errors offer refresh/manual entry. Key storage is unchanged. Only a successful
connection test and successful credential write commit the model to the exact
`study_model_choice` / `openrouter_model` preference seam read by the typed-request
branch. Browsing/manual entry alone does not alter the planner's validated model.
That branch is untouched and not merged here; its save must be reconciled to one
successful-validation save when integrating both branches.

JVM tests cover fixture parsing, malformed/missing/duplicate/oversized data, decimal
price conversion/rounding, sorting/filtering, daily cache/forced refresh/offline and
storage failures, and fake HTTP checking the exact GET URL, no Authorization and
redirect rejection. Robolectric tests cover private cache persistence, the exact
planner preference seam, displayed costs/filter/selected ID, manual entry and
empty-offline guidance. No test uses public-network HTTP.

Owner-visible walkthrough is queued in `docs/agents/OWNER-RETURN-CHECKLIST.md` §1b.2;
no phone or PR was used.

### TASK-070 host verification

Observed toolchain: Temurin JDK **21.0.12.1+1-LTS** (JDK 21), Gradle **9.7.0**,
AGP **9.3.1**, Kotlin **2.4.0**. No version changes. New test result XMLs confirm
`OpenRouterModelCatalogTest`: **9 tests, 0 failures/errors** and `ModelPickerTest`:
**4 tests, 0 failures/errors**. Static `detekt` plus scoped `ktlintFormat` passed
(`BUILD SUCCESSFUL in 33s`). Both repository scripts passed with exit 0.

Exact full gate (serialized; no overlapping Gradle from this worker):

```
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m
../scripts/check-branding.sh
../scripts/check.sh
```

Full-gate final status: **NOT COMPLETE within the two-hour cap**. The current retry
reached debug lint. Aggregated unit-test XMLs: **652 tests, 0 failures, 0 errors,
1 existing skipped**, across **108 suites**. This is not a full-gate success claim.
Debug and unsigned release artifacts are under `android/app/build/outputs/apk/`.
The lead must collect the active gate result or rerun it serially before acceptance.

Earlier red runs: the initial fixture accidentally contained the same free model
twice; deduplication correctly produced one row and failed three count assertions.
The fixture was replaced by distinct paid/free actual response rows. One Robolectric
manual-dialog assertion needed a main-looper drain to deliver Android's queued
positive-button callback; assertion retained. Static checks drove helper extraction
and line wrapping without suppressions/baselines. A later retry compiled both APKs
but the app test executor exited with Gradle IPC `Connection reset by peer` (no
assertion failure); the serialized retry used unchanged code. Kotlin daemon connection
failures used Gradle's compile-without-daemon fallback. No shared daemon/build was stopped.


