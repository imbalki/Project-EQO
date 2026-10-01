# EQO — Product Requirements Document (Draft)

**Tagline:** EQO — Your Only Personal AI Assistant.

**Document status:** DRAFT for study planning. Not a production spec, not a claim of current capability.
**Author role:** Product/UX lead, independent Hermes CLI worker.
**Date:** 2026-10-01
**Scope of this file:** product status, source hierarchy, conflict ledger, scope/non-goals, personas, capability model, requirements with proposed acceptance criteria, architecture hypotheses, safety/privacy, risk register, study protocol, gates, open decisions.

Companion document: `USER-FLOWS.md` (screens, flows, state machines, microcopy, traceability). Requirement IDs defined here are traced there.

---

## 0. How to read this document (evidence discipline)

Every claim carries one of three labels:

| Label | Meaning |
|---|---|
| **[VERIFIED]** | Observed in local source files or evidence artifacts at the cited path. Static inspection only — no build, no test, no device run was performed by this author. |
| **[PROPOSED]** | Design decision or acceptance criterion proposed by this draft. Not implemented, not agreed by engineering. |
| **[UNPROVEN GATE]** | A capability that must be demonstrated (via engineering prototype on hardware) before the participant study APK gate passes and has NOT been demonstrated. No test results exist for it. |

**No test results are claimed anywhere in this document.** Where CI status appears, it is quoted from `evidence.json` as an observation about the upstream repos, not about EQO.

---

## 1. Product status

### 1.1 What exists today

| Asset | Status | Evidence |
|---|---|---|
| Project-EQO monorepo (`imbalki/Project-EQO`) | **Scaffold only.** `android/`, `desktop/`, `server/`, `packages/shared/` are README placeholders. Main CI's `repo-checks` job runs `scripts/check.sh`, which does not exist; the android job is commented out and targets a non-existent Gradle project. | [VERIFIED] `reports/qa-build-audit.md` §1, §4 F1–F2; `sources/main/android/README.md:1-3`; `sources/main/.github/workflows/ci.yml:16-27` |
| EQO PRD in `sources/main/docs/PRODUCT.md` | Placeholder: "Move the full PRD here (currently a Claude Doc)." | [VERIFIED] `sources/main/docs/PRODUCT.md:3` |
| OpenDroid (`yashab-cyber/opendroid`) | Real Kotlin/Compose agent app: Gradle 9.7.0, AGP 9.3.1, Kotlin 2.4.0, Hilt, `:app` module, 16 screens, agent loop, 12 LLM providers incl. OpenRouter, accessibility automation, Keystore credential storage. `minSdk 26`, `applicationId "com.opendroid.aiagent"`. | [VERIFIED] `sources/opendroid/README.md:52-192`; `sources/opendroid/app/build.gradle:11,16`; `reports/qa-build-audit.md` §1–§2 |
| ClosePaw (`imoonkey/closepaw`) | Real Kotlin agent harness: ReAct loop, accessibility platform + virtual display platform (Shizuku), `browser_script` (Chrome CDP, needs Chrome + Shizuku), BYOK LLM settings, Smart Capsule overlay with pause/takeover/stop, per-app approval policy, hard-blocked banking/authenticator/crypto apps, onboarding wizard state machines. `minSdk 31`, `targetSdk 36`, Apache-2.0. | [VERIFIED] `sources/closepaw/README.md:19-125`; `sources/closepaw/doc/main/state_machines/*`; `sources/closepaw/app/build.gradle.kts:22` |
| Shizuku (`RikkaApps/Shizuku` + Shizuku-API) | Full manager/server/starter stack inspected statically. Bootstrap refuses to run unless uid is 0 or 2000; server hard-exits unless package `moe.shizuku.privileged.api` is installed; server exits if the manager APK path changes. | [VERIFIED] `reports/helper-integration.md` §2.2, §2.6 |
| Study evidence harness | `evidence.json`, `ci-detail.json`, `lint-evidence.json`, `reports/` exist. `adb devices` returned an empty device list — **no device was attached during evidence collection.** | [VERIFIED] `evidence.json` (`adb_devices.stdout = "List of devices attached\n\n"`) |
| EQO study APK | **Not observed in this workspace.** No EQO build, install, or run is recorded in the audited artifacts. | [VERIFIED workspace observation from the audit only] no APK artifacts present under this workspace; this is not an exhaustive verification of all possible build outputs elsewhere |

### 1.2 What this document is for

This PRD specifies the **study build**: a single sideload APK used for a small, instrumented usability/feasibility study. It deliberately separates what is proven (source inspection) from what must be proven (feasibility gates) from what is merely proposed (UX requirements).

---

## 2. Source hierarchy (order of precedence)

1. **LATEST APPROVED BRIEF** (delivered in the assignment, recorded verbatim-in-substance in §3). Overrides all older docs for product direction.
2. **Local evidence artifacts** (`evidence.json`, `ci-detail.json`, `lint-evidence.json`, `reports/*.md`) — for facts about repo state.
3. **Source repositories** (`sources/main`, `sources/opendroid`, `sources/closepaw`, `sources/shizuku`) — for facts about code behavior; cited with path and line.
4. **Old main-repo docs** (`sources/main/docs/PRODUCT.md`, `ARCHITECTURE.md`, `ROADMAP.md`, `sources/main/AGENTS.md`, `sources/main/agents/android/context/STACK.md`) — **lowest precedence, partially superseded.** Retained for conflict record only.

