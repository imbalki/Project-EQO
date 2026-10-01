# EQO — User Flows (Draft)

**Companion to `PRD.md`.** Screens, flows, state machines, microcopy, ownership, and requirement traceability for the EQO **study build**.

**Status:** DRAFT. All microcopy is proposed. No flow has been implemented or tested — there is no study APK yet (`PRD.md` §1.1). Requirement IDs (REQ-*), conflict IDs (CF-*), and gate IDs (G-*) refer to `PRD.md`.

**Conventions**

- **Owner legend:** `USER` = participant acts inside an Android-owned (or Chrome-owned) settings surface; `APP` = EQO performs the check/transition automatically; `SHARED` = app guides, user acts, app re-verifies on return.
- **Check granularity (brief mandate):** pairing, connection, helper start, authorization, binder health, and per-function probes are **separate checks** with separate pass/fail. One green never implies another green.
- Screens are labeled `S-xx`; flows `UF-xx`; recovery branches `UF-Rxx`.
- Android-owned settings surfaces keep their **real OS names**; EQO never re-labels them (REQ-A11Y-03).

---

## 1. Screen inventory (with entry / exit / back behavior)

| ID | Screen | Entry | Exit / forward | Back behavior |
|---|---|---|---|---|
| S-01 | Welcome & legal | First launch | Continue → S-02 | N/A (root of first-run) |
| S-02 | What EQO sends (privacy primer) | S-01 → Continue | S-03 | Back → S-01 |
| S-03 | Setup hub (checklist of capabilities) | After first run; also from Settings | Tapping a row enters its flow; all-ready → S-20 | Back → Home (S-20) or previous screen |
| S-04 | Model setup (OpenRouter BYOK) | S-03 row "Model" | Validate → pass: S-03 (row green); fail: S-05 | Back → S-03 (key draft preserved) |
| S-05 | Model setup — failure states (5 variants) | S-04 failure | Repair action → S-04 or external provider page | Back → S-04 |
| S-06 | Accessibility setup | S-03 row "Screen control" | USER in Android settings → return → APP re-check | Back → S-03 |
| S-07 | Android 13+ restricted-settings repair card | User reports the toggle blocked in S-06 (no reliable app-side detection claimed) | USER completes path → return → S-06 re-check | Back → S-06 (step stays pending) |
| S-08 | Wireless ADB — step 1..8 checklist | S-03 row "Helper connection" (mandatory) | Each check passes → next; any fails → S-09 | Back → S-03 (completed checks persist, shown as done) |
| S-09 | ADB failure/repair state (per-check variants) | Failed check | "Show me how" → expanded instructions; re-check | Back → S-08 at the failed step |
| S-10 | Helper authorization prompt (Android-owned) | After pairing+connection pass | USER approves → S-11 | Decline → S-09 (authorization failed state) |
| S-11 | Helper health & probes (binder + per-function) | Helper start pass | All required probes pass → S-03 row green | Back → S-08 |
| S-12 | Chrome CDP — informed consent | S-03 row "Browser control" (optional capability) | Accept → S-13; Decline → S-03 row "Off (you declined)" | Back → S-03 |
| S-13 | Chrome debug preparation + restart | S-12 accept | USER completes prep/restart → APP verifies endpoint | Back → S-12 (consent persists) |
| S-14 | Chrome per-function readiness | Endpoint verified | Per-function list (read page / click / run script) each pass/fail | Back → S-13 |
| S-15 | Virtual display — compatibility list & toggle | S-03 row "Background mode" | Enable → probe; incompatible app → S-16 | Back → S-03 |
| S-16 | Foreground fallback proposal | VD failure or incompatible app | USER approves foreground → task continues in foreground | Back → task detail (task shows "needs decision") |
| S-19 | Capability readiness dashboard | S-03 header "Details" | Shows every named check with last-checked time | Back → S-03 |
| S-20 | Home / chat (task entry) | Default after setup | Send task → S-21 | Back → exits (standard) |
| S-21 | Task run (progress + step list) | Task submitted | Steps run; sensitive step → S-22; complete → S-24 | Back → S-20 (run continues; overlay persists) |
| S-22 | Sensitive-action approval card | Policy says "ask" | Approve → continues; Deny → step `Cancelled`; timeout → `Cancelled` | Back = Deny (explicit label) |
| S-23 | Control overlay (Pause / Stop / Takeover) | Floating capsule during run | Pause → `Paused`; Stop → `Stopped`; Takeover → user control | Dismiss overlay ≠ pause |
| S-24 | Result verification | Run ends (any terminal outcome) | "Looks right" → done; "Looks wrong" → issue logged, task marked needs-review | Back → Home, verification marked skipped |
| S-25 | Recovery hub (interrupted items) | Any `Interrupted` state | Per-item repair → resumes flow at failed check | Back → Home |
| S-26 | SMS composer (pre-filled draft) | Task output or "Draft SMS" | S-22 SMS confirmation card (see UF-13) — confirm then send or hand off | Back → draft saved, nothing sent |
| S-27 | Settings (model, privacy, capabilities, legal) | Home menu | Sub-pages | Back → previous |
| S-28 | Legal notices & attribution | Settings → Legal; **also** Welcome S-01 (outside normal flow, REQ-INS-03) | — | Back → origin |
| S-29 | Re-onboarding / revocation | Settings, or automatic on revocation detection | Re-enter affected flow at the failed step only | Back preserved |

