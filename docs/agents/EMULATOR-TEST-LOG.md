# EQO emulator test log (round 20, Android 15 emulator)

Test date: 2026-10-20 (round started 15:06 IST; interrupted 15:48-16:12 by an emulator graphics failure; the lead
restarted the emulator at 16:12 with hardware-GPU settings and testing resumed on that instance; the emulator
degraded again after ~17:30, ending model-planned cases).
Device: Android 15 emulator (AVD eqo35). Build: 0.1.0 (1) installed on the emulator.
Plan: docs/agents/EMULATOR-TEST-PLAN.md. Cases were driven by hand through the UI (adb taps, uiautomator dumps and
screenshots, all scoped to emulator-5554; the phone was never addressed).
The AI provider key is saved on the emulator (Set up EQO shows "AI provider key (OpenRouter) — Ready") with the free
model minimax/minimax-m3.1-flash:free. Outbound actions (Send, call, pay, order) were never executed: plan-only,
preview then Reject. Nothing was force-stopped, no third-party apps were installed, and no device serials, phone
numbers, emails or key values appear in this file.

Verdicts: PASS, FAIL (with log lines and reproduction), BLOCKED (what blocked it), NOT TESTED (why).
"model" below means the free planning model; its plan quality is reported separately from app bugs at the end.

Environment notes that affected testing (not EQO bugs):
- The emulator's graphics pipeline (gfxstream host composer) saturated at ~80% kernel CPU several times, freezing
  input for tens of seconds and producing "System UI isn't responding", twice "EQO isn't responding" and finally
  "Process system isn't responding" (reason logged: "Input dispatching timed out (Application does not have a
  focused window)"; host load 3-5.5). After the second EQO ANR the process was ended via the ANR dialog and
  restarted cleanly by Android (am_kill "user request after error", then a clean relaunch; this doubles as an
  accidental process-death check, see I4).