Rule: when (1) and (4) disagree, the brief wins and the conflict is logged in §3. **No source file was modified by this draft.**

---

## 3. Conflict ledger (brief vs. old main docs)

Recorded, not resolved in the sources. Sources left untouched as instructed.

| ID | Old constraint (with evidence) | Latest approved decision | Resolution status |
|---|---|---|---|
| CF-01 | Offline-default: "Cloud is opt-in. Default path must work with no network." (`sources/main/AGENTS.md:22`); "Local LLM: Gemma E4B via LiteRT" (`sources/main/agents/android/context/STACK.md:10`) | Model path is **OpenRouter BYOK** with setup validation and a reasoning/tool loop. Offline-on-device Gemma is no longer the default path. | Brief wins. Open question: is any offline fallback retained? See OD-01 (§11). |
| CF-02 | "App integrations go through Composio's hosted HTTP API." (`sources/main/AGENTS.md:23`; `STACK.md:12`) | Tools come from **ClosePaw's integrated tool set** on an **OpenDroid** base; **Composio is not the selected study integration**. No universal-automation promise. | **Partially resolved — not an explicit prohibition.** The latest brief does not explicitly forbid Composio or all paid services; Composio is simply not selected for the study build and would need explicit approval to be added later (see CF-10). |
| CF-03 | Roadmap phases: Phase 1 Gemma/OpenRouter/Composio/SMS/Room/voice; Phase 3 "Screen control via accessibility service" (`sources/main/docs/ROADMAP.md`) | Accessibility is a **first-study setup requirement**, not a Phase-3 item. Guided wireless ADB + helper authorization are in-scope for the study APK. | Brief wins; roadmap effectively superseded for study scope. |
| CF-04 | Min SDK 29 (Android 10) "confirm before locking" (`STACK.md:6`) | **Android 12+ (API 31+)**. Note OpenDroid base ships `minSdk 26`. | Brief wins. Study must raise/verify minSdk on the OpenDroid base — engineering action, not yet done. [UNPROVEN] |
| CF-05 | Positioning: "Tiers: free phone-only, standard with cloud VM, self-hosted" (`sources/main/docs/PRODUCT.md:6`); cloud VM tier in Roadmap Phase 4 | Study scope is **phone-only sideload**. No cloud VM tier in study. | Brief wins for study; tiers deferred (OD-08). |
| CF-06 | "Phase 1 is pure Kotlin. No embedded Node.js, no Python on the phone." (`sources/main/AGENTS.md:20`) | Single EQO APK, **setup helper INCLUDED**, no external branded helper install. ClosePaw ships a Termux Python bridge (`com.termux.permission.RUN_COMMAND`) — see C6 of `reports/qa-build-audit.md`. | **Tension recorded, not resolved.** Brief says no *separate external branded helper*; it does not license a Python/Termux runtime on the phone. `termux_shell` tool must be out of study scope unless explicitly approved (OD-04). |
| CF-07 | Branding: EQO-branded single APK, no external branded helper | Shizuku upstream **forbids** using `Shizuku` as app name, `moe.shizuku.privileged.api` as application id, or declaring `moe.shizuku.manager.permission.*` (stated as Apache 2.0 §6 terms in `sources/shizuku/README.md:81-85`), while the server hard-requires package `moe.shizuku.privileged.api` (`reports/helper-integration.md` §2.2) | **Open legal/technical conflict.** Cannot both rebrand to EQO identity and satisfy the server's hardcoded package check without a proven integration strategy. Escalate before any participant-facing APK work (G-03, OD-02); engineering prototypes are allowed to prove feasibility. |
| CF-08 | SMS: OpenDroid manifest requests `SEND_SMS`, `READ_SMS`, `RECEIVE_SMS` (`sources/opendroid/app/src/main/AndroidManifest.xml:13-15`) | **SMS compose first; sending only with explicit recipient/content confirmation and compatible permission/policy; no inbox-reading promises.** | Brief wins. `READ_SMS`/`RECEIVE_SMS` must not be requested in the study APK (REQ-SMS-03). |
| CF-09 | Voice: mic button, no wake word (`sources/main/docs/ARCHITECTURE.md:15`); OpenDroid ships offline wake word (`sources/opendroid/README.md:97-99`) | Brief is silent on voice/wake word. | Unresolved — see OD-06. Not a study gate. |
| CF-10 | "Do not add anything that requires a paid subscription." (`sources/main/AGENTS.md:10`); project free/open-source-only norm | OpenRouter BYOK is the model path; **OpenRouter credits may cost the user real money.** | **Unresolved conflict** — the brief neither explicitly lifts the old constraint nor forbids paid API usage. Interim handling: disclose cost explicitly in UF-02 before BYOK setup; no paid tooling/subscriptions added to the product itself; final decision = OD-09. |

---

## 4. Scope and non-goals

### 4.1 In scope (study build)

