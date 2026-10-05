# TASK-069 — action executor port, batch 1 part 1 (Refs #20)

## Status

Implementation checkpoint; verification results will be appended after real execution.
No device, phone, PR or typed-request-flow modification is included in this task.
Base: origin/main f738c3c. Branch: agent/android/69-port-actions-1.
Donor checkout HEAD was verified as 6ff5a061755b597b0558fed1f565587837ed4d51.
Toolchain pins: JDK 21, Gradle 9.7.0, AGP 9.3.1, Kotlin 2.4.0.
Live JDK: Temurin 21.0.12.1+1-LTS.

## Foundation choice

`:actions-android` owns Android action implementations under `ai.eqo.actions.impl`.
It depends on core-llm (ActionResult + the existing ActionSchema vocabulary),
platform-a11y (takeover-gated automation), core-agent (existing ContactResolver),
and core-security (existing AES-GCM SensitiveMemoryStore).
This avoids placing executors in the model vocabulary module or introducing a
platform-a11y -> actions dependency cycle. No Hilt/Room plugins or dependencies
are introduced in the new module; no dispatcher DAO or database is constructed.
Existing donor Hilt annotations in dependency modules are not used for construction.

Public entry point: AndroidActionRegistry.create(context, permissionRequester,
automationProvider, unknownActionSink), execute(actionName, params),
enabledActionNames. Executors and action-family lists are module-internal;
individual donor actions are private. A private coroutine-context permit guards
all ported action entry points, so even internal callers cannot execute one
outside a registry dispatch. The permit survives suspension/dispatcher switches.
Registry validates names/required parameters/defaults using ActionSchema, keeps
donor accepted parameter aliases, and rejects duplicate/unknown schema names.
UnknownActionSink receives only the unknown name, never a params map or secret.
Add later families to the explicit createWithStore factory; do not duplicate the
schema or add an independent dispatcher. Registry has no persistence dependency:
per the revised lead order batch 2 adds a separate persistence module after batch 1,
then batch 3 follows. Registry must not become coupled to that database.
Contextual approval remains the caller's responsibility: permission is not approval.

TaskActivity owns a lazy registry and TaskPermissionRequester, with Android
permission-result and settings-return lifecycle callbacks. This is the foundation
integration point, not a replacement for #68's typed-request plan/execution flow;
that flow still plugs into the registry later after its approval gate.

## Donor source-to-port map

All source paths below are under app/src/main/java/com/opendroid/ai/ at the pinned commit.
All destination implementations are under android/actions-android/src/main/java/ai/eqo/actions/impl/.

| Upstream source / lines | Destination | Treatment |
| --- | --- | --- |
| actions/CommunicationActions.kt 28–628 | CommunicationActions.kt | Ten communication actions; explicit construction, gated intent launch; send-only unsafe accessibility paths removed |
| actions/CallFlowExecutor.kt 14–87 | CallFlowExecutor.kt | Direct-call versus pending-dialer flow, dual permission checks, new-call verification and security fallback retained |
| accessibility/CallFlowVerifier.kt 14–56 | CallFlowVerifier.kt | Telecom polling verifier, 4-second timeout/250-ms interval, no injection annotations |
| actions/AdvancedControlActions.kt 163–198, 358–374, 384–515 | AdvancedControlActions.kt | Selected tap/type/scroll/enter/wait/screen/apps and file families; typed EQO facade outcomes, registry guard |
| core/storage/StorageWorkspaceProvider.kt 17–614 | StorageWorkspaceProvider.kt | SAF and app-workspace operations, read cap and ZipSlip guard; EQO preference name and tighter path boundaries |
| actions/CalendarActions.kt 582–602 | SaveSensitiveInfoAction.kt | Existing EQO AndroidSensitiveMemoryStore replaces Lazy PersonalGrowthEngine/Room; no key/label/secret echo |

SAVE_SENSITIVE_INFO is in CalendarActions upstream, not AdvancedControlActions.
No calendar executor or permission is imported just to obtain this action.
Provenance-map rows cover every added Kotlin file. Existing TaskActivity retains
its existing provenance row; its added lifecycle wiring is EQO TASK-069 code.

Enabled groups:
- SEND_SMS, SEND_EMAIL, SEND_WHATSAPP, SEND_WHATSAPP_GROUP, SEND_TELEGRAM,
  OPEN_TELEGRAM, MAKE_CALL, MAKE_VIDEO_CALL, READ_MESSAGES, READ_EMAILS.
- CLICK_ID, CLICK_TEXT, CLICK_COORDINATES, TYPE_ID, TYPE_TEXT, PRESS_ENTER,
  SCROLL, WAIT, GET_SCREEN_TEXT, LIST_INSTALLED_APPS.