- During the degraded window the emulator's DNS flapped: 8 planner calls failed with `planner code=NETWORK
  cause=UnknownHostException`, while ping worked again minutes later. Model-planned cases were blocked by this.
- uiautomator dumps do not include EQO's accessibility-overlay windows (edge handle and its panel), so the handle
  panel was verified via window metrics (dumpsys window) and pixel analysis of screenshots instead.

## A. Real-user behaviour

| Case | Request or steps | Result | What the screen showed / log lines |
| --- | --- | --- | --- |
| A1 typos and casing | "OPEN settings" | PASS | Instant plan: This plan: open "Settings" only / EQO will: 1. open "Settings". Case-insensitive. |
| A1 typos | "opn settings" | PASS | Plan for Settings appeared immediately; a verb typo does not break open-app. |
| A1 typos (app name) | "open calculater" | FAIL (message clarity; model quality) | No Calculator app is installed on the emulator, so the request fell through to the model; state line: "The model did not return a supported plan. Try a simpler request. Nothing ran." Log: `W EqoRun: planner code=UNKNOWN cause=UnsupportedOperationException`. Safe, but it does not say "app not installed". |
| A2 vague | "do something with my notes" | NOT TESTED | Both attempts hit the emulator graphics freeze/ANR window (15:47-15:56); no valid observation. |
| A3 polite and long | "hey could you please quickly open the calendar for me thanks" | NOT TESTED | Same emulator freeze window. |
| A4 mixed language | "kal subah 7 baje alarm" | NOT TESTED | Not reached (model cases were blocked later by emulator network flaps). |
| A5 same request twice | "open the settings" twice in a row | PASS | The second identical request produced the same plan again; no state carry-over error. |
| A6 change of mind: Plan, Reject, new request | Plan "open the settings", Reject, then a new request | PASS | Reject returns to the task screen; a new request plans normally. Nothing ran after Reject. |
| A7 Stop mid-run; Pause then Resume; Take over | 2- and 3-step open plans approved, then Pause/Stop | NOT TESTED | open-app runs complete in ~3-4 s (steps + 3 s waits); the emulator's input latency made it impossible to reach the Pause/Stop buttons mid-run before completion. |
| A8 press Home mid-run, return to EQO | HOME during a running 2-step plan | PASS | The run continued in the background (TaskRunService) and completed: returning to EQO showed the Clock already opened by step 2 and the result line "OPEN APP: Requested app launched." No crash. |
| A9 rotate during the plan dialog and during a run | Rotate 90° and back while the plan dialog was open | PASS | The dialog survived rotation with the plan text and Reject/Approve intact both ways ("This plan: open "Settings" only..." before and after). |
| A10 Back at every screen | Main → task → Set up EQO hub → Back | PARTIAL | Back from the setup hub returned to the task screen correctly. A further Back from the task screen could not be verified (the emulator degraded at that moment). Settings/handle-settings backs not reached. |
| A11 dark mode and large font | `cmd uimode night yes` + font_scale 1.3, then main/task screens | PASS (partial) | EQO re-rendered main and task screens in dark mode at 1.3x font with no crash (text reads normally in dumps); a plan dialog in that mode could not be captured (emulator lag). Restored to light/1.0 afterwards. |
| A12 airplane mode | (pending) | NOT TESTED | Not reached; the emulator's own network flaps gave an accidental (partial) view of the offline behaviour, see I6. |
| A13 rapid double/triple tap on Plan | Three fast taps on Plan for "open the clock" | PASS (with a note) | Only one plan was produced and one run executed ("OPEN APP — Done / Requested app launched."); no double run, no crash. Note: the extra taps landed on the open dialog area, so a fast second tap can hit Approve — same as a fast human, but worth knowing. |
| A14 500 characters and emoji | Long request with emoji typed into the task field | NOT TESTED | The emulator degraded mid-input (`input text` itself crashed on the emoji — an Android shell limitation, not EQO); no EQO observation. |
| A15 empty and whitespace-only request | Tap Plan with an empty field; then with "   " | PASS | Both show the plain state line "Type a request first."; no crash, no model call. |
| A16 an app that is not installed | "open flombulator" | FAIL (message clarity) | State line: "The model did not return a supported plan. Try a simpler request. Nothing ran." (log: `W EqoRun: planner code=UNKNOWN cause=UnsupportedOperationException`). Nothing ran, but a missing app should produce a plainer message (for example "Flombulator is not installed"). |
| A17 ambiguous app ("browser") | (pending) | NOT TESTED | Blocked by the model/network window. |
| A18 prompt-injection try | (pending) | NOT TESTED | Blocked by the model/network window. |
| A19 something EQO cannot do | (pending) | NOT TESTED | Blocked by the model/network window. |

## B. Core flows

| Case | Request or steps | Result | What the screen showed / log lines |
| --- | --- | --- | --- |
| B1 open apps: Settings, Clock, Calendar, Contacts, Chrome, Camera, Files | Plan then Approve each | PASS (7/7) | Each produced "This plan: open <app> only" and approving opened the right app (verified by window focus). Log per run: `action=OPEN_APP result=Success`, `step=N outcome=Completed`, `status=COMPLETED`. |
| B2 open app with a typo | "opn calculater" | FAIL (see A1) | The free model could not map the double typo; the app message is plain but unhelpful. |
| B3 browser choice: This time / Always, then forget | (pending) | NOT TESTED | Blocked by the model/network window. |
| B4 open a URL in a chosen browser | (pending) | NOT TESTED | Blocked by the model/network window. |
| B5 web search with the exact query | "search for the weather today in chrome" | PASS (preview) | Plan: "search the web for the weather today in Chrome"; rejected without running. |
| B6 calculate | "what is 15 percent of 240" | PASS (preview) | Plan: `calculate "15 percent of 240" = 36 (local preview answer)` — correct local math preview; rejected without running. |
| B7 time and date | "what time is it" | PASS (preview) | Plan: "read the current time from the device clock"; rejected without running. |
| B8 flashlight (plan only) | (pending) | NOT TESTED | Blocked by the model/network window. |
| B9 volume, brightness, ringer, DND (plan) | (pending) | NOT TESTED | Blocked by the model/network window. |
| B10 wifi, bluetooth, mobile data, hotspot (plan only, Reject) | (pending) | NOT TESTED | Blocked by the model/network window. |
| B11 timer for 2 minutes | "set a timer for 2 minutes" | BLOCKED | Model unreachable (`planner code=NETWORK cause=UnknownHostException` x8 during 17:14-17:30); app showed "Could not reach the model. Check your internet connection and try Plan again. Nothing ran." |
| B12 alarm at 6:30 (plan only) | "set an alarm at 630" | BLOCKED | Same model/network block. |
| B13 reminder with date and time variants | "remind me to call the dentist tomorrow at 9" | BLOCKED | Same model/network block (retried once after a 60 s wait per the lead's instruction; still unreachable). |
| B14 calendar event; list calendar | "what is on my calendar today" | BLOCKED | Same model/network block. |
| B15 notes | (pending) | NOT TESTED | Blocked by the model/network window. |
| B16 clipboard | (pending) | NOT TESTED | Blocked by the model/network window. |
| B17 screenshot (action and handle button) | (pending) | NOT TESTED | The handle panel could not be used (auto-closes, see H1); the plan-based path needed the model. |
| B18 take photo (plan only) | (pending) | NOT TESTED | Blocked by the model/network window. |

## C. Information and knowledge

| Case | Request or steps | Result | What the screen showed / log lines |
| --- | --- | --- | --- |
| C1-C11 (weather, news, define, translate, convert, summarize, fact check, stock/flight/traffic, briefing, memory, sensitive info) | (pending) | BLOCKED | All are model-planned; the model was unreachable during the window available (see B11). |

## D. Files and sharing

| Case | Request or steps | Result | What the screen showed / log lines |
| --- | --- | --- | --- |
| D1-D5 | (pending) | NOT TESTED | Not reached before the emulator degraded. |

## E. Messaging and calls (plan only, Reject)

| Case | Request or steps | Result | What the screen showed / log lines |
| --- | --- | --- | --- |
| E1-E12 | (pending) | NOT TESTED | Blocked by the model/network window. |

## F. Apps, shopping and money (plan only)

| Case | Request or steps | Result | What the screen showed / log lines |
| --- | --- | --- | --- |
| F1-F7 | (pending) | NOT TESTED | Blocked by the model/network window. |

## G. Automation features

| Case | Request or steps | Result | What the screen showed / log lines |
| --- | --- | --- | --- |
| G1-G7 | (pending) | NOT TESTED | Blocked by the model/network window. |

## H. Edge handle and voice

| Case | Request or steps | Result | What the screen showed / log lines |
| --- | --- | --- | --- |
| H1 handle visible on home; tap opens the icon panel (icons only, names on long-press) | Tap the handle on the launcher | FAIL (panel closes itself) | The handle overlay is present on the home screen (window "EQO edge handle", 126x189 px at the right edge). A tap opens the panel (the window grows to 630x651 px showing an icon grid; pixel analysis shows a 2-column grid of icon glyphs and no large text buttons). BUT the panel closes itself about 2.5-3 s after opening with no further input (reproduced 3/3: "Requested w=630" at t+2 s, back to "Requested w=126 h=189" at t+3 s). A user cannot choose an icon in 2-3 s, so the panel is effectively unusable. Suspected area: the panel dismisses on an accessibility/window-change event from the launcher (at-a-glance card updates) or a stray auto-dismiss timer. Round 15 verified the panel working on the phone, so this may be emulator-timing dependent. |
| H1 icon taps | Tap icons inside the open panel | NOT TESTED (inconclusive) | Taps at glyph centres produced no window/focus change before the panel closed; with the 2-3 s auto-close the taps may have lost the race, and uiautomator cannot see the panel to confirm what was tapped. The installed build may also predate the "fire panel icon clicks through performClick" fix (that commit is not in origin/main yet). |
| H1 handle hidden inside EQO | Open EQO main, look for the handle | PARTIAL / needs confirmation | The "EQO edge handle" overlay window was still present while EQO MainActivity was focused. Round 15 recorded "handle hidden on EQO's own screens" as by-design; either this regressed or the design changed. Needs a check on the phone. |
| H2 Close, Hide for now, Hide for this app, Hidden apps, Show again | (pending) | NOT TESTED | The panel auto-closes before its icons can be used (see H1). |
| H3 Ask EQO, Open EQO, Explain screen from the panel | (pending) | NOT TESTED | Same panel blocker. |
| H4 Dictation into a text field | (pending) | NOT TESTED | No speech input on the emulator (plan says PHONE). |
| H5 Record: limit prompt, red dot, expand, stop, Saved bar | (pending) | NOT TESTED | Record is reached from the panel, which auto-closes (see H1). |
| H6 Screenshot button: file in Pictures/EQO and the result line | (pending) | NOT TESTED | Same panel blocker. |
| H7 Mic on the task screen: first-tap sheet, cancel | Tap Mic on the task screen | NOT TESTED (inconclusive) | Tapping task_voice_button produced no visible first-tap sheet in the UI dump; the emulator lag makes a false negative likely. Needs a recheck. |
| H8 handle over the keyboard / video / rotating / lock screen | Lock-screen variant attempted | NOT TESTED | The emulator has no keyguard (screen lock = none). Keyboard and video variants not reached. |

## I. Setup, privacy and resilience

| Case | Request or steps | Result | What the screen showed / log lines |
| --- | --- | --- | --- |
| I1 Set up EQO hub states | Open Set up EQO | PASS (with a confusion) | The hub lists each item with its own state: "AI provider key (OpenRouter) — Ready", "Screen control (Accessibility) — Ready", "Wireless debugging pairing — Not set up", "Helper connection — Not set up", "Browser control (optional) — Not set up (unavailable in this build)". Confusion: the Edge handle row says "Accessibility is off. Turn on EQO in Screen control service to show the handle." while the row below says Screen control (Accessibility) — Ready. |
| I2 open-source notices | Open Open-source notices | PASS | "Legal & open-source notices" shows build 0.1.0 (1), upstream names and licenses (OpenDroid, ClosePaw, Shizuku, Shizuku-API; Apache-2.0/MIT) and the research-use note. |
| I3 permission-denied paths | (pending) | NOT TESTED | Contacts-resolution paths need a model plan, which was unreachable. |
| I4 process death: run started, Home, swipe away, reopen | Accidental equivalent observed | PASS (accidental variant) | After the second EQO ANR the process was ended via the ANR dialog (am_kill "user request after error", 15:50) and Android relaunched EQO; the app reopened without a crash, setup state and key intact ("credential openrouter=present model=true" at 15:50:16). The planned Home+swipe-away variant was not run. |
| I5 accessibility service off | Read the hub text | PARTIAL | The hub's Edge handle row carries the off-state guidance ("Accessibility is off. Turn on EQO in Screen control service to show the handle."), which is the right idea, but it shows at the same time as "Screen control (Accessibility) — Ready" (see I1). Toggling the real service off was NOT run. |
| I6 airplane mode and flaky network during AI planning | Emulator DNS flapped during the degraded window | PASS (offline message) | Eight planner calls failed with `W EqoRun: planner code=NETWORK cause=UnknownHostException`; the screen showed the plain line "Could not reach the model. Check your internet connection and try Plan again. Nothing ran." — correct, safe wording with nothing half-run. Connectivity came back later (ping OK). |
| I7 low storage message | (pending) | NOT TESTED | Not reached. |
| I8 second user or work profile | (pending) | NOT TESTED | Not reached. |
| I9 install a new build over the old one | NOT TESTED | No new build was available to install. |
| I10 logs: no request text, transcript, audio, numbers or emails in EqoRun/EqoActions/EqoVoice | Full logcat review of the four tags (125 lines) | PASS | Lines contain only structured codes: intents ("intent kind=open_app confidence=high"), step/phase/status codes, `planner code=NETWORK cause=UnknownHostException`, `planner code=UNKNOWN cause=UnsupportedOperationException`, `action=OPEN_APP result=Failure reason=Enable EQO accessibility in Settings before continuing.`, and "credential openrouter=present model=true". Zero occurrences of any request text typed during the round; no key material, transcript, numbers or emails. |

## Bugs (numbered)

1. Edge-handle panel closes itself ~2.5-3 s after opening with no input (H1). Reproduction: from the home screen tap
   the handle, do nothing, watch `dumpsys window` "Requested w=630 h=651" become "Requested w=126 h=189" within 3 s.
   Expected: the panel stays open until dismissed. Suspected area: accessibility-overlay dismissal logic in the
   service reacting to launcher window events (or a stray auto-dismiss timer). Possibly emulator-timing dependent;
   round 15 verified the panel working on the phone — please re-verify there.
2. Missing-app and unparseable-model responses both surface as "The model did not return a supported plan. Try a
   simpler request. Nothing ran." (A16, A1 app-typo). Log: `W EqoRun: planner code=UNKNOWN cause=UnsupportedOperationException`.
   Expected: a plain "app not installed" style message when the request names a missing app; keep the model-failure
   wording for genuine model failures.
3. The Set up EQO hub shows a contradictory accessibility state (I1/I5): the Edge handle row says accessibility is
   off while the Screen control row says Ready, on the same screen at the same time. Correlated log evidence:
   one mid-run step failed with `action=OPEN_APP result=Failure reason=Enable EQO accessibility in Settings before
   continuing.` while the service was enabled and its overlay visible (during the emulator stress window; a fresh
   run right after succeeded). Worth a look at how "accessibility on" is checked.
4. Minor: the task screen keeps the previous request's preview/result lines when a new plan fails (for example the
   stale "search the web for the weather…" preview above a fresh "Could not reach the model." state), which reads
   as if the old plan is still pending.

## Feature gaps / UX confusions (works as designed but confusing)

- The main-screen hint "Next: Set up EQO before trying the sample task." stays even after key + accessibility are
  Ready, while the hub says the next missing item is Wireless debugging pairing; the hint never says which item.
- "The model did not return a supported plan" reads like an internal error to a user (see bug 2).
- Task-screen result line says "Completed — check the result on your phone." — wrong device wording on the emulator.
- The task screen keeps a stale preview from the previous request under a new failure state (see bug 4).

## Planning quality (free model minimax/minimax-m3.1-flash:free; reported separately from app bugs)

- open-app, calculate and time requests plan instantly and correctly via local intent matching (`intent kind=open_app
  confidence=high`; local calculate preview "15 percent of 240" = 36 correct).
- Requests that fall through to the model produced an unmappable plan twice (`planner code=UNKNOWN
  cause=UnsupportedOperationException`) for a simple missing-app request and for a typo'd app name.
- The same conjunction wording planned locally once ("open settings and then open the clock") and fell through to the
  (unavailable) model on a later identical attempt — matching looks inconsistent for "and then" requests.
- No paid-model comparison was possible (USD 0 credit; paid models are rejected on this key).

## Ideas for new use cases noticed while testing

- "open the settings" (article + lowercase) is a very common phrasing and works; worth keeping in tests.
- A device without a common app (Calculator on this emulator) will hit the confusing message of bug 2 often; a
  registry-level "did you mean X?" or "app not installed" would help.
- The panel auto-close (bug 1) may also affect the phone if any window event fires while it is open.
- A "run continued in background after Home" note on the task screen (A8) would help users understand that EQO kept
  going after leaving the app.

## Not verified (honest list)

Everything marked NOT TESTED or BLOCKED above: A2-A4, A7, A10 (partial), A12, A14, A17-A19, B3/B4/B8-B18,
all of C, D, E, F, G, H2-H8, I3/I7/I8/I9, and the paid-model comparison. Causes: emulator graphics/input
degradation (15:48-16:12 and after ~17:30), the emulator's DNS flap blocking all model-planned cases (17:14-17:30),
no keyguard on the emulator, no speech input on the emulator, and no second build to install.
