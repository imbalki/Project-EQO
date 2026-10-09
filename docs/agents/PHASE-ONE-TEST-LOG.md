# Phase One phone test log

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