- Sideload APK for **Android 12+**, single **EQO-branded** artifact, setup helper **bundled inside** the APK (no second download).
- OpenDroid as the selected base app; ClosePaw tool set (agent loop, approvals, capsule-style control UI, tool policies) integrated.
- **OpenRouter BYOK** onboarding with live setup validation and a reasoning/tool loop.
- **Accessibility** onboarding, including **Android 13+ restricted-settings repair guided manually through Android-owned settings** (OS names shown verbatim to the user).
- **Mandatory guided wireless ADB** onboarding: Developer Options → Wi-Fi → Wireless debugging → pairing (pairing code, **pairing port ≠ connection port**) → authenticated connection → helper start → authorization → binder health → per-function probes, each as a **separate, individually failing check**.
- **Chrome CDP** capability: explicit informed consent, debug preparation/flag as applicable, Chrome restart, verified debugging endpoint, then per-function readiness.
- **Virtual display** for compatible apps only, with an **explicit, approved foreground fallback**.
- Task execution UX: progress, contextual sensitive-action approvals, Pause, Stop, manual takeover, result verification.
- Recovery UX for the enumerated failure classes, with fresh-resume and renewed-approval semantics.
- SMS **compose** (and optional send under conditions); legal notices/attribution reachable outside the main flow.

### 4.2 Non-goals (explicit)

- No production release, no Play Store listing, no signing/distribution pipeline work.
- No implementation, build, install, or publish performed as part of this document.
- No universal automation promise; no claim that EQO "just works" across OEMs.
- No automatic granting of permissions — the app **guides**; the **user** grants, in Android-owned settings.
- No inbox reading; no silent SMS sending; no automatic irreversible retries; no automatic resume after recovery.
- No cloud VM tier in study. Composio is **not the selected study integration** (CF-02) and would require explicit approval to adopt later. **Paid services — unresolved conflict (CF-10):** the old open-source/free-only constraint (`sources/main/AGENTS.md:10`) versus the brief's OpenRouter BYOK is recorded, not silently overridden; **OpenRouter usage may cost the participant real money and must be disclosed explicitly** before BYOK setup (trace: UF-02). The latest brief does not explicitly forbid Composio or all paid services. License metadata: Shizuku-API is MIT, ClosePaw/OpenDroid/Shizuku are Apache-2.0 ([VERIFIED] `evidence.json`).
- No telemetry/analytics claims beyond what sources state (ClosePaw: "No telemetry, no third-party analytics" — `sources/closepaw/README.md`).

---

## 5. Personas and jobs-to-be-done

| ID | Persona | Context | Job to be done | Frustration to avoid |
|---|---|---|---|---|
| P1 | **Curious adopter** (primary study participant) | Daily Android 12–15 phone, non-rooted, no ADB experience | "Set EQO up once and have it finish real tasks on my phone without me babysitting every step." | Dropped into OS settings with no explanation; unclear why something failed |
| P2 | **Privacy-conscious tinkerer** | Comfortable with developer options, wary of cloud | "Use my own OpenRouter key, know exactly what leaves the phone, and keep secrets out of logs." | Keys stored in plaintext; screen contents sent without consent |
| P3 | **Interrupted operator** | Tasks run while commuting / handing phone to a child | "Pause or stop EQO the moment I need the phone back, and know exactly what did / didn't happen." | Silent continuation; ambiguous state after a crash; auto-resume |
| P4 | **Study facilitator** (internal) | Runs sessions, collects metrics | "See per-step outcomes and requirement traceability without instrumenting the participant." | Having to infer which check failed |

Study personas map 1:1 to usability scenarios in §9.

---

## 6. Capability model

Capabilities are tiered by dependency. Each tier has an evidence status.

| Tier | Capability | Depends on | Evidence status |
|---|---|---|---|
| C0 | Chat with model, BYOK key handling, settings, memory off | OpenRouter key | Base app capability [VERIFIED present in OpenDroid/ClosePaw source] |
| C1 | **Reasoning/tool loop** (plan → act → verify → replan) | C0 | [VERIFIED] agent loop exists in both bases (`sources/opendroid/README.md:52-60`; `sources/closepaw/README.md` "ReAct loop, no external orchestrator"). EQO integration [PROPOSED]. |
| C2 | **Accessibility-driven device control** | User grants accessibility service; Android 13+ restricted-settings repair | [VERIFIED] accessibility services exist in both bases. Restricted-settings repair flow [PROPOSED — no source evidence found in either base]. |
| C3 | **Wireless ADB pairing + authenticated connection** | Developer Options, Wi-Fi, user enters pairing code | [VERIFIED partial] Shizuku has full pairing client (`AdbPairingClient`, SPAKE2, Keystore-encrypted ADB key — `reports/helper-integration.md` §2.5); ClosePaw has its own wireless ADB stack (`sources/closepaw/app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/`, incl. `ProcNetTcpListeners.kt:16` pairing-port discovery). **In-EQO integration untested; no device attached during evidence collection.** |
| C4 | **Helper start + authorization + binder health** | C3 | [UNPROVEN GATE] Feasibility only statically inspected (`reports/helper-integration.md` states: "Nothing was implemented, executed, built, or tested"). Bootstrap uid gate, hardcoded package identity, no persistence across app updates are known obstacles. |
| C5 | **Chrome CDP (browser_script)** | C4 + Chrome debug prep + restart + consent | [VERIFIED source] ClosePaw `browser_script` "Needs Chrome + Shizuku" (`sources/closepaw/README.md:62,114`). EQO guided flow [PROPOSED]; per-function readiness [UNPROVEN GATE]. |
| C6 | **Virtual display (background)** | C4 | [VERIFIED source] `VirtualDisplayPlatform` exists (Shizuku-gated) with a documented stale-state race in `VdLifecycleArbiter.kt:85-87,114-125` (`reports/qa-build-audit.md` F9). No universal background guarantee [BRIEF]. |
| C7 | **SMS compose / conditional send** | Runtime SMS permission + policy compatibility | [VERIFIED] OpenDroid declares SMS permissions; EQO must narrow to compose-first (CF-08). Send path [PROPOSED, conditional]. |