---

## 2. UF-01 — First install & welcome

**Entry:** app icon tap after sideload install. **Preconditions:** device Android 12+ (REQ-INS-01).

1. **S-01 Welcome.**
   - Microcopy: *"EQO — Your Only Personal AI Assistant. EQO works by guiding you through a few Android settings, then runs tasks on your phone with your approval at every sensitive step. Nothing is set up yet — you'll do each step with us."*
   - Buttons: `Continue` · `Read legal & licenses` (→ S-28, satisfies REQ-INS-03).
2. **S-02 Privacy primer.**
   - Microcopy: *"Before we start: EQO sends only what a task needs to your chosen AI model. Your API key stays on this phone, encrypted in the Android Keystore. Screen or browser content is only shared after you approve it, and redaction is best-effort — sensitive content may still be visible to the model."* Buttons: `Continue` · `What exactly is sent?` (expands REQ-PRIV-04 copy).
3. **S-03 Setup hub** shows rows: **Model** (required), **Screen control** (required), **Helper connection** (required, wireless ADB), **Browser control** (optional), **Background mode** (optional), **SMS** (optional). Each row: `Not set up` / `Checking…` / `Ready` / `Needs attention`.
   - Microcopy for required rows: *"EQO can't run tasks until these are ready."*
4. First-run banner: *"Estimated setup time: 10–15 minutes — a placeholder estimate we'll measure and correct during the study (our study target is about 25 minutes end-to-end). You'll be in Android settings for a few steps — we'll tell you exactly what to tap."* **(Unvalidated estimate, labeled as such; must be reconciled with the US-01 ≤25-min study target after measurement — REQ not yet proven.)**

**Owner:** SHARED. **Exit:** all required rows Ready → S-20. **No permission is ever auto-granted** (REQ-A11Y-03).

---

## 3. UF-02 — Model setup (OpenRouter BYOK) + validation

**Entry:** S-03 → Model. **Owner:** SHARED.

1. Microcopy: *"Which model provider will EQO use? Paste your OpenRouter API key. It's stored on this phone using the Android Keystore and never written to logs. Heads up: using OpenRouter may cost you money on your OpenRouter account — EQO doesn't charge you, but your provider might. This cost disclosure is pending final policy sign-off (CF-10/OD-09)."* Fields: key (masked), model picker (populated after validation).
2. `Test connection` → **APP** runs validation.
   - **Pass:** *"Connected to OpenRouter. Model: <model>. You can change models any time."* → S-03 row `Ready`.
   - **Fail states (S-05), each distinct (REQ-BYOK-04):**
     - `Unauthorized (401)`: *"OpenRouter rejected this key. Check you copied the whole key."* → `Re-enter key`.
     - `Rate limited (429)`: *"Too many requests right now. Try again in <countdown>. EQO won't retry on its own."* → `Try again when ready`.
     - `Insufficient credit`: *"Your OpenRouter account has no credit. Add credit at openrouter.ai/credits, then test again."* → `Open provider page`.
     - `Incompatible model`: *"<model> doesn't support EQO's tool calls. Pick a model with tool support."* → `Choose another model`.
     - `No network`: *"No internet connection. EQO can't reach OpenRouter."* → `Try again` (user-initiated only).
