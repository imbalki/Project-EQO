# Phase One phone test log

## Fixes for round 1 — t_7a91b291 — NOT TESTED ON PHONE

- 2026-10-10 accepted ADR-0008 implementation: preview lists runtime prerequisites; Allow now requests missing grants BEFORE approval/run. Existing Android grants are reused. The service is check-only; revoked permission does not launch a dialog and requires Stop plus explicit restart/reapproval. Removed the permission-controller touch bypass entirely; ordinary takeover, own-window and no-auto-resume protections remain.
- Fresh host verification, sequential modules with `--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`: actions ktlintFormat/check/detekt and 72 focused tests (5 suites), `BUILD SUCCESSFUL in 1m 28s`; platform-a11y format/check/detekt and all 102 tests (18 suites), `BUILD SUCCESSFUL in 50s`; app format/check/detekt and all 199 tests (34 suites), `BUILD SUCCESSFUL in 2m 38s`. Fresh XML has zero failures/errors/skips. `scripts/check.sh` passes (445 Kotlin/provenance rows); diff check passes; no added Kotlin line exceeds 120. Earlier failed iterations found formatting/static-analysis issues, an offline-weather test expectation changed by centralized prerequisite checking, and a JUnit test return type; final reruns above passed.
- Actual TaskActivity Robolectric regression: preview before Location request, typed-number Contacts omission, no pending run/controller before approval, Stop cancels preparation, late grant cannot start it, explicit retry with grant remembered reaches approval without executing. Fake registry tests prove deduplicated inventory and no read/send on preflight; requester tests retain cancellation/stale callback/120-second coverage and verify active-run missing-grant refusal. Platform source regression rejects any permission-touch bypass. No device/input-attribution success is claimed.
- Current phone checklist: grant/deny Location and named Contacts BEFORE run, wait over five seconds before answering, Stop/cancel then late callback, explicit retry after denial, remembered grant without another prompt, revoke between preview/approval or during run and verify no permission UI/automatic resume, delayed OEM input/lifecycle ordering, All files grant/return, screenshot guards, Keep note, arithmetic words, draft-only WhatsApp and multiple email recipients. NOT TESTED ON PHONE. No instrumentation, APK/release build, full Gradle gate or remote CI. Core-agent/core-llm and broad actions were not rerun in this rework; earlier evidence/failures remain below. Lead owns publication.

### Historical iterations (permission blocker superseded by the accepted pre-run flow)

- 2026-10-10 review rework: card remains BLOCKED on runtime-permission architecture, not ready for final approval. Re-fetched Android 11 InputDispatcher source and confirmed cross-UID outside-touch coordinate redaction (`FLAG_ZERO_COORDS`, lines 1846–1859 and 2523–2529). The existing Boolean permission helper test is not service-level attribution proof, and the bounds exception cannot identify a real Allow tap from the redacted event. Production permission/takeover code was not weakened or changed in this rework. See proposed ADR-0008 for the owner decision: recommended pre-run runtime prerequisites, with explicit restart after mid-run revocation; strict same-step mid-run continuation needs a different trustworthy input surface. No claim of a fixed real-phone Allow flow.
- Fixed the other review finding: named Keep/Notes writing can use known local note-navigation taps and typing before immutable plan approval; communication drafts still reject arbitrary taps/IDs, submit and sending actions. Added three fake-provider tests covering accepted note writing/type/draft routes, rejected Send/Share/Publish/unknown note taps and communication actions, and strict explicit don't-send/communication requests.
- Rework verification: sequential `:core-agent:ktlintFormat :core-agent:ktlintCheck :core-agent:detekt :core-agent:testDebugUnitTest --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`, exit 0, `BUILD SUCCESSFUL in 2m 27s`, 60 tasks (6 executed, 54 up-to-date). Fresh JUnit XML: 110 tests, 0 failures, 0 errors, 0 skipped in 19 suites. Earlier attempts caught and corrected a return-count violation, a long test line and a test fixture's wrong TYPE_TEXT parameter (`content`, not `text`); those failed runs are not passing evidence. `scripts/check.sh` passed (445 Kotlin files/445 provenance rows); `git diff --check` passed; no added Kotlin line exceeds 120 characters. No other module, phone, instrumentation, APK/release/full Gradle gate or remote CI was rerun in this rework. Prior test evidence follows, not a new device result.
- Branch `fix/round1-phone-tests`, based on `92802e7`. The checked-in log did not contain a "Phone round 1" section; this card's supplied phone findings are the inputs, not new device test results.
- Added fake/unit regressions for the permission apply-budget exclusion and 120-second wait, Stop/stale callbacks, narrowly scoped permission-dialog touch attribution, typed-number location permissions, All files Settings grant/recheck, title-only notes, calculator words/no-web-on-invalid-arithmetic, draft-only WhatsApp/no-send planning, explicit channel clarification, multiple email recipients and plain permission/failure copy.
- Screenshot investigation: `canTakeScreenshot=true` is already declared. All files access is not needed for EQO's private screenshot fallback. Own-window refusal is intentional; the failure now tells the owner to open the app to capture, without weakening that guard.
- All files Settings is normally completed before a run starts, after showing the preview warning. Returning with access granted continues the same plan; the owner still approves its actions. Settings is not a takeover exception. A real takeover or mid-run revocation never auto-resumes.
- Initial `:actions-android:testDebugUnitTest --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`: 145 tests, five failures (untouched `AutomationExecutorsTest` macro timeout and four `SharedStorageTest` Windows link/path cases). This is not a passing full gate. Initial compile also found a hidden Android permission-controller API; implementation now uses allowlisted system packages plus verified dialog/control bounds.
- Host checks passed sequentially with `--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`: actions `ktlintFormat`, `ktlintCheck`, `detekt` and 71 focused tests (`ContactRecipientsTest`, `ProductivityInformationTest`, `FileFeaturesRegistryTest`, `ShareActionsTest`, `SystemActionsTest`); full core-agent checks and 107 tests; full core-llm checks and 272 discovered tests (one skipped); full platform-a11y checks and 102 tests; final app checks and 197 tests, zero failures/errors/skips (`BUILD SUCCESSFUL in 8m 28s`). Recovery reran app format/check/static analysis/test tasks successfully in 1m 46s (tests up-to-date). `scripts/check.sh` passed again (445 Kotlin files, 445 provenance rows), `git diff --check` passed, and no added Kotlin line exceeds 120 characters. Full repository Gradle gate, APK build and device instrumentation NOT RUN. No device was contacted or changed. CI and lead-owned phone tests remain required.
- Recovery broad-actions retry: completed JUnit XML reports 146 tests, five failures, zero errors/skips. The same untouched cases fail: macro delete/list exceeds the coroutine test timeout; two shared-storage symlink fixtures lack Windows privilege; two shared-storage path assertions fail on Windows. The terminal transport timed out at 420 seconds while Gradle continued; the wrapper was subsequently observed exiting and XML results were parsed. These failures were not reproduced on an untouched baseline and are not claimed as a passing full actions gate. No out-of-scope macro/storage code was changed.
- Phone checks remaining: hold Allow for more than five seconds, grant/deny Location and Contacts, Stop while permission is pending, touch outside a permission dialog and verify takeover, missing All files access then grant/return, protected/own-app screenshot refusal, real WhatsApp draft preserving text with no Send, multiple Gmail recipients, Keep title creation, and arithmetic answers.