Rule: **no capability is advertised to the participant until its readiness check passes**, and readiness is never inferred generically — it is a named, per-function probe (see `USER-FLOWS.md` UF-08).

---

## 7. Requirements and proposed measurable acceptance criteria

Format: `REQ-<AREA>-<NN>` — Requirement — **Acceptance criterion (proposed, measurable)** — Trace: flow ID in `USER-FLOWS.md` — Ownership: Manual (user does it in OS/Chrome UI) / Automated (app verifies) / Shared.

### 7.1 Install & first run

| ID | Requirement | Proposed acceptance criterion | Trace | Owner |
|---|---|---|---|---|
| REQ-INS-01 | Single EQO APK installable by sideload on Android 12+ | On ≥3 distinct OEM/API-31..35 devices, `adb install` (or package-installer tap) succeeds; app reaches Welcome in ≤60s cold start median | UF-01 | Automated (device farm/manual matrix) |
| REQ-INS-02 | No second app to install | Install flow never references an external branded helper package; setup helper components resolve from within the EQO package | UF-01 | Automated (manifest/package assertion) |
| REQ-INS-03 | Legal notices & attribution reachable outside normal flow | From any main screen, "Legal & licenses" is reachable in ≤3 taps; content renders offline | UF-01, UF-14 | Manual + automated tap-path test |

### 7.2 BYOK / model setup

| ID | Requirement | Proposed acceptance criterion | Trace | Owner |
|---|---|---|---|---|
| REQ-BYOK-01 | OpenRouter key entry with live setup validation | Invalid key → explicit `401`-style failure state shown ≤5s after submit, with recovery action; valid key → "Connected" state with model list loaded | UF-02 | Automated (mock + live) |
| REQ-BYOK-02 | Key stored in Android Keystore, never in logs | Code/log inspection finds no key material in logcat, crash reports, or persisted plaintext; storage uses Keystore-backed encryption | UF-02, UF-13 | Automated (log scrape + storage audit) |
| REQ-BYOK-03 | Reasoning/tool loop demonstrable | Given a 3-step task, model issues ≥1 tool call, receives result, and produces a verified final answer; loop turns ≤ configured max with visible progress | UF-09 | Automated (instrumented run) |
| REQ-BYOK-04 | Recovery: 401 / 429 / insufficient credit / incompatible model / network error | Each of the 5 classes maps to a distinct, named state with a distinct repair action; no state auto-retries an irreversible action | UF-11 | Automated (fault injection) |

### 7.3 Accessibility setup

| ID | Requirement | Proposed acceptance criterion | Trace | Owner |
|---|---|---|---|---|
| REQ-A11Y-01 | Guided accessibility enablement | Participant enables the EQO accessibility service without facilitator intervention in ≥80% of first attempts (study metric) | UF-03 | Manual (user in Android settings) |
| REQ-A11Y-02 | Android 13+ restricted-settings repair, manual, Android-owned settings | On API 33+ where the **user reports** the toggle blocked (EQO does not claim reliable programmatic detection), the repair card names the usual Android-owned path: **Settings → Apps → EQO → app-info overflow (⋮ three dots) → Allow restricted settings**, then re-attempt the Accessibility toggle; no other path is asserted. Path wording/validity per OEM and Android version is **validation pending** (study matrix must confirm or correct per device). App re-checks accessibility state automatically on return; blocked-toggle reporting is user-initiated. | UF-03 | Shared |
| REQ-A11Y-03 | App never claims to grant permissions | Copy audit: no string asserts EQO granted a permission; all permission copy uses "Open settings → find EQO → turn on" framing | UF-03, UF-14 | Manual (copy review) |
| REQ-A11Y-04 | Accessibility loss detection | Service disabled mid-task → task enters `Interrupted` within ≤5s, user sees repair card, **no automatic resume** | UF-12 | Automated |

### 7.4 Guided wireless ADB (mandatory)

Each check is **separate**; a pass on one never implies a pass on another.