3. **Reasoning/tool loop smoke (REQ-BYOK-03):** after first validation, app runs a 3-step self-test shown as *"Checking EQO can plan and use tools…"* with steps `Plan → Tool call → Verified answer`. Failure routes to S-05 with the underlying class.

**Back:** S-04 → S-03, key draft preserved. **Automated:** validation, log-scrape for key leakage (REQ-PRIV-02).

---

## 4. UF-03 — Accessibility setup (incl. Android 13+ restricted-settings repair)

**Entry:** S-03 → Screen control. **Owner:** SHARED.

1. Microcopy: *"EQO needs the Accessibility service to read and tap the screen on your behalf. In Android settings, find EQO under **Accessibility → Downloaded apps**, then turn it on."* Button: `Open accessibility settings` (APP fires the settings intent — the USER flips the toggle).
2. **Return re-check (APP):** service connected → row `Ready`. Microcopy: *"Screen control is on. You can turn it off any time in Android settings."*
3. **Repair branch (REQ-A11Y-02), API 33+:** EQO does **not** claim it can reliably detect that Android has blocked the toggle. Instead, S-06 offers *"Is the toggle greyed out or missing? Tell EQO"* — the **user reports** a blocked toggle, which opens the repair card:
   - S-07 card: *"Android may be blocking this toggle as a 'restricted setting'. This is Android's protection, not an EQO error. The usual path is: **Settings → Apps → EQO → tap the ⋮ (three dots) in the app-info screen → Allow restricted settings**. Then come back and turn on the Accessibility toggle. EQO can't do this for you."*
   - Buttons: `Open EQO's app info` · `I've done it` (re-checks accessibility state only — re-check success is observable; the blocked-toggle condition itself is user-reported, not auto-detected).
   - **OEM/device path validation: PENDING.** Exact wording/placement of "Allow restricted settings" varies by OEM and Android version and must be confirmed in the study device matrix; if a device's path differs, the facilitator records it and the card copy is corrected. Hint text includes device OEM/API in a "Device details" disclosure (REQ-ADB-11).
   - **Owner:** SHARED.
4. **Lost later (REQ-A11Y-04):** service off mid-task → run enters `Interrupted`, S-25 card: *"Screen control was turned off. The task stopped at <step>. Turn it back on, then choose Resume."* **No auto-resume** (REQ-TASK-07).

---

## 5. UF-04 — Guided wireless ADB: pairing and connection (mandatory)

**Entry:** S-03 → Helper connection. **Owner:** SHARED, but every check is separately reported (REQ-ADB-01..06, 12). The app never pretends to grant anything; it watches and verifies.

Step list shown to user as a vertical checklist, each with its own spinner/pass/fail:

| # | Check | Guidance microcopy (USER) | Verification (APP) | Failure state (S-09) |
|---|---|---|---|---|
| 1 | `developer_options` | *"Open **Settings → About phone → Build number** and tap Build number 7 times to unlock Developer options."* | Reads developer settings state | *"Developer options aren't unlocked yet. Tap Build number 7 times, then try again."* |
| 2 | `wifi_state` | *"Make sure Wi-Fi is on. Your phone and the helper use the same Wi-Fi connection."* | Wi-Fi enabled + SSID present | *"Wi-Fi is off (or not connected). Turn on Wi-Fi, then try again."* |
| 3 | `adb_wifi_enabled` | *"In **Developer options → Wireless debugging**, turn it ON."* | Wireless debugging enabled | *"Wireless debugging is off. Open Developer options → Wireless debugging and switch it on."* |
| 4 | `paired` (pairing port) | *"Tap **Pair device with pairing code**. Enter the 6-digit code and the **pairing port** shown in the pairing popup — that number is only for pairing."* | In-app pairing client completes SPAKE2 handshake to the **pairing port** with the entered code | *"Pairing failed. Check the pairing code and the pairing port — the pairing port is the small number in the pairing popup, not the one on the main screen."* |
| 5 | `connected` (connection port) | *"Now connect: on the main Wireless debugging screen, note the **IP address & port** — this port is different from the pairing port. EQO will connect to it."* | Fetches **connection port** separately; authenticated round-trip succeeds → state only then `connected` | *"Couldn't connect. The connection port is the one next to your IP address on the main Wireless debugging screen — it is not the pairing port."* |

- **Port distinction (REQ-ADB-05) is a first-class UI concept:** the two numbers are labeled *"Pairing port"* and *"Connection port"* in separate cards with an inline note: *"These are two different numbers. Android changes them between sessions."* The app never pre-fills one from the other.
- **Authenticated connection (REQ-ADB-06):** `connected` is shown only after a signed round-trip, with microcopy: *"Connected and verified."*
- **Reboot/revoke:** returning after reboot or "Revoke USB/Wi-Fi debugging authorizations" → S-09 variants `AdbAfterReboot` / `AdbRevoked` → re-run from step 4 (fresh pairing), never silently reconnect (UF-R2).

**Back:** back within this flow returns to S-08 (the ADB checklist), where passed checks persist; back to S-03 shows row `Needs attention`.

---

## 6. UF-05 — Helper start, authorization, binder health, probes

**Entry:** after UF-04 step 5. **Owner:** SHARED/AUTOMATED.

1. `helper_started` (APP): *"Starting the EQO helper…"* → fail: *"The helper didn't start. This can happen after an update or reboot. Try Again — if it keeps failing, the connection checks above are still saved."*
2. `helper_authorized` (USER): Android-owned authorization prompt appears: *"Allow EQO to use the helper? This lets EQO run the commands you approve."* Buttons are Android's, not EQO's. → Decline: *"Authorization declined. EQO can't run helper tasks until you allow it."* → `Ask again`.
3. `binder_alive` (APP, periodic): microcopy in details: *"Helper connection: healthy (checked <time>)."* Death → `BinderDead` (UF-R3). **Separate check, never merged with 1 or 2** (REQ-ADB-07..09).
4. Per-function probes (APP), each listed individually (REQ-ADB-10):
   - `probe_shell` — *"Run a harmless command (echo)"*
   - `probe_display` — *"Check display control"* (only if Background mode being enabled)
   - `probe_browser` — *"Check browser control channel"* (only if CDP enabled)
   - Result UI: *"3 of 3 checks passed"* or mixed: *"2 of 3 checks passed — display control failed"*, each line individually green/red (REQ-CDP-04 forbids generic readiness).

---

## 7. UF-06 — Chrome CDP: informed consent, debug prep, endpoint, per-function readiness

**Entry:** S-03 → Browser control (optional). **Owner:** SHARED.

1. **S-12 Consent (REQ-CDP-01, REQ-PRIV-03/04):**
   - *"Browser control lets EQO read and interact with pages in Chrome using Chrome's own debugging tools. What this means: page content and EQO's script actions are sent to your model provider for processing. Redaction is best-effort — sensitive content may still be visible to the model. EQO only starts this after you agree, and only for tasks you run."*
   - Buttons: `I understand — continue` · `Not now` · `What exactly is sent?`
   - Decline → row shows *"Browser control: Off (you declined)"*; rest of app unaffected (REQ-PRIV-03).
2. **S-13 Debug preparation (REQ-CDP-02):**
   - *"Chrome needs to allow debugging before EQO can connect. Steps for your Chrome version: <flag/setting as applicable>. EQO can't flip this for you."* → USER performs it → `Restart Chrome` button → USER confirms restart.
   - App then **verifies the debugging endpoint**: *"Checking Chrome's debugging endpoint…"* → pass: *"Endpoint verified."* / fail: *"Chrome's debugging endpoint isn't available yet. Re-check the flag above and restart Chrome."*