## Files v2 — t_ecfe91de — NOT TESTED ON PHONE

- Local branch `feat/files-v2`; tested code `add2fab`, no push/PR or phone contact. Synthetic files and fake selection callbacks only. Host results below do not verify a real attachment or Send button.
- Phone round-1 failure motivating this card (task-supplied evidence): All files access ON, FIND_FILES succeeded, then SEND_WHATSAPP/SEND_EMAIL with an attachment failed in the 12:28–12:31 test window. The pre-run planner could not know a file path; use `find:` directly on the send action rather than a made-up FIND_FILES output path.
- [ ] "send my eBay bill to <test contact> on WhatsApp": approved search in preview, exact chosen name in run status before Continue, correct attachment/chat, Send pressed only after disclosure.
- [ ] "email the screenshot from 7 October to me": planner emits `find:type=screenshot,date=YYYY-10-07` using the intended year; gallery screenshot from Pictures/Screenshots or DCIM/Screenshots, correct attachment, disclosure before Send.
- [ ] Several matches -> chooser: at most eight names with date/size, newest first; tapping a non-first match attaches only that file. `latest` still requires choice.
- [ ] Zero matches: plain searched-query message, no compose/send. No foreground UI: multiple-match name list and refusal.
- [ ] Cancel, background, rotation, timeout, Stop/takeover: no unintended attachment or send; ordinary task controls/takeover still work.
- [ ] Exact date/local midnight and inclusive range; words in different order/case; folder/type restrictions; All files access denied; hidden/other-app/staging/private/link paths excluded.
- [ ] TAKE_SCREENSHOT + last_screenshot remains working; staged copies swept on age/failure; logs contain only counts/kinds/codes, not names/paths/searches.
- [ ] Realme/Samsung/Xiaomi (where available): MediaStore screenshot/camera/gallery buckets, vendor-specific directories and Download/Downloads synonyms; modern and legacy WhatsApp/Business, Telegram, Instagram, Bluetooth and Documents.
- [ ] Discovery is reused across runs/restart; `rescan=true` refreshes on demand, zero matches refresh automatically; no filename or search saved in the folder-map preferences. MediaStore denied/incompatible/stale -> safe filesystem fallback, without guessing among matches or visiting Android/data/obb.

### Files v2 host verification (resumed run)

- Serial module-scoped Gradle, from `android/`, with `--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process --console=plain`. For each of `core-agent`, `core-llm`, `actions-android`, `app`: `:MODULE:ktlintFormat :MODULE:ktlintCheck :MODULE:detekt :MODULE:testDebugUnitTest`; app also `:app:assembleDebug`.
- Core-agent: exit 0, `BUILD SUCCESSFUL in 1m 38s`, 60 tasks up-to-date. JUnit XML: 108 tests, zero failures/errors/skips; cached passing execution from the first attempt, not a new test run.
- Core-llm: exit 0, `BUILD SUCCESSFUL in 5m 53s`, 68 tasks (16 executed, 52 up-to-date). JUnit XML: 272 tests, zero failures/errors, one existing pre-Android-Q/minSdk skip.
- App: exit 0, `BUILD SUCCESSFUL in 10m 15s`, 240 tasks (56 executed, 184 up-to-date). JUnit XML: 195 tests, zero failures/errors/skips, including four chooser/disclosure/lifecycle tests and the task-control takeover checks. Debug APK built at `android/app/build/outputs/apk/debug/app-debug.apk`; not installed.
- Actions: ktlintFormat/ktlintCheck/detekt pass, but full-suite exit 1, `BUILD FAILED in 7m 19s`, 120 tasks (10 executed, 110 up-to-date). JUnit XML: 155 tests, one failure, zero errors, three skips. Sole failure: unchanged `AutomationExecutorsTest` / `delete and list macros keep system macros and sort names`, `UncompletedCoroutinesError` after its explicit 10-second timeout, reproduced on retry. No unrelated macro production/test changes were made. This is NOT a passing full gate; lead/CI must investigate or confirm on an unloaded host.
- File-specific suites within that full run: AttachmentFileSearchTest 13, SharedStorageCatalogTest 7, SharedStorageTest 21, FileFeaturesRegistryTest 19 (60 total), zero failures/errors. Three actual-link tests skip only when host link creation is unsupported (Windows privileges); deterministic injected-link/path-ancestor exclusion tests do run. CI must exercise actual symlinks. Staging sanitization uses a portable source filename and separately asserts invalid-name sanitization.
- Verification fixes: split overlong resolver expression; search all coexisting folder aliases; avoid generic `Media` leaf learning across WhatsApp/Business; cap directory discovery globally; update stale reflective task-dialog test to pass the added chooser flag. No existing takeover guard, approval or send safety was relaxed.
- Focused actions rerun: `:actions-android:testDebugUnitTest --tests 'ai.eqo.actions.impl.AttachmentFileSearchTest' --tests 'ai.eqo.actions.impl.SharedStorageCatalogTest' --tests 'ai.eqo.actions.impl.SharedStorageTest' --tests 'ai.eqo.actions.impl.FileFeaturesRegistryTest'` with the same worker/compiler flags. Exit 0, `BUILD SUCCESSFUL in 1m 21s`, 106 tasks (one executed, 105 up-to-date). Fresh XML confirms 60 tests, zero failures/errors, three capability skips. This focused execution replaces the local XML files from the failing full run; the full-run failure remains recorded above, not concealed by the focused pass.
- `bash scripts/check.sh`: exit 0, `455` Kotlin files/provenance rows, `BRANDING GATE PASSED`, `OK`. `git diff --check`: exit 0. Added/modified Kotlin lines are at most 120 characters; pre-existing schema/registry long lines are unchanged.
- NOT RUN: phone, instrumentation, publication/CI or a repository-wide Gradle gate. Required phone checklist above remains unchecked.
## Voice v2 — 2026-10-09 — NOT TESTED ON PHONE

