# TASK-073 — phone and system executor port (Refs #20)

Locally verified executor port; no device-verification claim. Historical checkpoints
below are superseded by the final continuation results at the end of this document.
Branch agent/android/73-port-actions-2 starts at origin/agent/android/69-port-actions-1
78c2207 (remote advanced from the card's bd3f5b6). New commits remain separate.
Donor HEAD checked: 6ff5a061755b597b0558fed1f565587837ed4d51.
Toolchain pins: JDK 21, Gradle 9.7.0, AGP 9.3.1, Kotlin 2.4.0.
Live JDK checked: Temurin 21.0.12.1+1-LTS.

## Per-action Android 11 behavior and access

| Action | Actual behavior | Just-in-time access |
|---|---|---|
| OPEN_APP | Visible launcher/installed-app label/package substring lookup, launch, donor 2s transition wait; not proof app is fully loaded | None; narrow launcher package visibility |
| CLOSE_APP | Home global action, NOT force-stop; donor is AdvancedControlActions.kt 376–382 | Accessibility/takeover gate |
| OPEN_BROWSER | Chrome launch or donor HTTPS Google fallback | Accessibility/takeover gate |
| OPEN_URL | Donor HTTPS normalization and VIEW; valid HTTP(S) host, no URI userinfo; no URL echoed in result | Accessibility/takeover gate |
| TAKE_SCREENSHOT | Android global screenshot action; no screenshot uploaded | Accessibility/takeover gate |
| RECORD_SCREEN | Explicit failure; donor 734–740 only returned simulated start/stop | None |
| ANALYZE_SCREENSHOT | Secure-window-refused/password-filtered gated text observation to explicitly supplied analyzer; donor VisionEngine text fallback, no image capture | Caller must supply approved provider; default unavailable, no transmission |
| COPY_TO_CLIPBOARD | Direct plain-text clip with EQO label, no copied-content echo | None |
| GET_CLIPBOARD | Direct first clip item text; Android foreground restrictions still apply; registry fences successful output as untrusted | None |
| CLEAR_CLIPBOARD | Direct clearPrimaryClip on Android 11 | None |
| TOGGLE_FLASHLIGHT | Direct camera torch API, donor callback/state tracking and flash-camera lookup | CAMERA runtime immediately before camera access; actual grant rechecked |
| TOGGLE_WIFI | Donor WIFI panel, full WIFI settings fallback; manual change, never claims state changed | Accessibility/takeover gate; no radio permission |
| TOGGLE_BLUETOOTH | Android 11 enable-confirm prompt for explicit on, otherwise Bluetooth settings; manual completion | Accessibility/takeover gate; legacy normal BLUETOOTH permission capped at API 30; no direct adapter control or CONNECT request |
| TOGGLE_MOBILE_DATA | Donor INTERNET_CONNECTIVITY panel, DATA_ROAMING_SETTINGS fallback; manual change | Accessibility/takeover gate; no privileged telephony access |
| TOGGLE_HOTSPOT | Donor WIFI panel, TETHER_SETTINGS fallback; panel may not expose hotspot on OEM, user must navigate; no changed-state success | Accessibility/takeover gate; no tethering privilege |
| TOGGLE_DND | Direct interruption filter after policy grant | ACCESS_NOTIFICATION_POLICY declaration; requester special access and actual grant recheck |
| SET_VOLUME | Donor stream aliases, clamp 0–100 and max-volume scaling; direct audio API | MODIFY_AUDIO_SETTINGS normal; DND policy access before ring/notification/system streams |
| SET_RINGER_MODE | Donor silent/mute/vibrate/vibration/normal aliases, direct audio API | MODIFY_AUDIO_SETTINGS; DND special access/recheck |
| SET_BRIGHTNESS | Donor 0–100 scaling, manual mode and brightness via Settings.System only | WRITE_SETTINGS special access, package-scoped screen/recheck; NEVER Settings.Secure |
| SET_WALLPAPER | Donor wallpaper picker only, user chooses/applies | Accessibility/takeover gate; no wallpaper-write grant |
| LOCK_SCREEN | Donor Android global lock action | Accessibility/takeover gate |
| RESTART_DEVICE | Donor power dialog only, user chooses Restart; no reboot API | Existing caller plan-level approval + accessibility/takeover gate |
| INSTALL_APP | Donor market search with encoded appName, web Google Play fallback; no APK installation | Existing caller plan-level approval + accessibility/takeover gate; no REQUEST_INSTALL_PACKAGES |
| ENABLE_PRIVATE_MODE | Donor Chrome extras or default browser fallback; always asks user to verify private mode, extras not proof | Accessibility/takeover gate |
| CLEAR_BROWSER_DATA | Donor Chrome clearBrowserData URI, Chrome app-info, manage-applications fallback; user selects/clears; no deletion performed | Existing caller plan-level approval + accessibility/takeover gate |
| GET_SYSTEM_INFO | Donor OS release and model only, registry untrusted fence | None |

## Port decisions and deviations

SystemActions.kt retains selected donor class bodies/algorithms with explicit construction,
private executor classes, registry permit checks, cancellation propagation, and gated intent
launch. No Hilt/Room, shell/termux/root, helper secure-settings write, broad package query,
or new approval mechanism. Registry is the part-1 foundation; only family composition,
analyzer dependency and untrusted-output list are extended. ActionSchema already contains
these names; the registry constructor validates them. Runtime/special access is not approval.
Existing caller plan-level approval remains mandatory for high-impact actions; registry
never interprets a permission grant as consent to reboot/install/delete.

Safety deviations: donor Wi-Fi setter falsely reports success on Android 11, telephony/
hotspot reflection is privileged, Bluetooth adapter writes are deliberately omitted under
this task's panel-only owner requirement. Their donor fallback shapes remain, with honest
UserActionRequired outcomes. Donor RECORD_SCREEN simulation is rejected, not reproduced as
fake success. Pending wallpaper/store/private-mode/clear-data operations likewise report
manual completion, not effects. Clipboard copy does not echo secrets; read is fenced.

ANALYZE_SCREENSHOT deliberately does NOT call the existing donor-derived VisionEngine:
its raw screenshot path is outside the takeover facade and can include password pixels.
TextScreenAnalyzer is a concrete explicit-provider implementation of its text fallback,
using the same fenced prompt, temperature 0.3, 500-token TEXT response and a 30s deadline.
Registry accepts an optional ScreenAnalyzer; default fails closed without a provider.
App wiring for an approved provider and safe image capture is NOT implemented here.
This is a documented reduced-capability deviation, not full image-analysis parity.

No phone, PR, merge or device operation. NOT device-verified: Android/OEM intent resolution,
radio/settings panels, Bluetooth confirm prompt, torch/callback timing, actual audio/brightness
changes, DND grants/lifecycle, screenshots/lock/power dialog, package visibility/app transitions,
clipboard foreground policy, Chrome incognito-extra handling, store search and data settings.
No real LLM transmission or billing is used in local tests.

## Verification

- scripts/check-branding.sh: PASS, exit 0; tracked Kotlin/provenance rows both 352.
- scripts/check.sh: PASS, exit 0.
- Initial targeted compile took 34m56s, failed on UserActionRequired constructor arity and generated underscore catches; both corrected. Subsequent targeted invocation compiled production and tests and executed 32 tests (28 pass, 4 fail).
- The four failures were three tests assuming Robolectric special-access defaults were denied (the shadows defaulted granted), and the panel-family takeover test reaching an exception rather than a typed result. Tests now explicitly set denied special-access state, and the panel family returns the typed gate refusal before fallback; re-verification pending.
- TextScreenAnalyzerTest: 3/3 PASS with fake provider; no real LLM request. Part-1 registry/call tests: 17/17 PASS.
- :actions-android:ktlintFormat invoked ONCE; applied automatic formatting but exit 1 for a non-autofix long string. String split; late registry alias edit wrapping corrected. ktlintCheck still pending.
- Exact full gate started (assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt, specified workers/heaps); outcome pending. No green gate claim.

Host observed at 99% RAM load, roughly 119 MiB available; MSYS child-copy failures and
file-tool timeouts required native Python recovery for local edits. No unrelated process
was stopped. Kotlin daemon connection retries repeatedly fell back to in-process compilation.


## Final checkpoint at two-hour cap (not complete)

Exact full gate FAILED after 10m40s in :app:compileDebugKotlin: the foundation base
78c2207 already has duplicate TaskActivity.onPause (91/128) and onDestroy (96/629).
Task-073 has no diff in that file. Repair prerequisite t_6fa953b7 was created for
eqo-build-fixer; lead must provide/approve corrected foundation before rerunning.
No APK assembly/full gate success is claimed.

Latest targeted test/lint invocation also FAILED test compilation because the guessed
ShadowSettings.ShadowSystem.setCanWrite method does not exist. The installed
Robolectric 4.16.1 classes were checked using javap; the fixture now uses actual
ShadowAppOpsManager.setMode(OPSTR_WRITE_SETTINGS, uid, package, MODE_ERRORED)
and a denied NotificationManager policy-access shadow. This correction is NOT yet
retested. Last completed test suite remains 32 tests, 28 passed and 4 failed; do not
treat the corrected source as a passing rerun. Lint analysis did not finish before
the cap; no lint pass claimed.

:actions-android:detektBaseline generated explicit IDs for preserved donor complexity,
broad Android exception handling, magic values and long source/provenance lines,
plus the extended registry composition/alias mapper. This follows part-1 baseline
practice but needs review: no detekt pass is claimed without rerunning detekt.
ktlintCheck found the late AppOps fixture wrapping; corrected, not rechecked.
Donor ringer mute/vibration and volume ringtone/ringer/notif aliases now normalize
before schema validation, but registry-level alias regressions still need adding.

EXACTLY REMAINS: recompile/rerun all module tests; finish lintDebug; recheck ktlintCheck
and detekt and review baseline scope; add registry audio-alias and positive special-
access/screen-analysis gate regressions; receive corrected foundation and rerun the
full exact gate; approved provider/app wiring or owner acceptance of text-only
ANALYZE_SCREENSHOT deviation. RECORD_SCREEN intentionally remains honest unavailable
for the donor simulation. No phone/PR/merge.

## Authorized continuation after batch-1 merge (current status)

The sections above record the original blocked run, not current verification.
PR #68 verified MERGED at 5bf8ccbcbf5c953e1a859d9f566fda39c34ab872.
Only the three batch-2 commits rebased onto that merged main; no foundation copy
or local TaskActivity repair is retained. Authorized rebase push 20b44eb875497f734e03ae54827a7062ec400cdc
verified against the exact remote branch. No PR or merge performed here.

Lead accepted text-only analysis and unavailable recording. Success explicitly says
'Analysed from screen text, not the image.' Text is fenced as UNTRUSTED in the
provider request and registry response. Default app registry has no analyzer;
it honestly fails without transmitting text. Recording returns typed Failure,
'unavailable in this build', not the donor's simulation. No MediaProjection port.

Dangerous RESTART_DEVICE, INSTALL_APP, CLEAR_BROWSER_DATA and LOCK_SCREEN are
registry-only, NOT enabled by TaskPlanner/PlanValidator. DELETE/WIPE-type verbs
remain excluded too. Regression tests reject both schema and lower-case names.
No extension of the live plan allowlist; future exposure requires explicit lead
enablement and existing immutable-preview/approval checks. Permissions are not approval.

### REAL / PARTIAL / UNAVAILABLE table (all 26)

REAL means an implemented Android API/intent/global action, NOT device-tested.
PARTIAL means manual completion or reduced capability. Radio panels are PARTIAL;
direct mutation is UNAVAILABLE on Android 11+ under the owner-approved app boundaries.

| Action | Status | Capability boundary |
|---|---|---|
| OPEN_APP | REAL | Visible packages only; gated launch, not proof loaded |
| CLOSE_APP | PARTIAL | Home only, not force-stop |
| OPEN_BROWSER | REAL | Chrome/default browser gated launch |
| OPEN_URL | REAL | Gated HTTP(S) VIEW |
| TAKE_SCREENSHOT | REAL | Global screenshot request, no image transmission |
| RECORD_SCREEN | UNAVAILABLE | No MediaProjection; typed unavailable-in-this-build failure |
| ANALYZE_SCREENSHOT | PARTIAL; default UNAVAILABLE | Fenced text-only explicit-provider implementation; app has no analyzer configured |
| COPY_TO_CLIPBOARD | REAL | Gated mutation, no copied-content echo |
| GET_CLIPBOARD | REAL | Foreground policy applies; output fenced |
| CLEAR_CLIPBOARD | REAL | Gated clearPrimaryClip |
| TOGGLE_FLASHLIGHT | REAL | CAMERA request/recheck, gated torch API |
| TOGGLE_WIFI | PARTIAL; direct UNAVAILABLE | WIFI panel/settings; ordinary app setter blocked on Android 11+ |
| TOGGLE_BLUETOOTH | PARTIAL; direct UNAVAILABLE | API30 on-confirm prompt or settings; API31+ settings only; no adapter mutation per owner rule |
| TOGGLE_MOBILE_DATA | PARTIAL; direct UNAVAILABLE | INTERNET_CONNECTIVITY panel/data settings; privileged telephony path unavailable |
| TOGGLE_HOTSPOT | PARTIAL; direct UNAVAILABLE | WIFI panel/tether settings; privileged tethering path unavailable; OEM navigation may be required |
| TOGGLE_DND | REAL | Policy grant/recheck, gated interruption filter |
| SET_VOLUME | REAL | Normal audio permission; ring/notification/system policy grant, gated scaling |
| SET_RINGER_MODE | REAL | Policy grant/recheck, gated ringer mode |
| SET_BRIGHTNESS | REAL | WRITE_SETTINGS requester/recheck; gated Settings.System only, NEVER Secure |
| SET_WALLPAPER | PARTIAL | Picker; user applies |
| LOCK_SCREEN | REAL; registry-only | Gated global lock, excluded from plan allowlist |
| RESTART_DEVICE | PARTIAL; registry-only | Gated power dialog only; user reboots |
| INSTALL_APP | PARTIAL; registry-only | Store search only; user selects/installs |
| ENABLE_PRIVATE_MODE | PARTIAL | User verifies incognito; extra not proof |
| CLEAR_BROWSER_DATA | PARTIAL; registry-only | Browser/app settings only; user confirms clearing |
| GET_SYSTEM_INFO | REAL | OS release/model; output fenced |

Baseline scope reviewed against merged main: original checkpoint added 97 explicit
finding IDs and replaced 2 obsolete IDs. Added categories: 29 MaxLineLength,
19 TooGenericExceptionCaught, 17 InstanceOfCheckForException, 13 ReturnCount,
8 MagicNumber, 4 CyclomaticComplexMethod, 3 SwallowedException, and one each
LongMethod, TooGenericExceptionThrown, NestedBlockDepth, LongParameterList.
These retain donor algorithms/Android catch-and-fallback and adapter composition;
no rules disabled, no test assertion/permission/takeover guard removed. Baseline
is explicit port debt, not verification. New regressions must pass without new IDs.

Continuation adds registry audio-alias/output-fencing regression, positive DND/
ringer and takeover-denied mutation checks, and plan-verb rejection. Module tests,
ktlintCheck and detekt rerun underway; exact full gate/results to be appended.
The original single ktlintFormat invocation is not repeated.

First continuation full gate FINISHED after 43m17s at 93a236e: both app APK variants
assembled and all 698 JUnit tests ran (697 passed, zero failures/errors, one existing
pre-Q assumption skip). Gate then failed on exactly two module lint ObsoleteSdkInt
findings: donor API-M torch callback check and API-P clipboard branch, both below
EQO minSdk 30. Removed only the unreachable pre-30 guards/fallback; supported API30+
behavior unchanged, clipboard mutation remains takeover-gated. No lint suppression
or baseline added. Full exact gate is being rerun on the corrected source.

## Final continuation verification

Exact requested full Gradle gate FINISHED successfully on code HEAD
6527a180618fe413fed9ea06cb815839e1981784, exit 0, 26m13s:
`./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m`.
1003 actionable tasks: 200 executed, 803 up-to-date. Both app APK variants,
all module tests, lintDebug, ktlintCheck and detekt passed. No new baseline IDs
added to resolve continuation findings. Corrected-head branding and repository
checks also FINISHED exit 0; 352 tracked Kotlin files match 352 provenance rows.

JUnit XML totals recomputed programmatically: 698 tests, 697 passed, zero failures/
errors, one existing ModelDownloadSchedulingTest pre-Q assumption skip (EQO minSdk30
makes that path unreachable). Module totals: actions-android 36, adb-pairing 82,
app 95, browser-cdp 35, core-agent 72, core-llm 262, core-security 47,
helper-server 5, platform-a11y 64. All required typed-request/approval, lifecycle-
permission and takeover guard suites passed without skips. Actions-module suites:
registry 15, call 3, system 15, text analyzer 3; all 36 passed without skips.

Structural check: all 26 unique table/executor names exist in ActionSchema; no
forbidden secure-settings/shell paths and no TaskActivity diff against merged main.
Final evidence-only commit does not change the verified Android source/build tree.
Logs and per-suite JSON are attached to the task. No phone, PR or merge. Remaining
limitations are exactly those in the REAL/PARTIAL/UNAVAILABLE and device-boundary
sections above, not unfinished local gates. Runtime app analyzer wiring/image capture/
recording and live plan enablement are intentionally not included, per lead decision.