| ID | Requirement | Proposed acceptance criterion | Trace | Owner |
|---|---|---|---|---|
| REQ-ADB-01 | Developer Options enabled | Check `developer_options` reports ON; if OFF, guide (7-tap Build number) with OEM-specific hint | UF-04 | Manual + Automated |
| REQ-ADB-02 | Device on Wi-Fi (same network semantics apply) | Check `wifi_state` ON and SSID present; failure → named repair state | UF-04 | Shared |
| REQ-ADB-03 | Wireless debugging ON | Check `adb_wifi_enabled` ON (cf. `BootCompleteReceiver` writes `adb_wifi_enabled` — `reports/helper-integration.md` §2.5) | UF-04 | Shared |
| REQ-ADB-04 | **Pairing uses pairing code + pairing port** | UI shows the Android-provided pairing code field and pairing port; pairing attempt to wrong port fails and re-prompts; success yields a distinct `paired` state | UF-04 | Automated |
| REQ-ADB-05 | **Connection port ≠ pairing port** | Connection step fetches and uses the *connection* port shown under the "IP address & port" section; UI labels the two ports distinctly and never pre-fills one from the other | UF-04 | Automated |
| REQ-ADB-06 | Actual authenticated connection | A real authenticated session is established (not just discovery): a signed round-trip succeeds; state `connected` only after that round-trip | UF-04 | Automated |
| REQ-ADB-07 | Helper start is its own check | `helper_started` check passes independently of `connected`; failure → distinct error ("helper did not start") | UF-05 | Automated |
| REQ-ADB-08 | Authorization is its own check | `helper_authorized` check (user approves the authorization prompt) is separate from start | UF-05 | Manual + Automated |
| REQ-ADB-09 | Binder health is its own check | `binder_alive` probe runs periodically while any ADB-dependent capability is enabled; death → `Interrupted`, not silent success | UF-05, UF-12 | Automated |
| REQ-ADB-10 | Per-function probes | `probe_shell`, `probe_display`, `probe_browser` (as applicable) each report pass/fail individually; a mixed result shows mixed state | UF-08 | Automated |
| REQ-ADB-11 | OEM/version variation | On each matrix device, every failure state shows a device-specific hint (OEM + API level shown in a "Device details" disclosure); ≥1 deliberate OEM variation exercised in study | UF-04, UF-12 | Shared |
| REQ-ADB-12 | Missing prerequisites → explicit failure/repair | Every check has exactly one of {pass, fail+repair action}; no check may end in "unknown" without an explanatory reason string | UF-04 | Automated |

### 7.5 Chrome CDP

| ID | Requirement | Proposed acceptance criterion | Trace | Owner |
|---|---|---|---|---|
| REQ-CDP-01 | Explicit informed consent before any screen/context transmission | Consent screen states what is sent (screen content/JS context), to where, and that redaction is best-effort; task cannot start until accepted or declined | UF-06 | Manual |
| REQ-CDP-02 | Debug preparation/flag as applicable + Chrome restart | Flow performs the required preparation (flag/setting as applicable to the device/Chrome version), then requires a Chrome restart; restart is user-confirmed | UF-06 | Shared |
| REQ-CDP-03 | Verified debugging endpoint, then per-function readiness | Endpoint verification is a named check (e.g. `devtools_endpoint` reachable); only after pass do `read_page` / `click` / `script` probes run, each with its own pass/fail | UF-06, UF-08 | Automated |
| REQ-CDP-04 | No generic "ready" | The UI never shows "Chrome ready" as a single boolean; it shows the endpoint check plus per-function results | UF-06, UF-08 | Manual (UX review) + automated |

### 7.6 Virtual display

| ID | Requirement | Proposed acceptance criterion | Trace | Owner |
|---|---|---|---|---|
| REQ-VD-01 | Compatible apps only | Compatibility list is explicit per app; an unlisted app triggers "Not supported here — run in foreground instead" | UF-07 | Automated + Manual |
| REQ-VD-02 | Explicit approved foreground fallback | When VD fails or app is incompatible, the app proposes foreground mode and requires explicit approval; never silently continues in background | UF-07, UF-12 | Manual |
| REQ-VD-03 | No universal background guarantee | Copy and capability page state: "Background mode works only for compatible apps while the helper is healthy" | UF-07 | Manual (copy review) |
| REQ-VD-04 | VD failure is a named state | Injected VD creation failure → `VirtualDisplayFailed` with fallback offer within ≤2s; no task auto-continues unattended | UF-12 | Automated |

### 7.7 Task execution, approvals, control

| ID | Requirement | Proposed acceptance criterion | Trace | Owner |
|---|---|---|---|---|
| REQ-TASK-01 | Task progress visible per step | Each step shows name, live state (pending/running/done/failed), and elapsed time; ≥1 visual progress surface always on screen during a run | UF-09 | Automated |
| REQ-TASK-02 | Contextual approval for sensitive actions | Approval card shows action, target (e.g. recipient), and app in context; `AwaitingApproval` times out to `Cancelled` (ClosePaw uses 60s: `tool_call.md` `APPROVAL_TIMEOUT_MS = 60_000`) — EQO proposes 60s for study, surfaced to user as countdown | UF-10 | Automated |
| REQ-TASK-03 | Pause ≠ Stop | Pause reaches a confirmed quiescent point (ClosePaw model: `TakeoverPending → Paused`), preserving task context; Stop cancels the run. Distinct states in UI and logs. | UF-10 | Automated |
| REQ-TASK-04 | In-flight uncertainty is displayed | If an action may have partially executed when interrupted, UI shows "Unknown result — verify manually" instead of success/failure | UF-10, UF-12 | Automated |
| REQ-TASK-05 | Manual takeover | Takeover from overlay/primary UI succeeds in ≤2 taps from any app screen during a run | UF-10 | Manual |
| REQ-TASK-06 | Result verification | Every completed task ends with a verification step (artifact shown: sent message, screenshot, or step receipts); user can mark result "Looks wrong" | UF-11 | Shared |
| REQ-TASK-07 | No automatic irreversible retries; no automatic resume after recovery | Fault-injection: after any recovery-triggering error, the run stays `Interrupted` until the user explicitly resumes; irreversible actions are never retried without a fresh approval | UF-11, UF-12 | Automated |

### 7.8 SMS