Branch `feat/voice-v2`, card t_e9801e01. No phone was contacted or modified. No real provider audio was uploaded.
Initial `:app:ktlintFormat :core-llm:ktlintFormat :app:compileDebugUnitTestKotlin --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process` succeeded in 8m 27s (Gradle daemon log verified after tool timeout).
Final frozen-source checks for local code head `381b083` completed after the earlier worker timed out:
`:app:ktlintFormat :core-llm:ktlintFormat :app:ktlintCheck :core-llm:ktlintCheck :app:detekt
:core-llm:detekt :app:testDebugUnitTest :core-llm:testDebugUnitTest --max-workers=2`.
Saved `voice-v2-final-gates.log` reports `BUILD SUCCESSFUL in 9m 48s` (220 actionable tasks).
Verified XML: app 209 tests, core-llm 277 tests; zero failures/errors, one existing core-llm skip.
All 34 voice/provider tests pass (6 Android adapter, 10 presenter, 13 v2 recording/UI, 5 provider).
Both module debug lint XML reports contain zero fatal/error/warning issues. Repo/branding/provenance checks
pass (451 Kotlin files and 451 provenance rows); no changed Kotlin line exceeds 120 characters.
Resumed-worker confirmation on the unchanged Kotlin source: app/core-llm `ktlintCheck`, `detekt`,
`testDebugUnitTest` and `lintDebug`, with `--max-workers=2 --console=plain
-Pkotlin.compiler.execution.strategy=in-process`, exited 0: `BUILD SUCCESSFUL in 28m 3s`
(370 actionable tasks: 42 executed, 328 up-to-date). Unit tests were up-to-date from the passing frozen-source run.
CI remains the full merge gate; no APK install or real audio upload was performed.

Phone checklist (owner must record build commit and actual outcome):
- [ ] Phone engine: long English sentence with multiple 3–4 second pauses; partials appear live, stop control works.
- [ ] Phone engine: hi-IN long Hindi sentence with pauses; en-IN, device default and provider-reported languages.
- [ ] If the service still ends early, already-heard words remain; next mic tap adds rather than wipes.
- [ ] Grant, deny, cancel and permanently deny microphone permission; no request at startup, typing still works.
- [ ] Edit recognized words; nothing submits until the normal task button is tapped, preview/approval unchanged.
- [ ] AI engine: first-use consent accept/decline; only accepted consent allows capture and provider upload.
- [ ] Audio-capable configured model: English and Hindi verbatim transcripts; stop tap, 60-second cap, repeat append.
- [ ] Text-only model: plain audio-unsupported explanation and Use Phone voice fallback; no audio upload.
- [ ] Airplane mode on both engines: understandable failure, words kept, no automatic network retry.
- [ ] Leave screen while recording/transcribing, or tap mic during transcription: no late draft overwrite,
      microphone released and EQO voice cache file deleted. Test failures/cancellation as well as success.
- [ ] Compare phone vs AI accuracy and latency; verify offline language packs separately in the phone provider.

## Explain/handle UX polish (t_e84b3eaa): NOT TESTED ON PHONE

This worker has not installed or exercised this change on a phone or emulator. Owner-reported
earlier-phone behavior is the motivation, not verification of this revision. Full CI is the gate.
Fake-clock tests cover all panel sizes/background alpha and the five-second peek deadline;
Robolectric tests cover overlay close/reopen, touch flags, source intents, notification repost,
accessibility refresh events, hub opt-in/background intent and hidden-app recovery. Existing Ask
tests now assert a real focused request screen, voice control and no misleading Practice run title.
Final local checks: sequential scoped platform/app ktlintFormat, ktlintCheck, detekt,
testDebugUnitTest and lintDebug commands from CURRENT-HANDOFF.md exit 0 (1m 11s and 6m 10s).
Both use max-workers=2 and the in-process Kotlin compiler. JUnit XML confirms platform 111,
app 251, total 362 tests; no failures/errors/skips. The 22 new API-30/33 cases all pass.
Initial failures exposed premature deferred-panel cancellation, timer scheduling and Button.handler
receiver shadowing; corrected before the final full scoped runs. Removed an obsolete string and
documented the single native Switch XML lint exception (platform Activity/StudyTheme, no new dependency).
Repo/branding/provenance/secret, added/changed Kotlin line length, XML parse and whitespace checks pass.
NOT RUN: full all-module/root Gradle gate, APK assembly/install, release tasks, device/emulator
instrumentation, real provider/speech/OEM behavior or GitHub CI.

Phone checklist: follow the seven steps in CURRENT-HANDOFF.md's UX polish section. Record
build commit, OS/OEM, panel/large-font/rotation/keyboard behavior, peek touch-through and timing,
open-close-open from each entry, notification recovery after service death and preference-off,
QS editor/manual install (API 30) and platform add request (API 33+), accessibility/volume shortcut,
hub handle immediate rendering/off hint, battery settings, real Ask focus/mic and per-app/all unhide.

## Pairing discovery fix (t_9a512691, fix/pairing-discovery): NOT TESTED ON PHONE

Local-only implementation; no device was contacted or changed. No model/provider request was made.
Verified code commit: `c8d6d2ad98ee912f57e3ed2bd3fc19e814a7b40b` (later documentation-only commit does
not change tested code). Each command below ran sequentially from `android/`, with
`--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`, and exited 0:

| Gradle tasks | Actual final result |
|---|---|
| `:app:ktlintFormat` | BUILD SUCCESSFUL in 1m 2s |
| `:app:ktlintCheck :app:detekt :app:testDebugUnitTest` | BUILD SUCCESSFUL in 6m; 255 app tests |
| `:app:lintDebug :app:assembleDebug :app:processReleaseMainManifest` | BUILD SUCCESSFUL in 13m 3s |

- Final JUnit XML: 255 tests, zero failures/errors/skips. Pairing regressions: 50 executions (DNS parser 8,
  reply/pending state 6, fake resolve attempt 3, debug receiver 3, existing discovery state 10, notification/
  Android discovery 20 on API 30 and 33). Includes real framework failure/timeout callbacks with an injected
  mDNS lookup, late NSD rejection, code-envelope consumption, strict input, network-bound pending expiry,
  and unchanged fake PAIR+CONNECT-only/no-helper path. Test data is synthetic; no pairing sockets were
  opened against a device. Real multicast socket/lock behavior remains a phone checklist item.
- Source-set regression verifies the lab receiver is only under `src/debug`, guarded by BuildConfig.DEBUG
  and protected by DUMP. Read-back of actual merged debug/release manifests independently confirms the
  exact debug receiver/action/permission and absence of the receiver/action in release. The built debug
  APK's DEX contains the receiver. A release APK/DEX was not built or inspected.
- Actual APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 59,776,547 bytes; SHA-256
  `c7d10024f9b3154fcdb049cc6163b844b55d3e193cf5d8a208443fcc5aded1f8`.
  ZIP inspection confirms helper starters for arm64-v8a, armeabi-v7a, x86 and x86_64. No install attempted.
- `bash scripts/check.sh` passes: BRANDING GATE PASSED, 469 tracked Kotlin files/469 provenance rows.
  `git diff --check` and changed Kotlin lines <=120 checks pass. Raw host logs are kept under ignored
  `android/app/build/reports/pairing-fix/`, not committed. No push, PR or gh action by this worker.
- NOT RUN: full root/all-module Gradle checks, untouched modules' unit-test tasks, release lint/unit tests/
  APK build, connected/device instrumentation or GitHub CI. App tasks' dependency compilation/lint
  analysis is not those modules' unit-test execution. NOT TESTED ON PHONE; every phone item below remains.

Initial lint/compile findings were fixed without a baseline: bounded parser wire-number annotation,
named transport constants, split framework listener method, short imports/lines and explicit test generic.