- READ_FILE, WRITE_FILE, LIST_FILES, COPY_FILE, MOVE_FILE, DELETE_FILE,
  CREATE_DIRECTORY, ZIP_FILES, UNZIP_FILE, SAVE_SENSITIVE_INFO.

## Required just-in-time access / manifest changes

Only these runtime permissions are added, each with a manifest reason:

| Action | Permission / timing |
| --- | --- |
| SEND_SMS, SEND_WHATSAPP, MAKE_CALL, MAKE_VIDEO_CALL | READ_CONTACTS immediately before resolving a non-number contact |
| SEND_TELEGRAM | READ_CONTACTS only for a name requiring resolution, not direct username/number |
| MAKE_CALL on calling-capable hardware | CALL_PHONE and READ_PHONE_STATE immediately before placing/verifying a direct call |
| SEND_SMS to explicit number | None: ACTION_SENDTO smsto compose-only, no SmsManager, SEND_SMS or READ_SMS |
| SEND_EMAIL, OPEN_TELEGRAM, SEND_WHATSAPP_GROUP, READ_MESSAGES, READ_EMAILS | None: opening another app, not accessing its private providers |
| MAKE_VIDEO_CALL | No EQO camera permission: donor only opens a third-party app, which owns its own camera permission |
| Control, wait, screen text | Existing EQO accessibility enablement / takeover gate, not a dangerous runtime permission |
| File operations | No all-files/storage permission: donor app-specific workspace or user-selected persisted SAF tree |
| SAVE_SENSITIVE_INFO | No runtime permission: existing AndroidKeyStore encrypted app-private store |

Telephony is optional (`uses-feature required=false`). Narrow `<queries>` entries
cover donor communication intent handlers/packages for Android package visibility.
The :actions-android library manifest declares only READ_PHONE_STATE so lint can
verify the guarded `TelecomManager.isInCall` call inside the ported call verifier;
it merges into the same permission set :app already declares and adds nothing new.
LIST_INSTALLED_APPS reports only packages Android makes visible; no broad
QUERY_ALL_PACKAGES permission is added. Contacts/calendar/SMS/camera/notification/
overlay/all-files permissions not actually consumed by this port are not added.

PermissionRequester is a suspendable fakeable seam. The Activity adapter checks an
existing grant; otherwise requests the actual Android dangerous permission at the
point of need, then rechecks grant state on callback. Denial returns a plain failure
and prevents dispatch. Request rationale is shown when Android indicates it is needed.
Destroyed/cancelled task screen fails the pending request, not the action. Later
families can supply SpecialAccess with the exact settings action/package scope,
plain instructions and a grant predicate; the adapter opens that screen only after
the owner presses Continue and rechecks access on return. No current ported action
requires notification-listener/overlay/all-files special access, so no unused access
is requested. This does not grant third-party apps' own permissions.

## Safety substitutions / behavior differences / dropped paths

- Every intent-driven UI launch is inside EqoAutomation.runAction through
  GatedIntentLauncher. All taps/types/scrolls/gestures/IME operations use existing
  GenericAppAutomator, which itself routes through the same facade. Disabled
  accessibility and takeover fail; there is no raw performAction in this module.
- GET_SCREEN_TEXT uses gated observe, retaining secure-window refusal and password
  subtree filtering. Screen/file/list/app-label data is fenced by existing
  UntrustedScreenText, which neutralizes closing-fence sequences.
- SMS keeps #66's compose-only ACTION_SENDTO smsto contract. Donor SmsManager,
  SMS auto-send, and ACTION_VIEW sms fallback are dropped: not permission-granted
  sending, and never fake success merely because a draft opened.
- WhatsApp/Telegram donor intent URI/package/fallback shapes remain. Unsafe donor
  automator/send retry/raw-node verification paths are dropped rather than bypassing
  EQO's gate. Drafts return UserActionRequired, never 'sent'. Group action remains
  manual; video-call action only opens an app and returns UserActionRequired.
- Email draft remains UserActionRequired; direct calls report success only when
  verifier positively sees a new active call. Already-active/inconclusive calls
  fail, dialer fallback is PendingUserAction, not a completed call.
- WAIT retains the 10-second hard ceiling and non-negative clamp. Coordinates
  additionally reject negative/non-finite values before any gesture dispatch.