| ID | Requirement | Proposed acceptance criterion | Trace | Owner |
|---|---|---|---|---|
| REQ-SMS-01 | Compose-first | Drafting an SMS completes with the composer pre-filled and **no message sent** without further user action | UF-13 | Automated |
| REQ-SMS-02 | Optional send requires explicit recipient + content confirmation | Confirmation card shows full recipient and full body; send proceeds only on explicit confirm; cancel leaves draft intact | UF-13 | Automated |
| REQ-SMS-03 | No inbox reading | Study APK does not request `READ_SMS`/`RECEIVE_SMS` (removing OpenDroid's declarations, CF-08); manifest assertion in gate G-05 | UF-13 | Automated |
| REQ-SMS-04 | Permission/policy compatibility | If the runtime SMS permission is unavailable or policy-restricted, EQO falls back to "Open your messaging app with this draft" and says so | UF-13 | Shared |

### 7.9 Privacy

| ID | Requirement | Proposed acceptance criterion | Trace | Owner |
|---|---|---|---|---|
| REQ-PRIV-01 | Minimal model context | Prompt payload audit: only fields required for the task; size logged per request | UF-09 | Automated |
| REQ-PRIV-02 | No secrets in logs | Automated grep over study logs for key material returns 0 hits | UF-13 | Automated |
| REQ-PRIV-03 | Informed screen/context transmission consent | Consent captured per session-scope with a visible "What EQO sends" page; declining disables the dependent capability only, not the whole app | UF-06 | Manual |
| REQ-PRIV-04 | Redaction limitations stated | Consent and settings copy states: "Redaction is best-effort. Sensitive content may still be visible to the model." | UF-06 | Manual (copy review) |

### 7.10 Recovery (cross-cutting)

Fault classes and required explicit states — each row is its own acceptance test in the study protocol:

| ID | Fault class | Required behavior (proposed) | Trace |
|---|---|---|---|
| REQ-REC-01 | API 401 (revoked key) | `ModelError: Unauthorized` → re-enter key; task `Interrupted` | UF-11 |
| REQ-REC-02 | API 429 (rate limit) | `ModelError: RateLimited` + visible countdown; user may wait or stop; **no auto-retry loop** | UF-11 |
| REQ-REC-03 | Insufficient credit | `ModelError: Credit` → link to provider billing, key remains stored | UF-11 |
| REQ-REC-04 | Incompatible model | `ModelError: IncompatibleModel` → model picker, capability re-check | UF-11 |
| REQ-REC-05 | Network error | `ModelError: Network` → retry only as user-initiated | UF-11 |
| REQ-REC-06 | ADB disconnection / revoke / reboot | `AdbDisconnected` / `AdbRevoked` / `AdbAfterReboot` distinct states; repair = re-run guided pairing; **fresh user resume + renewed approval** | UF-12 |
| REQ-REC-07 | Helper binder death | `BinderDead` → helper restart sequence as its own checks; task `Interrupted` | UF-12 |
| REQ-REC-08 | Accessibility lost | `A11yLost` → repair card, no auto-resume | UF-12 |
| REQ-REC-09 | Chrome debug unavailable | `DevToolsUnavailable` → re-run debug prep; consent not re-required (scope unchanged) | UF-12 |
| REQ-REC-10 | Virtual display failure | `VirtualDisplayFailed` → approved foreground fallback | UF-12 |

---

## 8. Architecture dependencies as hypotheses

These are hypotheses to be validated before/during the study — NOT established architecture.

| ID | Hypothesis | Evidence so far | Falsifier / proof needed |
|---|---|---|---|
| H1 | OpenDroid is a viable EQO base (rebrand `com.opendroid.aiagent` → EQO id, raise minSdk 26→31) | [VERIFIED] real build config, Hilt, 16 screens (`reports/qa-build-audit.md` §1; `sources/opendroid/app/build.gradle:11,16`) | Build + install on API 31+ device; lint gate passes (opendroid CI latest shows `failure`/`action_required` in `evidence.json`) |
| H2 | ClosePaw tools integrate into the OpenDroid base within study time | [VERIFIED] both are `:app`-module Kotlin apps; ClosePaw minSdk 31, OpenDroid 26 | A compiling merged source tree — **does not exist yet** |
| H3 | Shizuku manager/native bootstrap can be delivered **inside** the EQO APK without external branded install | [UNPROVEN GATE] Static inspection only; known blockers: uid 0/2000 gate (`starter.cpp:192-196`), hardcoded `moe.shizuku.privileged.api` server requirement, server exit on manager APK change, upstream branding prohibitions (`reports/helper-integration.md` §2.2, §2.6, §3) | On-device demo: helper starts, authorizes, binder healthy, per-function probes pass — **required before study APK gate (G-03)** |
| H4 | Wireless ADB pairing/connection works in-app across target OEMs | [PARTIAL] Full pairing client exists in Shizuku and ClosePaw sources (`reports/helper-integration.md` §2.5; `sources/closepaw/.../cdp/wireless/`); `adb devices` in evidence run was **empty** | Matrix run across ≥3 OEMs; explicit pairing-port vs connection-port verification |
| H5 | Chrome CDP readiness can be verified per-function | [VERIFIED source] `browser_script` needs Chrome + Shizuku (`sources/closepaw/README.md:62`); no EQO readiness flow exists | Endpoint + per-function probes on device |
| H6 | Virtual display works for a compatible-app subset with safe fallback | [VERIFIED source] `VdLifecycleArbiter` documents stale-`Running` race (`reports/qa-build-audit.md` F9) | On-device run incl. injected failure |
| H7 | Study instrumentation can be added without upstream modification of sources/ | This workspace keeps `sources/` read-only; EQO code would live outside `sources/` | Directory separation maintained (already true) |
| H8 | Persona memory/Room layer from OpenDroid suffices for study | [VERIFIED] Room 7 DAOs/3 migrations exist (`sources/opendroid/README.md:156-159`) | Only if study uses memory features (OD-07) |

---

## 9. Safety, privacy, and legal

- **Permission posture:** the app guides, the user grants, in Android-owned settings that keep their OS names (no rebranding of Android settings screens). REQ-A11Y-03.
- **Sensitive-action approvals:** contextual, in-flow, target-visible; timeout cancels (never approves) — mirrors ClosePaw `AwaitingApproval → Cancelled` semantics (`sources/closepaw/doc/main/state_machines/tool_call.md`).
- **Blocked categories:** ClosePaw hard-blocks banking/authenticator/crypto apps with no override (`sources/closepaw/README.md` "Safe by default"). EQO study inherits this as non-negotiable. [PROPOSED as requirement]
- **FLAG_SECURE surfaces** are invisible to perception by design (ClosePaw README) — surfaced to users as an expected limitation, not an error.
- **Secrets:** Keystore for BYOK; no key material in logs (REQ-BYOK-02, REQ-PRIV-02).
- **Screen/context transmission:** informed consent + stated redaction limitations (REQ-CDP-01, REQ-PRIV-03/04).
- **Legal notices & attribution:** reachable outside the normal flow (REQ-INS-03). Includes third-party attribution: OpenDroid (Apache-2.0), ClosePaw (Apache-2.0), Shizuku (Apache-2.0 + stated §6 restrictions), Shizuku-API (MIT) — license metadata [VERIFIED] via `evidence.json`.
- **Branding/licensing tension (CF-07)** must be escalated as a legal question, not resolved by engineering — flagged, not judged, consistent with `reports/helper-integration.md` §3.

---

## 10. Risk register

| ID | Risk | Likelihood | Impact | Mitigation / gate |
|---|---|---|---|---|
| R1 | Shizuku-in-single-APK infeasible or license-problematic (H3/CF-07) | High | Critical — blocks participant study APK | G-03 proof on hardware (engineering prototype) before the participant study APK gate; fallback = study without C5/C6 tiers (OD-03) |
| R2 | OEM/API variation breaks guided ADB (H4) | High | High — onboarding abandonment | Per-device failure/repair states (REQ-ADB-11/12); matrix ≥3 OEMs |
| R3 | Android 13+ restricted settings block accessibility toggle | Medium | High — setup dead-end | REQ-A11Y-02 repair card; study metric on recovery rate |
| R4 | Participant leaks key (logs/crash) | Medium | Critical | REQ-BYOK-02/REQ-PRIV-02 automated log scrape |
| R5 | Ambiguous in-flight state erodes trust (REQ-TASK-04) | Medium | High | "Unknown result — verify manually" state; scenario US-05 |
| R6 | OpenDroid upstream CI currently failing (`evidence.json`: latest Android CI `failure`, plus `action_required`) | Medium | Medium | Fork-and-fix within EQO workspace; gate G-01 |
| R7 | ClosePaw release pipeline runs no tests (`reports/qa-build-audit.md` F5) | Medium | Medium | EQO adds its own smoke gate; do not inherit upstream silence |
| R8 | Scope creep toward "universal automation" promise | Medium | High | Non-goals §4.2; copy review in G-06 |
| R9 | Consent fatigue → rubber-stamp approvals | Medium | Medium | Contextual (not blanket) approvals; study measures approval-read time |
| R10 | Helper dies after reboot (no persistence across updates; boot-start only Android 13+ w/ prior grant — `reports/helper-integration.md` §2.6) | High | Medium | REQ-REC-06/07 states; user re-runs guided check, no auto-resume |

---

## 11. Study protocol and measurable usability scenarios

**Study type:** moderated, formative feasibility + usability study on the study APK (when built). No results exist; all metrics below are **targets/proposed measures**, not outcomes.

**Participants:** 6–10 (P1-heavy), ≥3 distinct devices (OEM spread), API 31/33/35 represented.
**Instrumentation:** per-check events (pass/fail/reason), per-step task receipts, consent log. No key material logged.

### Scenarios (with proposed measures)

| ID | Scenario (persona) | Steps | Primary measure | Target [PROPOSED] |
|---|---|---|---|---|
| US-01 | Fresh install → first task ready (P1) | UF-01→UF-08 chain | Time-to-ready; facilitator interventions | ≤25 min median; ≤3 interventions (proposed target; the in-app "10–15 minutes" copy is an unvalidated estimate to be measured later and reconciled with this target — see UF-01) |
| US-02 | Bad API key (P1/P2) | UF-02 failure branch | % recognizing error + completing repair without help | ≥80% |
| US-03 | Accessibility blocked on Android 13+ (P1) | UF-03 repair | % completing restricted-settings repair unaided | ≥70% |
| US-04 | Wireless ADB pairing (P1, never used ADB) | UF-04→UF-05 | Completion rate; pairing-vs-connection port confusion incidents | ≥80% completion; 0 port-confusion defects (REQ-ADB-05) |
| US-05 | Interrupt mid-task: Pause vs Stop vs takeover (P3) | UF-10 | Correctly distinguishing pause/stop in post-task interview; takeover taps | ≥90% correct; ≤2 taps |
| US-06 | Sensitive-action approval (P2) | UF-10 | % reading target before approving; denied-path success | ≥90% read; 100% denial works |
| US-07 | Revoked key mid-session (P2) | UF-11 REQ-REC-01 | % correctly diagnosing; no data loss | 100% diagnosis; 0 lost drafts |
| US-08 | ADB disconnect + reboot (P1) | UF-12 | % completing repair without facilitator | ≥70% |
| US-09 | Chrome CDP consent understanding (P2) | UF-06 | Comprehension check on "what is sent / redaction limits" | ≥90% correct answers |
| US-10 | SMS compose → confirm send (P1) | UF-13 | % confirming recipient+content correctly; near-miss cancellations | 100% correct recipient; cancellations safe |
| US-11 | Virtual display incompatible app (P3) | UF-07 | % accepting foreground fallback without confusion | ≥90% |
| US-12 | Find legal notices cold (P4/P2) | UF-14 | Success in ≤3 taps | 100% |

**Procedure:** consent → think-aloud tasks (US-01..US-12 as assigned) → post-task interview (pause/stop/consent comprehension) → severity ratings (0–3) per defect. No incentives tied to speed.

---

## 12. Release / study gates and open decisions

### 12.1 Gates (ordering: engineering feasibility first, then the participant study build)

**Gate ordering clarification (review fix):** these gates are not all "before any APK exists" — that would be circular, because **G-3 can only be proven by engineering feasibility prototypes/APKs run on real devices** (not participant builds). Sequence: (a) engineering feasibility prototypes demonstrate G-1/G-2/G-3 on hardware, then (b) the **participant study APK gate** (G-4..G-7) may pass, then (c) distribution to study participants. No participant-facing study APK is released until G-3 is demonstrated on hardware.

| Gate | Criteria | Status |
|---|---|---|
| **G-0 Document** | PRD.md + USER-FLOWS.md drafted with conflict ledger and traceability | 🟡 Draft complete, **awaiting review sign-off** — not a validated product artifact |
| **G-1 Base readiness** | OpenDroid fork builds; minSdk=31; EQO application id; lint smoke green (upstream CI currently `failure` in `evidence.json`) | ❌ not started |
| **G-2 Integration readiness** | ClosePaw tool set compiles into EQO base; approval/pause/stop state machines reachable | ❌ not started |
| **G-3 Helper feasibility (hard gate)** | **Shizuku manager/native bootstrap feasibility proven on-device via an engineering prototype (non-participant build):** helper starts, authorization granted, binder healthy, per-function probes pass — **within a single EQO-branded APK, with the CF-07 branding/licensing question escalated** | ❌ **UNPROVEN — must be demonstrated on hardware before the participant study APK gate passes; engineering prototype builds are expected and allowed to prove it** |
| **G-4 Setup flows** | UF-01..UF-08 implemented with every check independently failing and repairing; ADB port-distinction verified on device | ❌ not started |
| **G-5 Safety assertions** | Manifest: no `READ_SMS`/`RECEIVE_SMS`; no key in logs; blocked-app list intact; copy review for REQ-A11Y-03, REQ-PRIV-04, REQ-VD-03 | ❌ not started |
| **G-6 UX readiness** | All microcopy in USER-FLOWS approved; states distinguish pause/stop/uncertainty; no generic "ready" strings | ❌ draft microcopy in USER-FLOWS |
| **G-7 Study readiness** | Instrumentation events defined; facilitator script + consent form; device matrix ≥3 OEMs | ❌ not started |

### 12.2 Open decisions

| ID | Decision | Owner | Needed by |
|---|---|---|---|
| OD-01 | Keep any offline/local model fallback (CF-01), or OpenRouter-only for study? | Product | G-1 |
| OD-02 | CF-07 resolution path: bundle strategy vs. dependency strategy vs. drop C5/C6 | Legal + Eng | G-3 |
| OD-03 | If G-3 fails, does the study run with accessibility-only tiers (C0–C2, C7)? | Product | G-3 |
| OD-04 | Is `termux_shell` / any Python runtime excluded outright (CF-06)? | Eng | G-2 |
| OD-05 | Approval timeout value (proposal: 60s, matching ClosePaw `APPROVAL_TIMEOUT_MS`) | UX | G-6 |
| OD-06 | Voice/wake-word in study scope (CF-09)? | Product | G-4 |
| OD-07 | Is persistent memory/Room in study scope? | Product | G-2 |
| OD-08 | Cloud VM tier and tiers from old PRODUCT.md — deferred entirely? | Product | post-study |
| OD-09 | CF-10 resolution: is the old "no paid subscription" constraint lifted for OpenRouter BYOK, and is explicit participant cost disclosure sufficient? | Product + Legal | G-4 |

---

## 13. Traceability summary

- Requirements REQ-* (§7) → flows UF-* and screens S-* (`USER-FLOWS.md` §17 traceability matrix).
- Hypotheses H1–H8 (§8) → gates G-1..G-3 (§12.1).
- Conflicts CF-01..CF-09 (§3) → open decisions OD-* (§12.2).
- Risks R1–R10 (§10) → scenarios US-01..US-12 (§11).

## 14. Change control

This draft modifies no source. Any change to §3 conflicts or §12 gates requires re-reading the latest approved brief first; source repos under `sources/` remain read-only.