Phone checklist (lead/owner; record build commit and pass/fail, never real identifiers or credentials):
- On the Android 11/ColorOS phone, open EQO setup, enable its notification, then open Wireless debugging.
  Open Pair device with pairing code. Confirm EQO finds both ports despite the framework mapping failure;
  app logs may contain only `resolve: nsd fail` / `resolve: mdns ok` (or generic fail), no values/names.
- While the pairing dialog stays open, reply with its six-digit code from the notification. Verify PAIR
  and CONNECT pass, no helper authorization occurs in the background, and returning/tapping Connect again
  displays the protected EQO helper prompt. Deny/Back must fail; Allow must be a real human tap.
- Force discovery to be unavailable in a lab build or environment: Reply remains visible. Supply code,
  pairing port and connection port in one string, with spaces then commas, without split screen. Verify
  the normal pinned path succeeds, and the code is cleared/consumed. Check the in-app box accepts both.
- Supply code and pairing port only: use a discovered connection port if present; otherwise verify the
  plain waiting message, no premature pairing or secure-enrollment report, successful late discovery,
  replacement by a three-field reply and discard after one minute. No connection port is guessed.
  Forget or a new foreground pair/reconnect must cancel the pending code before any new transport work.
- Reject malformed codes, Unicode digits, signs, out-of-range/equal ports, extra fields and bad separators
  before transport. Show a plain error; do not echo the entry or exception. Reopen expired pairing dialog.
- Test notification denial/channel blocking and the in-app fallback; keyboard allows spaces/commas and
  does not save/autofill the code. Check Android 13+ notification permission and background restrictions.
- Toggle Wi-Fi, change network/address, enable VPN (API 30–32), stop discovery and close during lookup:
  no stale endpoints/replies restore state; multicast socket/lock are released. Another LAN device's
  advertised service is never selected. Repeat toggle/re-pair and rejected server-key/pinning scenarios.
- In an installed DEBUG build only, use an explicit DUMP-authorized lab broadcast with the three extras
  documented in CURRENT-HANDOFF.md. Verify shared PAIR+CONNECT, no helper auto-consent. A normal sender
  without DUMP is denied; a release build has no receiver/action/class and cannot accept the broadcast.
- Verify actual local multicast/unicast delivery, OEM multicast filtering and service-name handling;
  host fixtures do not prove any of these real-network behaviors. CI remains the complete merge gate.

## Edge handle (t_a3fa16d0, feat/edge-handle): NOT TESTED ON PHONE

Local code commit `205739d`: sequential platform-a11y and app ktlintFormat, ktlintCheck, detekt,
testDebugUnitTest and lintDebug commands exit 0 (`--max-workers=2`, Kotlin compiler in-process).
JUnit XML totals: platform 111, app 186; 297 tests, no failures/errors/skips. Six app edge-handle
tests exercise real preference/widgets/overlay objects with fake run execution, including existing
controller Pause/Stop, focus, drag/edge switch, hide-app and settings choice/order/reset. Ten platform
edge-handle tests use fake registry storage/nodes/operations to check defaults/upgrades/availability,
own-overlay refusal and takeover exclusion. Exact commands and checks not run are in CURRENT-HANDOFF.md.
This feature has not been installed or exercised on a phone by this worker. Full CI remains the gate.

Round-1 review correction (local-only): panel opening now requests the existing user Pause once and
waits for no in-flight action before showing the guarded window. Closing never resumes or clears
takeover. Final sequential scoped commands in CURRENT-HANDOFF.md both exit 0: platform 2m 19s,
app 5m 34s. JUnit XML totals: platform 111, app 189; 300 tests, zero failures/errors/skips, including
19 edge-handle tests. Added real-overlay/controller regressions cover a due step during paced work
followed by panel Pause/Stop, in-flight settle with one Pause request and no resume on dismissal,
and cancellation of a deferred panel after foreground change/disable. Underlying/panel automation
remains blocked. Repo/branding/provenance, whitespace and changed Kotlin line-length checks pass.
Initial detekt method-size/return-count findings were fixed before the final passing runs.
NOT RUN: all-module/root Gradle checks, untouched-module unit suites, assembleDebug/APK install,
release tasks, device/emulator instrumentation, phone testing or GitHub CI.

Phone checklist (record build commit and actual results before merge):
- Enable EQO accessibility; confirm handle is absent until enabled in Edge handle shortcuts.
- Show handle over Gmail, Chrome and Android Settings; tap/swipe inward, outside dismissal and Back.
- Drag vertically, switch left/right edge, rotate and restart service/app; check saved placement.
- Long press, Hide for this app, switch app, then restore hidden apps in settings.
- Disable/reorder/reset shortcuts; restart app; verify choice/order persist and unavailable controls are absent.
- Ask EQO opens the task request with focus/keyboard and does not plan/run; Open EQO opens home.
- Open the panel during pacing, wait past the next step's due time, select Pause then Stop: no FAILED
  terminal, underlying tap or takeover. Opening requests user Pause; dismissal must not resume.
- Open during an in-flight action: the action settles before the panel appears; Stop stays usable.
- Dragging the collapsed handle must not trigger takeover or pause the run.
- A normal touch outside the collapsed handle during work still triggers takeover; handle never clears it.
- Try an approved agent coordinate tap at the handle and a node/coordinate tap while the panel is open:
  no handle/panel button is activated by the agent, even with another app active underneath.
- Inspect OEM keyboard/full-screen game/video hiding; explanation must match observed limitations.
- Disable handle, revoke/re-enable accessibility and repeat takeover tests; existing touch probe still works.
## Explain screen — t_699c0abc

NOT TESTED ON PHONE. Local-only branch `feat/explain-screen`, base `4a49832`. No provider request or phone action was performed by this worker.

Phone checklist (lead/owner; use synthetic content and do not record real messages or keys):
- Add the Explain screen tile in Quick Settings. From Calculator, explain a formula; ask a formula/image follow-up and check screenshot-vs-text fallback with image-capable and text-only models.
- From Android Settings, tap the tile: shade closes, EQO main UI does not open, the result describes Settings and its main controls. Verify no control is tapped or changed.
- From a Chrome page with ordinary text and a diagram, verify explanation and a typed follow-up refer to the same original screen. Navigate behind the sheet, ask again, and verify no different screen image is sent. Close, reopen and verify the prior session is gone.
- From a synthetic Gmail inbox, explain using both tile and notification. Verify no message is opened or sent, and EQO is not brought to the foreground first.
- Sharing OFF: first-use disclosure appears before any observation/provider request; Close declines. Allow enables sharing explicitly. Switch it off in Setup and verify future requests do not read/upload.
- Check a password field and a very large/deep screen: no password text/image is sent; text-only notice appears when privacy inspection is incomplete.
- Android 13+ notification denied/channel blocked: tile remains usable and no invisible observation occurs. Enable notification, test the action from another app, then disable it and verify it disappears. Test Android 11 and Android 14+ tile entry/background restrictions.
- Read aloud and auto-read: device-language voice, missing voice data, long explanations, Stop on Close; verify keyboard and controls fit at large font size and with TalkBack. Note whether the configured TTS engine uses network synthesis.
- Missing accessibility, missing model/key, offline provider and refused model: plain errors, no raw provider message/key/content in logs. Protected window: permissive policy seam; Android capture refusal falls back to text rather than bypassing FLAG_SECURE.
- Close during an in-flight request, disable accessibility, or open a replacement sheet: no late result reopens it. Rotation/lockscreen/process death must not persist screen context.