3. **S-14 Per-function readiness (REQ-CDP-03/04):** only after endpoint pass:
   - `read_page` · `click` · `run_script` — each shows Passed/Failed with reason. Microcopy: *"Browser control is ready — <n>/<m> functions available."* Never a single unexplained "ready".
4. Consent scope persists; a Chrome restart later re-triggers only the endpoint + function checks (not consent) unless the user revokes (UF-R5).

---

## 8. UF-07 — Virtual display & approved foreground fallback

**Entry:** S-03 → Background mode. **Owner:** SHARED.

1. Microcopy: *"Background mode lets EQO work on a separate virtual screen so you can keep using your phone. It works only for compatible apps while the helper is healthy — it is not a guarantee for every app."* (REQ-VD-03)
2. Compatibility list shown with per-app status (`Compatible` / `Not supported here`).
3. Probe fails → **S-16 (REQ-VD-01/04):** *"EQO couldn't start a virtual screen (<reason>). Run this task in the foreground instead? You'll see EQO working on your screen, and you can pause anytime."* Buttons: `Run in foreground` · `Cancel task`. **Explicit approval required; no silent background continuation** (REQ-VD-02).
4. During a foreground-fallback run, S-23 overlay shows badge: *"Foreground (background mode unavailable)"*.

---

## 9. UF-08 — Capability readiness dashboard

**Entry:** S-03 → Details. Shows every named check with last-checked timestamp and status: model validation, a11y service, the 8 ADB checks, binder health, per-function probes (display/browser), VD compatibility, SMS capability. Tap any item → re-run that single check. Microcopy: *"Each check is separate. Green here means exactly what it says — nothing else."*

**Owner:** APP (re-verify), with `Re-check` USER trigger. Satisfies REQ-ADB-10, REQ-CDP-04.

---

## 10. UF-09 — Normal task run (progress, loop, minimal context)

**Entry:** S-20 → task submitted. **Owner:** APP, with USER checkpoints.

1. S-21 shows the plan: steps with names and live states `Pending / Running / Done / Failed` + elapsed time (REQ-TASK-01).
   - Microcopy during run: *"Working on it — step 2 of 4: opening Maps."*
2. Model requests tool calls → tool states `Validating → Scheduled → (AwaitingApproval) → Executing → Success/Error/Cancelled`.
3. Sensitive steps pause for S-22 (UF-10). Model context is minimized per task (REQ-PRIV-01); details screen shows *"What the model sees for this step: <screen summary>"* — honest about redaction limits.
4. Completion → S-24 verification (UF-11). Overlay S-23 remains available the whole run.

---

## 11. UF-10 — Approvals, Pause, Stop, manual takeover

### 11.1 Sensitive-action approval (S-22, REQ-TASK-02)

- Card microcopy: *"<App> wants to send a message to <recipient>: \"<content>\". Approve this?"* Buttons: `Approve` · `Deny` · countdown chip *"Auto-denies in 60s"* (OD-05; proposal matches ClosePaw `APPROVAL_TIMEOUT_MS = 60_000`).
- **Timeout/Deny → step `Cancelled`** with microcopy: *"Denied — EQO didn't send anything. The task will ask you how to continue."* **Never auto-approves.**
- TOCTOU honesty: if the foreground app changed while waiting: *"The screen changed while waiting for your answer, so EQO cancelled that step."* (mirrors ClosePaw `sources/closepaw/doc/main/state_machines/tool_call.md` "App changed during approval wait").

### 11.2 Pause vs Stop vs Takeover (S-23, REQ-TASK-03/05)

