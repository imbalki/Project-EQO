# Phase One phone test log

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