Host verification of code commit `96220ce1e6c57f8bad6553231b5e50c2e7906a01` (later documentation-only commit does not change tested code):

Each command ran separately, sequentially from `android/`, with `--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process --console=plain`. Every command exited 0:

| Gradle task | Actual result |
|---|---|
| `:app:ktlintFormat` | BUILD SUCCESSFUL in 2m 38s |
| `:app:ktlintCheck` | BUILD SUCCESSFUL in 55s |
| `:app:detekt` | BUILD SUCCESSFUL in 39s |
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL in 5m 46s; 209 tests, zero failures/errors/skips |
| `:app:lintDebug` | BUILD SUCCESSFUL in 5m 30s |
| `:app:assembleDebug` | BUILD SUCCESSFUL in 4m 52s |

- Explain tests: 15 pure fake-session tests plus 14 Robolectric test executions (seven adapter tests on API 30 and 33), 29 total. Includes text/image choice, sparse/capture fallback, exact model capability, consent OFF, own/System UI roots, passwords/hidden text, privacy bounds, immutable original context, adapter cleanup, late capture/answer rejection, and notification action routing. All model answers/images are explicit synthetic fixtures; no inference request was sent.
- Repo gate: `bash scripts/check.sh`, exit 0, `kt files: 448; provenance rows: 448`, `BRANDING GATE PASSED`, `OK`. `git diff --check` exits 0. Every new Kotlin file is mapped.
- Real APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 59,632,916 bytes; SHA-256 `9c6c1c1c3575087dff5ec292216a71439855f92ad39bed7a450490e50750abbb`. ZIP inspection confirms all four existing helper-starter ABIs are packaged. No install was attempted.
- Ignored local host logs: `android/app/build/reports/explain-screen/`, especially `explain-final-gates.log`. Raw host logs are not committed or uploaded. Initial verification found a nullable image receiver compile error and new-code detekt/lint issues; these were fixed, not baselined. The final complete sequence above passed. The only targeted new lint suppression is the Intent tile-launch overload on OS versions below API 34, where the PendingIntent overload does not exist; API 34+ uses PendingIntent.
- NOT RUN: full-repository Gradle `ktlintCheck detekt test`, other modules' unit-test tasks, release lint/build/unit tests, connected/device instrumentation, live accessibility/screenshot/provider calls, voice integration on the advanced main commit, and every phone checklist item above. Dependency compilation/debug lint analysis performed by app tasks does not count as those modules' unit tests. CI remains the full merge gate; lead/owner owns phone validation and publication.
## Voice input — 2026-10-08 — NOT TESTED ON PHONE

- Branch `feat/voice-input`, card t_e9ef0f95. Mic fills the editable request only; the user must still tap the normal task button. Permission is requested on Mic, not startup. No EQO audio storage or transcript logging.
- Standalone compilation/JUnit of the real presenter: `OK (6 tests)`, exit 0 (denial, absent recognizer, grant/result, empty results, errors/retry, cancelled-session late callbacks).
- Android/Robolectric tests added but NOT RUN. Full Gradle lint/static analysis/unit tests/APK build NOT RUN: other worktrees' Gradle wrappers remained busy after two five-minute waits. Standalone ktlint CLI fallback failed with missing Clikt (exit 1).
- Phone checks remaining: grant/deny/cancel/permanent denial, recognized text review/edit with no task submission, no recognizer explanation, no-match/network/audio errors, device language, leaving the screen stops recognition. No phone was contacted or modified by this task.

Device: Realme RM10, Android 11, serial <DEVICE_SERIAL>. Owner pastes his own OpenRouter key; it is never read or recorded here.
Rule: start `adb logcat -s EqoRun EqoActions` into a file before the owner tests; record each test below with the build commit.

## 2026-10-06