| Action | Microcopy on button | Semantics | Resulting state |
|---|---|---|---|
| **Pause** | `Pause` — *"Pause after the current step finishes"* | Cooperative **quiescent** point: while Paused, EQO performs **no automated reads, no clicks/taps, and no model calls**; task context preserved | `Taking over…` → `Paused` ("Paused. Nothing is running — EQO is not reading your screen or calling the model. Resume when you're ready.") |
| **Takeover** | `Take over` — *"You drive; EQO holds"* | User controls device; EQO **holds without observation by default**. Observation (EQO watching the screen while you drive) happens **only with separately consented opt-in** captured at takeover time or earlier in settings; without it, EQO does not read the screen during takeover | `Paused (you're driving)` — with badge `Observing (you allowed it)` or `Not observing` |
| **Stop** | `Stop` — *"Stop this task completely"* | Cancellation, context discarded after summary | `Stopped` + S-24 with receipts |

- **In-flight uncertainty (REQ-TASK-04):** if a step may have half-executed: *"This step may have partially run — EQO isn't sure if it went through. Check <app> before resuming."* state `UnknownResult`. **Never reports success it didn't verify.**
- Pause ≠ cancellation is stated in UI: paused chips read *"Paused — steps preserved"*; stopped chips read *"Stopped — task ended"*.

---

## 12. UF-11 — Result verification & model-error recovery (soft)

**Entry:** run terminal state → S-24. **Owner:** SHARED.

1. Microcopy: *"Done — here's what happened: <step receipts/artifacts>. Verify: did it do what you asked?"* Buttons: `Looks right` · `Looks wrong` (logs issue, marks task `Needs review`, REQ-TASK-06).
2. Model-error branches (REQ-REC-01..05) arrive as S-25 cards with the exact class from PRD §7.10 and a single next action; **user-initiated retry only** (REQ-TASK-07): e.g. `ModelError: RateLimited` → *"Too many requests. EQO won't retry on its own — try again in <t> or Stop."*

---

## 13. UF-12 — Recovery branches (hard failures)

All branches end in an **explicit state + repair path + fresh user resume**; none auto-resume, none auto-retry irreversible actions (REQ-TASK-07). **Resume guard (all branches):** Resume requires (a) the relevant readiness checks re-run green, (b) the current screen/app re-verified against what the task expects, and (c) a **fresh contextual approval** for any sensitive action not yet provably completed — approvals consumed before the failure are never carried over, and `UnknownResult` steps are verified by the user rather than replayed.

| UF-R | Fault (REQ) | State shown | Microcopy (repair) | Resume rule |
|---|---|---|---|---|
| UF-R1 | API 401/429/credit/incompatible/network (REQ-REC-01..05) | `ModelError:*` | Class list and repair actions: UF-11 (this document) and PRD §7.10 | On Resume: **re-verify target/content/current screen, then obtain a fresh contextual approval for any sensitive action** — a consumed approval is never retained. Unknown-result actions are **never blindly replayed**; they re-run only after user verification. |
| UF-R2 | ADB disconnection / revoke / reboot (REQ-REC-06) | `AdbDisconnected` / `AdbRevoked` / `AdbAfterReboot` | *"Your phone's wireless debugging connection dropped. Reconnect with the pairing steps — Android may show a new pairing code."* → S-08 | **Fresh resume + renewed approval** for any pending sensitive step |
| UF-R3 | Helper binder death (REQ-REC-07) | `BinderDead` | *"The helper stopped responding. EQO will restart its checks."* → S-11 (helper start, authorization if needed, probes) | Task `Interrupted`; user resumes |
| UF-R4 | Accessibility lost (REQ-REC-08) | `A11yLost` | See UF-03 step 4 | User re-enables → explicit Resume |
| UF-R5 | Chrome debug unavailable (REQ-REC-09) | `DevToolsUnavailable` | *"Chrome's debugging endpoint went away (Chrome may have restarted). EQO will re-check."* → S-13 endpoint | Consent not re-required (scope unchanged); functions re-probed |
| UF-R6 | Virtual display failure (REQ-REC-10) | `VirtualDisplayFailed` | S-16 foreground fallback, approval required | Approved fallback or cancel |
| UF-R7 | Helper/authorization revoked by user in settings | `HelperRevoked` | *"You turned off <setting>. EQO paused. Turn it back on when you want to continue."* → relevant S-0x check only | Renewed approval for sensitive steps |
| UF-R8 | App update kills helper (known upstream: server exits on manager APK change, `reports/helper-integration.md` §2.6) | `HelperNeedsRestart` | *"EQO was updated, so the helper needs a quick restart."* → S-11 | User-initiated |