- File behavior retains the donor 100-KB read ceiling, workspace mapping, SAF
  single-file paths and ZipSlip guard. Relative traversal checks use a directory
  separator boundary (not a prefix collision); absolute access to other app-private
  data is refused. Copy/move/zip reject self/nested destinations to avoid unbounded
  recursion/data corruption. Copy/move/archive retain donor local-file behavior,
  not a newly invented SAF archive implementation. Two donor "/sdcard" strings were
  lint-flagged: the unreachable post-canonicalization branch in resolveFile was
  removed (the /sdcard symlink canonicalizes to the external-storage root, which the
  neighbouring branch handles), and the raw-input prefix list in cleanRelativePath is
  kept 1:1 behind a scoped SdCardPath suppression because it matches user input
  rather than choosing a storage location.
- Sensitive save uses the existing AES-GCM store; no growth-engine/knowledge graph,
  Room, sensitive log content, false 'hardware-encrypted' claim or echoed label.
- Other AdvancedControl families (system info/ringer/background camera/close app)
  are outside the selected scope and not enabled. No Python bridge, shell process,
  root, termux or connector path is introduced; D-005 remains inert.

## Verification / device limitations

Tests are local JVM/Robolectric with fake permission answers, verifier and encrypted
store boundary. Intent resolution/startActivity is shadowed; no network services
are invoked and no real messages, calls or third-party accounts are used.
- `scripts/check-branding.sh`: PASS, exit 0; tracked Kotlin files/provenance rows both 333.
- `scripts/check.sh`: PASS, exit 0.
- `:actions-android:ktlintFormat` and `:app:ktlintFormat`: PASS in the targeted invocation.
- `:actions-android:testDebugUnitTest`: PASS, 17 tests (registry/permit/schema,
  messaging intent shapes, denial paths, takeover, control actions, wait ceiling,
  file CRUD/traversal/ZipSlip/size caps, sensitive store, call permission/verification).
- `ai.eqo.task.TaskPermissionRequesterTest` (run alone first, per lead): PASS, 3 tests
  (just-in-time runtime request + grant continue, decline and task-destruction fail
  without proceeding, special-access instructions -> exact package settings deep link
  -> grant recheck on return). The first coroutine-scheduling version of this test
  hung a Gradle test JVM in virtual time and was replaced: the suite now runs each
  request unconfined on the Robolectric main thread (same thread model as production
  Dispatchers.Main) with bounded 5-second waits, and the product dialog now wires its
  buttons with direct synchronous listeners so a tap can never race an internal
  AlertDialog handler. No test can block beyond its bounded waits.
- Static scan: all 30 requested executor names are present; all new implementation
  files have Origin headers; no performAction, Runtime.exec, ProcessBuilder,
  SmsManager or UnknownActionDao tokens occur in the implementation module.
- Donor-port structural complexity (ReturnCount/LongMethod/etc. in the 1:1 ported
  storage/communication families) is recorded in `detekt-baseline.xml`, matching the
  established repo baseline practice; trivial findings (empty constructor, unused
  params/properties, UseRequire, constant function) were fixed instead.
- Full gate, single invocation from android/ (JDK 21, Gradle 9.7.0, AGP 9.3.1,
  Kotlin 2.4.0; `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug
  ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m
  -Pkotlin.daemon.jvmargs=-Xmx1024m`, 1h40m under concurrent builds): both APKs
  assembled; :actions-android (17 tests), :app (full suite), :adb-pairing and
  :browser-cdp testDebugUnitTest all passed. One unrelated pre-existing stress test,
  core-agent's ActionLoopRaceTest "threaded command storm keeps every settle
  invariant", failed once on a threaded-round settle-timing assertion ("settle state
  must be terminal, was RUNNING") and passed on two forced re-runs — core-agent is
  not touched by this branch (no diff in that module), so it is recorded as a
  load-sensitive flake, not a regression.
- The gate's lint stage caught three classes of finding in the ported code, all fixed
  (library manifest carries READ_PHONE_STATE + the same narrow <queries> list as :app;
  unreachable hardcoded "/sdcard" resolveFile branch removed; KTX edit/toUri used; the
  raw-input prefix normalizer keeps donor behavior behind a scoped SdCardPath
  suppression; LIST_INSTALLED_APPS carries a scoped QueryPermissionsNeeded note —
  only visible packages are listed and no QUERY_ALL_PACKAGES permission is added).
  `lintDebug ktlintCheck detekt` over all modules then passed (14m), as did forced
  re-runs of TaskPermissionRequesterTest (3/3) and ActionLoopRaceTest.

NOT verified on any device: Android/OEM permission-dialog presentation and permanent
denial UX; settings lifecycle and grants across process death; contacts resolver
accuracy on real contacts; Telecom state transitions/radio/SIM behavior; messaging,
email, Telegram/WhatsApp/Meet/Zoom intent handling; accessibility gestures/IME or
third-party layout timing; real secure-window enforcement; real SAF provider grants,
file operations/archives or AndroidKeyStore hardware backing. No phone was used.