| Time (IST) | Build | Request / action | Result | Evidence | Follow-up |
|---|---|---|---|---|---|
| ~15:43 | PR #74 head 3133f6a (CI APK) | Typed "Open Keep Notes and search for opencode", Plan | "Could not reach the model" twice. Phone network fine (ping openrouter.ai ~57 ms). Cause not found; later plans worked after the owner re-entered the key and re-enabled accessibility. | screenshots, ping | none; likely accessibility/key state after reinstall |
| ~15:48 | 3133f6a | Same request, plan approved | Plan shown correctly (OPEN_APP "Keep Notes", WAIT, TYPE_TEXT "Search your notes", PRESS_ENTER). Step 1 OPEN_APP failed (`execution_failed`) twice, 1.4 s then 0.1 s. Reason not logged in that build. | EqoRun log | added short failure reason to EqoRun (commit c2dd4e4) |
| ~22:11 | PR #79 test build 375590c (#74 + #78) | Same request | Step 1 OPEN_APP succeeded and Keep opened. Step 3 TYPE_TEXT failed: 15 retries "node_found=false", then PartialApply, status FAILED. Real field label is "Search Keep" (EditText, resource id com.google.android.keep:id/toolbar); planner guessed "Search your notes". | EqoRun log | fix: type into the only text input when the hint does not match (commit df7ad13); with several inputs it still refuses to guess |
| ~23:00 | df7ad13 | Owner ran the request; reported typing still failing in search fields | NOT captured (capture started 23:14; phone log rolled over). | none | live capture started |
| 23:28 | df7ad13 | Keep Notes search (captured) | OPEN_APP ok, WAIT ok, TYPE_TEXT found the "Search Keep" EditText and reported action_accepted in 24 ms, then USER_TAKEOVER latched 52 ms later and the run paused. The bar opens a separate search page, so the text never reached the real field. | eqorun-live.log 23:28:00 | fix af0ace9: wait 700 ms, check the focused field, retype into it if empty; 600 ms grace for EQO's own touch signals |
| 23:29 | df7ad13 | SEND_WHATSAPP (captured) | READ_CONTACTS requested; TYPE_TEXT accepted; result UserActionRequired, then takeover latched and status FAILED. | eqorun-live.log 23:29:06-11 | WhatsApp send path needs its own check |
| 23:30 | df7ad13 | Open app + READ_MESSAGES (captured) | Completed. | eqorun-live.log 23:30:03-09 | none |
| 23:31 | df7ad13 | Keep search, second try (captured) | TYPE_TEXT NodeNotFound x15 once (screen state), then on retry accepted again and takeover latched 51-52 ms after, same pattern as 23:28. | eqorun-live.log 23:31, 23:32 | same fix af0ace9 |
| 23:57-23:58 | af0ace9 | Open Google Meet (captured) | `reason=App 'meet' not installed` (correct: Meet is not installed on this phone). | eqorun-live.log | none |
| 01:06-01:25 | af0ace9 | Search boxes in Keep, Gmail, Zepto (captured, owner report) | TYPE_TEXT found an EditText but Android rejected set-text and paste (`ActionRejected`), at 01:06, 01:07, 01:21, 01:25. Typing worked in Flipkart search, a message field and a new-mail compose field. | eqorun-live.log; owner report | commit 3350a47: rejected first attempt now gets the confirm-and-retype pass; log carries widget id and flags |
| 01:09 | af0ace9 | Open a URL | OPEN_URL succeeded, run COMPLETED. | eqorun-live.log | none |
| 01:18-01:20 | af0ace9 | Gmail flow: OPEN_APP, WAIT, READ_EMAILS, then a tap | READ_EMAILS ok; the following tap_text step NodeNotFound x16, run FAILED. | eqorun-live.log | needs the plan text to see the target; planner/step diagnostics |
| 01:22 | af0ace9 | Send email | SEND_EMAIL succeeded (draft opened). | eqorun-live.log | none |
| 01:30 | af0ace9 | "Open calculator and find 2345678 * 8765432" | OPEN_APP, WAIT, CALCULATE all Success. CALCULATE evaluates inside EQO and never types into the Calculator app. Owner reports the Calculator showed "238 * 800", not the requested numbers; the plan text and result line were not captured. | eqorun-live.log | open: find where "238 * 800" came from (plan param vs display); decide whether Calculator should be driven by taps or the answer shown in EQO |
Owner summary 2026-10-07 ~01:30: opening apps, sending SMS/WhatsApp messages and calls work; typing fails in some search boxes (Notes, Gmail, Zepto).
| 02:01-02:03 | af0ace9 (CI) | Keep search via debug plan (no key) | Passed: CLICK_TEXT toolbar, TYPE_TEXT into `search_actionbar_query_text`, PRESS_ENTER, COMPLETED. | eqorun-live.log | debug plan receiver added (debug builds only) |
| 02:03 | af0ace9 | Gmail search via debug plan | First TYPE_TEXT hit `open_search` (ActionRejected), retype landed in `open_search_view_edit_text`; COMPLETED. | eqorun-live.log | none |
| 09:18 | local b360a56 | Zepto search | Search is a Button with a text child, no EditText: tap-then-type fallback worked; COMPLETED. | eqorun-live.log | fix b360a56 + step-budget ordering |
| 09:20 | local | Calculator keys (12 x 3 =) | Passed with real labels (digits, Multiply, Equals, Clear). Earlier "238 x 800" was leftover state in the Calculator, not typed by EQO. | eqorun-live.log; screenshot | planner hints added (fbf4fbb) |
| 11:14-11:17 | 21de750 (#74+#78+#77+#81) | LIST_MACROS, DETECT_ROUTINES, READ_NOTIFICATIONS via debug plan | All three Success (notification content not verified; access not granted). | eqorun-live.log | #81 needs notification-access check |
| 11:52 | 21de750 | BROWSER_NAVIGATE https://example.com, consent allowed, Chrome flag + command-line file set | Chrome launched with the DevTools socket present, but EQO reported "Android did not allow EQO to connect to Chrome's DevTools socket". Consent was first reset by a stray test tap (lead error). | screenshot; /proc/net/unix | #78 not merged as working; needs adb-forward through the helper |
| 12:15 | 21de750 | Wireless pairing (split screen) | PAIR, CONNECT, HELPER_START passed; AUTHORIZE failed (`StepFailed`), BINDER_HEALTH not run. Pairing screen says "Paired and connected"; hub row "Needs attention". Cause: Shizuku.checkSelfPermission never granted, no prompt exists. Pinned-key risk resolved (first connect matches on Realme). | eqorun-live.log 12:31, 12:34 | card t_91b2a02e (helper authorization prompt) |
Merged to main 2026-10-07: #74 (65a884f), #77 (29bad15). Open: #78 parked, #81 awaiting rebase, UX #83 in progress.

## Known failing (as of df7ad13)
- Typing into some apps' search fields (Keep reported by owner after the sole-input fix; cause unknown until captured).
- Contact search inside apps, WhatsApp voice calls, MAKE_CALL permission: not yet fixed.

## Not tested on a phone yet
- Wireless pairing (#77), Chrome control (#78), notifications/macros/routines (#81), voice input.
- Files and attachments (`feat/files-attachments`): All files access row, FIND_FILES / LIST_FILES, `attachment` on email / WhatsApp / SMS, screenshots into `Pictures/EQO`.

## One-step wireless pairing — t_681e8ea6

NOT TESTED ON PHONE. Local-only branch `feat/one-step-pairing`; lead/owner owns device testing and publication.

- Implemented: own-Wi-Fi/own-phone mDNS port discovery; 10-second manual fallback; bounded foreground discovery; EQO RemoteInput pairing-code reply; short numbered guidance and Developer options button.
- Security: existing pinned connect/enrollment path retained. Reply runs only PAIR + CONNECT, never helper start/AUTHORIZE/BINDER_HEALTH or activation completion. Return to EQO and tap Connect again, then a human must tap Allow in EQO's existing protected consent dialog. No accessibility interaction with Settings or reading of its code.
- Verified code commit: `0baad6f5751f849709e20e024ffbf44b7d6ee97a` (base `a647b26f6a79b5e1681ec25088fd2b64ff0d74d8`). Later handoff-only commits do not change the tested code. All changes are committed locally; no push or PR.
- Full host gate from `android/`: `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`, exit **0**. Real output: `BUILD SUCCESSFUL in 12m 36s` and `485 actionable tasks: 189 executed, 296 up-to-date`. JUnit XML totals across nine modules: **961 tests, 0 failures, 0 errors, 1 skipped** (960 passed). The existing skip is `ModelDownloadSchedulingTest`'s pre-Android-Q case, unreachable under minSdk 30. App: 176 tests, no failures/errors/skips. Debug APK exists at `android/app/build/outputs/apk/debug/app-debug.apk` (59,953,858 bytes).
- Focused wireless/source-guard checks: `:app:detekt :app:testDebugUnitTest --tests 'ai.eqo.onboarding.Wireless*Test' --tests 'ai.eqo.study.StudyFlowGateTest' --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`, exit **0**, real output `BUILD SUCCESSFUL in 51s`; 32 tests, no failures/errors/skips. Includes fake NSD discovery/resolve callbacks on API 30/33, actual ten-second Handler timeout and late discovery recovery, wrong-host/wrong-network filtering, lost-service and stale-resolution invalidation, strict code parsing, consumed RemoteInput envelopes, explicit notification routing/stale-token rejection, and a fake runner that throws if helper start/authorization is attempted.
- Repo checks: `bash scripts/check.sh`, exit **0**, real output `kt files: 425; provenance rows: 425`, `BRANDING GATE PASSED`, `OK`. `git diff --check` exits 0. The source guard now permits only the activity and its isolated notification service to construct the pinned runner; it also rejects helper hooks or activation completion in the notification service.
- Verification caveats: the initial build retried a failing Kotlin daemon; using in-process compilation avoided it without changing project configuration. The first full gate hit a timeout in the untouched `AutomationExecutorsTest` macro delete/list case in `actions-android`; one full-gate retry passed all 95 tests in that module without edits. Phone tests and Android device instrumentation were **not run**. Notification permission/channel denial, real mDNS visibility and OEM Settings behavior still need lead/owner confirmation.
- Required phone checks: Android 11 and 13+ local mDNS announcements, reply with leading-zero code while the pairing dialog remains open, success followed by human helper authorization, wrong/expired code, revocation/key mismatch, notification permission denied/channel blocked, wireless debugging off, ten-second discovery timeout with manual ports, service lost/new dialog, Wi-Fi disconnected/switched, screen rotation and five-minute discovery expiry. No real codes, ports or device identifiers belong in this log.

## Phone round 1 on main 92802e7 (2026-10-09, Realme RM10, Android 11, local debug build, accessibility on)

Capture: `adb logcat -s EqoRun EqoActions` into `eqorun-live.log` (started 09:15, before the tests). Times are IST. Owner ran the tests by typing requests; contact names and numbers are masked here.

| Time | Test | Result (owner report + log) | Cause found | Fix |
|---|---|---|---|---|
| 09:30-09:48 | 1. "add a note called Test in Keep" | FAIL: nothing typed in Keep. Log: `ASK_USER NeedsInput` (09:30), `ADD_NOTE Failure` (09:32, 09:48), planner repair "ADD_NOTE: missing content". | The planner picked `ADD_NOTE`, which is EQO's own internal memory note (`ProductivityMemoryActions.addNote`), not Google Keep, and it fails when only a title is given. | Planner hint: when the user names an app (Keep), plan open_app + type steps; `ADD_NOTE` only for "remember this" notes. |
| 09:50-09:51 | 2. Zepto search | PASS for search (OPEN_APP, WAIT, TYPE_TEXT after tap, PRESS_ENTER, COMPLETED). "Add item" FAIL: plan stopped after the search. | The plan was made before the results screen was visible; nothing plans the Add tap. | Needs re-plan on the new screen (planned) and/or vision locate. |
| 09:52-09:57 | 3. "what is 238 times 8" | PARTIAL: right answer, but via a Google search and not the Calculator app; owner saw a notice. Log: `CALCULATE UserActionRequired` then OPEN_URL + GET_SCREEN_TEXT. | `CALCULATE` could not parse the words "times" and fell back to a web search. | Normalise words (times, plus, minus, divided by) before evaluating; prefer the built-in result. |
| (earlier) | 4. "open Flipkart" | PASS: not-installed notice, Chrome opened. | n/a | n/a |
| 10:00, 10:05, 10:23 | 5/6. WhatsApp message to a saved contact | PASS for sending (`SEND_WHATSAPP Success`). FAIL on intent: when the request said only to type the message, EQO still pressed Send; a plain "message" defaulted to WhatsApp. | `SEND_WHATSAPP` always presses Send; no type-only route; no default channel rule. | Add a draft-only WhatsApp route and planner rule: "type"/"draft"/"don't send" never presses Send; ask once which channel for a bare "message". |
| 10:03, 10:24 | SMS to a saved contact | PASS as designed: text window opened with the right message (`SEND_SMS UserActionRequired` = draft, user sends). | n/a | n/a |
| 10:08 | 7. Phone call to the test number | PASS: `MAKE_CALL Success`, permissions granted. | n/a | n/a |
| 10:08-10:09 | 8. WhatsApp voice call (3 attempts) | Log shows `WHATSAPP_CALL Success` x3. Owner has not yet confirmed what rang. | n/a | Owner to confirm. |
| 10:19 | 9. Email draft, one recipient | PASS (`SEND_EMAIL Success`). | n/a | n/a |
| (10:2x) | Email with a second recipient | FAIL: "no matching contact usable number or email was found". | Multiple recipients in one `to` are looked up as a single name. | Split recipients on comma / "and" / ";", accept typed addresses without a contact lookup. |
| 10:28-10:33 | 10/11. Share contact by WhatsApp | FIRST TRY showed "Needs you" (`SHARE_CONTACT UserActionRequired`, then two `ASK_USER NeedsInput`); SECOND TRY (rephrased) PASS (`SHARE_CONTACT Success`, text typed into the right chat). | First try needed the Contacts permission tap; the screen only said "Needs you". | Say what is needed ("Tap Allow for Contacts"), and don't ask the model to ask the user when a contact is clear. |

Not yet tested in round 1: 12 share location, 13-16 files and screenshots, 17 voice, 18-19 pairing, 20 controls.

### Round 1, second batch (2026-10-09, 10:38 onwards, same build 92802e7)

| Time | Test | Result (owner report + log) | Cause found | Fix |
|---|---|---|---|---|
| 10:38 (x2) | 12. Share location by WhatsApp | FAIL. Permission dialog appeared, then the run closed; the second try said it could not finish. Log attempt 1: `SHARE_LOCATION` requested READ_CONTACTS and ACCESS_FINE_LOCATION, then `takeover=USER_TAKEOVER`, status PAUSED, FAILED. Attempt 2: `Interrupted apply_interrupted_effect_unknown` exactly 5 s after start. | The owner's tap on the system permission dialog was counted as a takeover; the permission wait times out after about 5 s; READ_CONTACTS was requested although a typed number needs no lookup. Location permission was granted "only this time" (appops/dumpsys: ONE_TIME). | Card t_7a91b291 item 1. |
| 11:38-11:41 (x3) | 16. Screenshot | FAIL: `TAKE_SCREENSHOT Failure`, instantly, no reason shown. | `MANAGE_EXTERNAL_STORAGE` (All files access) not granted on the phone (`appops get`: no operations, default mode); possibly also a missing accessibility screenshot flag (to be checked). The failure carries no plain reason. | Card item 2. |
| 11:39 | 14. Find a file in Downloads | FAIL: `FIND_FILES Failure`, instantly. | Same: All files access not granted. | Card item 2. |
| 11:44-11:46 | file and WhatsApp-the-file requests | Planner returned an invalid structure once (repair attempted), then a network error with automatic retry. | Planner output shape for the new attachment parameters; transient network. | Watch; no fix yet. |
| ~11:30 | 18. Wireless pairing screen | FAIL: no EQO Allow prompt; the screen kept saying EQO is searching for the port, with no end. | Not yet known. The manual-port fallback is meant to appear after 10 s and did not (or was not noticed). The device log buffer holds no pairing lines. Needs a controlled run with the pairing dialog open. | To be diagnosed with the owner at the phone. |
| ~11:45 | 17. Voice input | PARTIAL: works some of the time. Owner asks for a separate (non-device) speech model, e.g. through OpenRouter. | Details of "partial" not yet captured (which phrases or languages). Android's built-in recogniser quality varies by language and phone. | To be investigated; OpenRouter audio input is an option. |

### Round 1, third batch (2026-10-09, 12:16 onwards; All files access switched on at ~12:28)

| Time | Test | Result (owner report + log) | Cause found | Fix |
|---|---|---|---|---|
| 12:16-12:22 | Find file / screenshot, All files access OFF | FAIL (`FIND_FILES Failure`, `TAKE_SCREENSHOT Failure`, instantly). | Permission not granted (the special-access page is not in the normal Permissions list). | Card t_7a91b291 item 2 (plain message + opens the page). |
| 12:23 | Open the Files app, then the eBay bill | First attempt `CLICK_TEXT NodeNotFound` x17, FAILED. A later attempt (12:32) PASSED: OPEN_APP, WAIT, FIND_FILES Success, CLICK_TEXT accepted, COMPLETED. Owner: "executed it correctly". | First attempt tapped a label that was not on screen. | Watch. |
| 12:28-12:31 | Attach a file to email / WhatsApp, All files access ON | FAIL: `FIND_FILES Success` then `SEND_EMAIL Failure` (12:28), `SEND_WHATSAPP Failure` (12:30, 12:31). Owner: asked for the 7 October screenshot to a friend by WhatsApp, "couldn't do that". | Design flaw: the planner must write a literal file path before the run, and cannot know the path FIND_FILES will find; `AttachmentSpec` accepts only a path or `last_screenshot`. So "send the <file>" can never work as built. | Card t_ecfe91de: `find:` references resolved at run time (name, type, date), chooser for several matches. |
| 12:31 | List/find files | PASS (`FIND_FILES Success`, `LIST_FILES Success`, COMPLETED). | n/a | n/a |

### Round 2 on main 732751e (2026-10-09 evening; Explain screen, edge handle, pairing, voice)

Capture note: the live log capture was not running between 12:36 and 19:07; reports in that window rely on the owner's account only. Capture restarted 19:07, tag `EqoExplain` added at ~20:00.

| Time | Test | Result (owner report + device evidence) | Cause found | Fix |
|---|---|---|---|---|
| ~19:40 | Wireless pairing, Find ports, pair box open | FAIL: screen stayed on "searching/not paired"; no notification; split screen not working on this phone. | Device log: `NsdManager.discoverServices` works and reports both services (pairing service appears when the pair box opens), but every `resolveService` fails with `NsdService: id N for SERVICE_RESOLVED has no client mapping` (Realme/ColorOS Android 11), so EQO never receives a port. | Card t_9a512691: self-contained mDNS resolver, one-string fallback `CODE PAIRPORT CONNECTPORT`, debug-only PAIR receiver. My earlier owner steps wrongly omitted "tap Find ports first". |
| ~20:00 | Explain screen, first use (Calculator) | PASS with issues: opened from the notification action; consent shown; read-aloud PASS; follow-up question PASS. The result sheet covers most of the screen. First read-out was meaningless ("calculator display"). | Sheet is full-width, 40% text area plus controls, opaque; the first answer used thin screen text. | Card t_e84b3eaa: compact translucent panel, see-through button. |
| ~20:00 | Explain screen, second use | FAIL: could not reopen; notification no longer in the shade; Quick Settings tile never added. | `dumpsys activity services`: only EQOAccessibilityService alive, `ExplainNotificationService` not running although pref `notification=true`; ColorOS stops background foreground services. Tile needs manual add on Android 11 with no in-app guidance. | Card t_e84b3eaa: handle shortcut, accessibility-service re-posts notification, accessibility button, tile guidance, battery row. |
| ~20:10 | Edge handle | FAIL: no handle visible. | No handle preference exists on the device: the switch (behind a small button on the main screen) was never turned on. `dumpsys window` shows no handle window. | Card t_e84b3eaa: switch in Setup hub, draws immediately, first-time hint. |
| ~20:10 | Voice v1 | FAIL: listens for under 2 seconds. | Android recogniser default silence timeout. | Card t_e9801e01 (voice v2). |

### Round 2, edge handle after switching it on (2026-10-09, ~21:30, build 732751e)

| Test | Result | Cause / note | Fix |
|---|---|---|---|
| Edge handle visible and draggable | PASS: owner saw it and moved it. Preference `handle_enabled=true` confirmed on the device. | The earlier "no handle" was the switch never being turned on: the screen holding it (button "Edge handle shortcuts") is on `MainActivity`, a second launcher entry; the normal EQO icon opens the Setup hub, which has no handle switch. | Card t_e84b3eaa: switch in Setup hub. |
| Panel: Open EQO | PASS (goes to the main screen). | n/a | n/a |
| Panel: Ask EQO | FAIL: opens the practice-run (sample task) screen. | Wrong target screen. | Card t_e84b3eaa (comment added): open the real task screen with the request box focused. |
| Panel: Hide for this app | PARTIAL: hid the handle for EQO itself, no way to bring it back; handle then not seen on EQO screens although enabled. | No "hidden apps" list. | Card t_e84b3eaa (comment added): Hidden apps list with Unhide; never hide inside EQO's own settings. |
| Panel: Pause / Stop | Not yet tested. | n/a | n/a |

### Round 3 on main 9716dbd (2026-10-10, 08:55-09:05, first test round of the combined build)

Capture running (`EqoRun`, `EqoActions`, `EqoExplain`). Results are the owner's account plus the log lines noted.

| Test | Result | Evidence / cause | Fix |
|---|---|---|---|
| Setup hub, Edge handle switch | PASS: switch present; the handle was already enabled from earlier; off and on both worked. | n/a | n/a |
| Handle > Ask EQO (from Gmail) | PASS: opens the real task screen with the request box ready. | n/a | Redesign wanted: a compact in-place panel. |
| Handle > Explain screen | PARTIAL. Works and reopens (log: `entry source=edge_handle`, `opened`, `explain: text, ok`, `closed`, twice). But the answer shows raw markdown symbols (`##`, `**`), reads like the page text, and Read aloud speaks the symbols and cannot be paused or stopped. | Model answer is not rendered or stripped for speech; no stop control on the speech. | Card: render markdown, strip for TTS, Pause/Stop, plainer short explanation. |
| Handle disappears briefly | BUG: after closing the panel the handle was not drawn until the owner switched to another app and came back. | Handle is not redrawn after the panel closes. | Same card. |
| Handle: Ask EQO vs Open EQO | UX: two near-identical entries. | Ask EQO goes to the full task screen, Open EQO to the main screen. | Same card: Ask EQO becomes a compact in-place panel; Open EQO stays as "Full EQO" plus a "Back to previous app" option. |
| Task from Gmail: "reply to this mail" | UNCLEAR to the owner ("nothing happened"). Log 09:01: `OPEN_APP`, `GET_SCREEN_TEXT`, tap `reply_button`, `TYPE_TEXT` (two NodeNotFound retries, then success), tap `send`, `COMPLETED`. | The run did type and press Send; the owner saw no result (the run screen is behind the app, with no visible progress). | Same card: live progress inside the compact panel. Owner to check Gmail's Sent folder. |