**Re-onboarding (S-29):** any revoked capability re-enters **only its own step** in S-03; completed steps stay green until their own re-check fails (mirrors ClosePaw `firstIncompleteStep` re-validation invariant, `sources/closepaw/doc/main/state_machines/onboarding_wizard.md`).

---

## 14. UF-13 — SMS: compose-first, conditional send

**Entry:** task *"text <person> I'll be late"* → S-26. **Owner:** SHARED.

1. **Compose (REQ-SMS-01):** EQO opens the composer pre-filled with recipient + body. Microcopy: *"Draft ready — nothing is sent yet."* Buttons: `Send…` · `Edit` · `Hand off to my messaging app` (REQ-SMS-04 fallback).
2. **Confirm (REQ-SMS-02):** `Send…` → confirmation card:
   - *"Send this SMS? To: <full number/name>. Message: \"<full body>\". This will use your carrier plan."* Buttons: `Send now` · `Cancel`.
   - Cancel → *"Nothing sent. Your draft is saved."*
3. **Permission/policy compatibility (REQ-SMS-04):** if `SEND_SMS` unavailable/denied or policy-restricted: *"EQO can't send SMS directly on this phone. Here's the draft — open it in your messaging app and send it yourself."* → hand-off intent.
4. **No inbox reading (REQ-SMS-03):** no screen, flow, or permission references inbox access; manifest gate G-05 asserts `READ_SMS`/`RECEIVE_SMS` absent (removing upstream OpenDroid declarations — conflict CF-08).

---

## 15. UF-14 — Legal notices & attribution (outside normal flow)

- **Entry 1:** S-01 Welcome → `Read legal & licenses`.
- **Entry 2:** S-27 Settings → Legal (≥2 taps from Home; REQ-INS-03 requires ≤3 taps from any main screen).
- Content: EQO notices, third-party attribution (OpenDroid — Apache-2.0; ClosePaw — Apache-2.0; Shizuku & Shizuku-API — Apache-2.0/MIT with upstream terms as applicable), open-source license texts. Works offline.
- Microcopy: *"Open-source licenses & notices — always available here, even offline."*

---

## 16. State transitions

### 16.1 Task/session states (adapted from ClosePaw `sources/closepaw/doc/main/state_machines/session_state.md` — [VERIFIED] source model)

| From | To | Trigger | Guard |
|---|---|---|---|
| `Idle` | `Running` | User submits task | Platform checks pass |
| `Running` | `AwaitingApproval` | Sensitive action requested | Policy = ask |
| `AwaitingApproval` | `Running` | Approve | TOCTOU checks pass (foreground app unchanged & not blocked) |
| `AwaitingApproval` | `Cancelled(step)` | Deny **or timeout** | — |
| `Running` | `TakingOver` | Pause tapped | cooperative pause requested |
| `TakingOver` | `Paused` | pause confirmed | context preserved; resume allowed |
| `Paused` | `Running` | Resume tapped (explicit) | **never automatic** |
| `Running`/`TakingOver` | `Stopping` | Stop tapped | cancel signals |
| `Stopping` | `Stopped` | cancellation completes | receipts generated |
| `Running` | `Interrupted` | Recovery fault (UF-R*) | repair required; **no auto-resume** |
| `Interrupted` | `UnknownResult` | possible partial execution | user must verify |
| `Interrupted`/`UnknownResult` | `Running` | user Resume | Guard: required checks re-run green **and** current screen/app matches task expectation **and** fresh approval obtained for any sensitive action (consumed approvals never reused; `UnknownResult` never blindly replayed) |
| `Running` | `Completed` | all steps done | verification screen follows |
| `Completed` | `Verified` / `NeedsReview` | user confirmation | S-24 |

### 16.2 Setup-hub row states

`NotSetUp → Checking… → Ready | NeedsAttention(reason) → (re-check) …`. Revocation flips the affected row to `NeedsAttention` **without** touching **unrelated** rows (REQ-ADB-12 per-check isolation) — but **dependent readiness is invalidated transitively**: a helper/ADB failure (`helper_started`, `binder_alive`, probes) marks **Browser control (CDP)** and **Background mode (VD)** as `Dependent — re-check needed`, because both depend on the helper. Model and Accessibility rows stay as they were. Recovering the helper re-runs the dependent probes before those rows can return to `Ready`.

### 16.3 Tool-call states (from ClosePaw `sources/closepaw/doc/main/state_machines/tool_call.md` — [VERIFIED])

`Validating → Scheduled → AwaitingApproval → Executing → Success | Error | Cancelled`
Notable guards EQO keeps: approval timeout → `Cancelled("timed out")`; foreground change during wait → `Cancelled`; policy deny → `Cancelled`; missing approval package → fails closed.

---

## 17. Requirement traceability matrix

| Flow | Screens | Requirements covered |
|---|---|---|
| UF-01 | S-01, S-02, S-03 | REQ-INS-01/02/03, REQ-PRIV-03/04 |
| UF-02 | S-04, S-05 | REQ-BYOK-01/02/03/04, REQ-PRIV-02 |
| UF-03 | S-06, S-07, S-25 | REQ-A11Y-01/02/03/04 |
| UF-04 | S-08, S-09 | REQ-ADB-01..06, 11, 12 |
| UF-05 | S-10, S-11 | REQ-ADB-07/08/09/10 |
| UF-06 | S-12, S-13, S-14 | REQ-CDP-01/02/03/04, REQ-PRIV-03/04 |
| UF-07 | S-15, S-16 | REQ-VD-01/02/03/04 |
| UF-08 | S-19 | REQ-ADB-10, REQ-CDP-04 |
| UF-09 | S-20, S-21, S-23 | REQ-TASK-01, REQ-PRIV-01, REQ-BYOK-03 |
| UF-10 | S-22, S-23 | REQ-TASK-02/03/04/05 |
| UF-11 | S-24, S-25 | REQ-TASK-06/07, REQ-REC-01..05 |
| UF-12 | S-25, S-29, S-08/S-11/S-13/S-16 | REQ-REC-06..10, REQ-TASK-07, REQ-A11Y-04 |
| UF-13 | S-26 | REQ-SMS-01/02/03/04 |
| UF-14 | S-28, S-01 | REQ-INS-03 |

**Manual vs automated ownership summary:** USER-only = all Android-owned settings toggles, pairing-code entry, helper authorization prompt, Chrome debug prep/restart, consent decisions, SMS final confirm. APP-only = all state checks, endpoint verification, probes, binder health polling, log hygiene, state transitions. SHARED = guided steps (guide → user acts → app re-verifies): a11y enable, restricted-settings repair, ADB pair/connect, helper start/authorize/probe, foreground fallback approval, resume-after-recovery.

---

## 18. Microcopy principles (binding for G-06)

1. **No blame, no magic:** failures explain *who owns the fix* (Android / Chrome / EQO / you) — *"This is Android's protection, not an EQO error."*
2. **Never claim granted permissions:** always *"Open settings → find EQO → turn on"* (REQ-A11Y-03).
3. **Never generic readiness:** readiness is always per-function with counts (REQ-CDP-04, REQ-ADB-10).
4. **Two ports, two names:** "pairing port" and "connection port" everywhere, never "the port" (REQ-ADB-05).
5. **Honest uncertainty:** *"EQO isn't sure"* over false success (REQ-TASK-04).
6. **Pause/Stop wording is literal:** "pause after the current step" vs "stop completely" (REQ-TASK-03).
7. **Redaction limits stated in plain language** wherever screen/browser context is enabled (REQ-PRIV-04).
8. Android and Chrome settings keep their **OS names verbatim**.

---

*Draft end. No source under `sources/` was modified; no implementation, build, or test was performed for this document.*
