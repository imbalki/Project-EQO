# EQO QA Feasibility Report

Role: Independent EQO QA lead worker (audit of local evidence only).
Date: 2026-10-01. Root: C:/Users/<user>/Hermes/EQO-Android-Study.
Writes confined to: C:/Users/<user>/Hermes/EQO-Android-Study/team/qa/ (this file).
No source, config, or CI changes were made. No Gradle build or test was executed locally (read-only source mandate + no Java SDK). No publication.

---

## 1. Provenance — source SHAs verified locally

Verified with `git -C <dir> rev-parse HEAD` and `git log -1` (read-only) on 2026-10-01:

| Source | Path (absolute) | Remote | SHA (verified locally) | Last commit |
|---|---|---|---|---|
| opendroid | C:/Users/<user>/Hermes/EQO-Android-Study/sources/opendroid | github.com/yashab-cyber/opendroid | 6ff5a061755b597b0558fed1f565587837ed4d51 | 2026-09-04 "feat: AI Social Media Management System, SAF storage policy remediation…" |
| closepaw | C:/Users/<user>/Hermes/EQO-Android-Study/sources/closepaw | github.com/imoonkey/closepaw | 75dae2653f5a6b25d5df51ee7008b0f830de1536 | 2026-06-04 "refactor: rename ignored projects/ symlink → plan/" |
| shizuku | C:/Users/<user>/Hermes/EQO-Android-Study/sources/shizuku | github.com/RikkaApps/Shizuku | b844bc491f1790c72328e1a8e5b2349f8978f0ea | 2025-06-18 "fixup! Clarify license" |
| shizuku-api | C:/Users/<user>/Hermes/EQO-Android-Study/sources/shizuku-api | github.com/RikkaApps/Shizuku-API | a27f6e4151ba7b39965ca47edb2bf0aeed7102e5 | 2025-05-29 "Bump patch version to 6" |
| main (Project-EQO) | C:/Users/<user>/Hermes/EQO-Android-Study/sources/main | github.com/imbalki/Project-EQO | 4b2d77f34c3d6b8d4d601c7af8bf8f228ffd0500 | 2026-09-30 "docs(agents): add Android agent workspace with Phase 1 task files" |

Evidence files (retrieved 2026-10-01T06:14:02Z per evidence.json:2):
- C:/Users/<user>/Hermes/EQO-Android-Study/evidence.json (1,065 lines; repo metadata + CI runs)
- C:/Users/<user>/Hermes/EQO-Android-Study/ci-detail.json (58 KB; API payloads for run 33920897530 + check-runs)
- C:/Users/<user>/Hermes/EQO-Android-Study/lint-evidence.json (lint job annotations + logs 401)
- C:/Users/<user>/Hermes/EQO-Android-Study/lint-artifacts.json ({"total_count": 0, "artifacts": []})

SHAs above match the `inspected_sha` fields in evidence.json:13,65 (and equivalents). All sources were left unmodified.

## 2. Evidence-tier discipline

Three tiers are used and never mixed:

- T1 — Upstream CI job metadata (GitHub API records captured in evidence.json/ci-detail.json/lint-evidence.json). Not executed by me.
- T2 — Static inspection of local source trees (test files, workflows, baselines read with file/terminal tools). Existence and content only; no execution.
- T3 — Independently executed tests (would require running Gradle on this machine). NONE WERE RUN. **No local Gradle test is claimed as passed anywhere in this report.**

## 3. Environment probes (read-only, executed 2026-10-01)

- `java -version` → "java: command not found" (T3 probe; corroborates evidence.json:5 `"java": null`). No local Java SDK.
- `adb devices` → "List of devices attached" then empty (T3 probe; corroborates evidence.json `adb_devices` key: exit_code 0, empty stdout). No connected device/emulator. adb binary exists (evidence.json:6, scrcpy-bundled adb v4.0).
- Gradle wrappers present (T2): sources/opendroid/gradlew, sources/closepaw/gradlew — but unusable without a JDK.

**Consequence:** unit/instrumented test execution is BLOCKED locally. Feasibility of running tests can only be asserted for CI (where JDK 21 + emulator lanes exist), not on this machine.

## 4. Context verification — OpenDroid CI at 6ff5a061 (T1)

All items in the parent-provided context were verified against ci-detail.json (run 33920897530, "Android CI", workflow .github/workflows/android-ci.yml, push event, run_number 43, created 2026-09-04T21:23:50Z, run conclusion "failure"):

| Job | Conclusion | Confirmed? |
|---|---|---|
| Unit tests and debug build | success | YES (ci-detail.json jobs entry; steps "Run unit tests and build debug APK" success) |
| Android Lint | failure | YES (step "Run Android Lint" failure; lint-evidence.json annotation "Process completed with exit code 1", start_line 415) |
| Unsigned release build (R8/ProGuard) | success | YES |
| Instrumented tests (API 36) | success | YES |
| Instrumented tests (API 26) | success | YES |

- Logs 401: YES — lint-evidence.json, key "actions/jobs/101178721895/logs": "HTTP Error 401: Server failed to authenticate the request." Job log text is therefore NOT available; only annotations survive.
- Artifacts empty: YES — lint-artifacts.json: total_count 0.
- Caveat: these five jobs ran on OTHER SHAs too; the lint-failure run at 6ff5a061 is the head-of-branch run. Unit/instrumented "success" entries in evidence.json list runs at various SHAs — all CI claims here are T1 metadata, not my execution.

## 5. Static test inventory (T2 — files exist, NOT executed)

### opendroid (6ff5a061)
- Unit tests: 63 files matching *Test.kt under sources/opendroid/app/src/test (packages: actions, core/agent, core/llm(+error,+providers), core/security, core/service, core/storage, data, social, ui, …).
- Instrumented: 5 files under sources/opendroid/app/src/androidTest — ClickTargetingAccuracyInstrumentationTest.kt, KnownScreenScrapeInstrumentationTest.kt, ServiceReadinessInstrumentationTest.kt (accessibility), AndroidKeyStoreAeadCipherInstrumentationTest.kt, AndroidProviderCredentialStoreInstrumentationTest.kt (core/security).
- CI workflow: sources/opendroid/.github/workflows/android-ci.yml (184 lines). L27-57 unit+debug lane (`./gradlew testDebugUnitTest assembleDebug`, L50); L61-93 lint lane (`./gradlew :app:lintDebug`, L83; L80 comment: "Pre-existing findings live in app/lint-baseline.xml; only new…"); L97-138 instrumented lane matrix api-level [26, 36] (L104), google_apis target for >=30 (L138).
- Lint baseline: sources/opendroid/app/lint-baseline.xml — 16 `<issue>` entries, lint 9.3.1, AGP 9.3.1. Baseline is PRESENT and wired into CI policy.
- SDK: app/build.gradle L12 compileSdk 36, L16 minSdk 26, L17 targetSdk 36.
- Network security config present: sources/opendroid/app/src/main/res/xml/network_security_config.xml.

### closepaw (75dae265)
- Unit tests: 205 files under app/src/test (…/test/kotlin/ai/closepaw/…). Instrumented: 25 files under app/src/androidTest.
- Scope-relevant baseline tests found (paths relative to sources/closepaw/app/src):
  - Wireless ADB pairing unit: test/kotlin/ai/closepaw/browser/cdp/wireless/AdbWirelessManagerTest.kt (enableWirelessDebugging happy path L41, no-bssid failure L52, binder-false L63, port retrieval L74-80, openPairPort L86-108 incl. empty-PSK rejection, closePairPort L118, pubkey authorized L124), Spake25519Test.kt (pairing crypto).
  - Wireless ADB pairing instrumented: androidTest/kotlin/ai/closepaw/browser/cdp/wireless/AdbPairingClientInstrumentedTest.kt (spake25519Reachable L12, conscryptExporterReachable L22).
  - CDP: main/kotlin/ai/closepaw/browser/cdp/ (ChromeCdpClient.kt, ChromeCdpCommand.kt, ChromeCdpEventBuffer.kt, CdpTransport.kt …) + shizuku/ bridge (ShizukuChromeDevtoolsBridge.kt, ChromeDevtoolsUserService.kt, DevtoolsHttpProtocol.kt). Instrumented: androidTest/…/browser/script/BrowserScriptRunnerInstrumentedTest.kt (`cdp_round_trip_resolves_on_real_webview` L36), BrowserRealDeviceQaInstrumentedTest.kt.
  - Pause/Stop + takeover races: test/kotlin/ai/closepaw/session/AgentSessionTest.kt — `shutdown from running emits session completed user stopped` L44, `resume rejected while takeover still pending` L173-194 (Op.Takeover L183), `paused not observable before pause confirmation` L201-212.
  - Approvals: androidTest/kotlin/ai/closepaw/qa/CapsuleApprovalTest.kt; test …/session/AgentSessionCompletionHandoffTest.kt.
  - BYOK/LLM auth: androidTest/kotlin/ai/closepaw/qa/SettingsLlmAuthTest.kt (OpenRouter provider switch L120-123, api-key/OAuth tab commit rules), test …/llm/OtherBaseUrlValidatorTest.kt, LLMClientFactoryTest.kt, AuthStoreTest.kt (auth/), CloudStreamRetryPolicyTest.kt + CloudStreamRetryRunnerTest.kt (retry/rate-limit behavior).
  - Secret redaction: test/kotlin/ai/closepaw/trace/CognitionTraceRedactorSecurityTest.kt, AgentTraceArtifactsTest.kt, agent/AgentTraceObservabilityTest.kt.
  - Virtual display: androidTest/kotlin/ai/closepaw/qa/DisplayModeSettingsTest.kt; main …/app/AgentService.kt, ServiceOverlayController.kt, AgentServiceViewerBridge.kt (implementation refs).
  - Lifecycle/security: test/kotlin/ai/closepaw/app/MainActivityIntentApplierSecurityTest.kt; network_security_config.xml in main and debug res/xml.
  - Onboarding: main/kotlin/ai/closepaw/onboarding/ (OnboardingViewModel.kt, OnboardingState.kt, HttpLlmCredentialValidator.kt, …) — implementation exists; dedicated onboarding test files NOT found (gap).
  - Legal notices baseline: closepaw/NOTICE, closepaw/app/src/main/assets/open_source_licenses.json, ui/settings/OpenSourceLicensesPage.kt.
- SDK: app/build.gradle.kts L16 compileSdk 36, L22 minSdk 31 (= Android 12+), L23 targetSdk 36.

### opendroid secret/approval/error-relevant tests (T2)
core/security/ProviderCredentialStoreTest.kt, LegacySecurePreferencesRetirementTest.kt; core/crash/CrashLogRedactorTest.kt, CrashReportExporterTest.kt; core/agent/ActionRiskPolicyTest.kt, AutoApprovalPolicyTest.kt, NeverAutoApproveTest.kt; core/llm/error/LLMErrorMapperTest.kt + ProviderErrorDetailTest.kt (429/quota/rate-limit mapping appears in these and ChatErrorUiStateTest.kt per content grep); core/llm/providers/CustomOpenAIProviderNetworkTest.kt.

### shizuku / shizuku-api (T2)
Library/manager sources only (server, starter, rish, api). No EQO-specific tests. Shizuku manager is a SEPARATE named helper app — this conflicts with the EQO "integrated manager, no separate named helper" requirement; closepaw's in-app Shizuku user-service bridge (…/cdp/shizuku/ShizukuChromeDevtoolsBridge.kt) is the only in-tree pattern for embedding that capability without a named helper.

## 6. Baseline present vs proposed EQO tests

"Baseline present" = a test file statically found in opendroid/closepaw that covers the EQO concern (NOT executed, no pass claim). "Proposed" = does not exist in any source tree; EQO must author it.

| EQO concern | Baseline present (path) | Proposed EQO tests (new) |
|---|---|---|
| Wireless ADB pairing flow | closepaw AdbWirelessManagerTest.kt, Spake25519Test.kt, AdbPairingClientInstrumentedTest.kt | EQO-WADB-* below (connection loss / revoked pairing / reboot distinct cases are NOT covered by baseline) |
| Connection loss after pairing | Partial: AdbWirelessManagerTest error paths only | EQO-WADB-004..006 |
| Revoked pairing vs helper-app authorization | pubkey-authorized check only (AdbWirelessManagerTest L124); no revoke/reboot tests | EQO-WADB-007..009 |
| Integrated manager (no named helper) | closepaw shizuku bridge pattern (source only) | EQO-MGR-001..003 |
| Chrome CDP navigation/forms + restart | closepaw CDP stack + BrowserScriptRunnerInstrumentedTest (round-trip only) | EQO-CDP-001..006 |
| Virtual display perception/input/cleanup | DisplayModeSettingsTest (settings only); no cleanup/perception tests | EQO-VD-001..005 |
| Pause/Stop + takeover races | AgentSessionTest.kt L44, L173, L201 (strong unit baseline) | EQO-PS-001..006 (in-flight irreversible effects NOT in baseline) |
| Approvals | CapsuleApprovalTest.kt; opendroid ActionRiskPolicyTest/AutoApprovalPolicyTest/NeverAutoApproveTest | EQO-APR-001..005 |
| Secret redaction | CognitionTraceRedactorSecurityTest.kt; opendroid CrashLogRedactorTest.kt | EQO-SEC-001..005 |
| BYOK / OpenRouter | SettingsLlmAuthTest.kt, OtherBaseUrlValidatorTest.kt, AuthStoreTest.kt, opendroid ProviderCredentialStore tests | EQO-BYOK-001..008 |
| Branding + legal-notices exception | closepaw NOTICE + open_source_licenses.json + OpenSourceLicensesPage.kt; opendroid LicenseScreen.kt | EQO-BRD-001..003 |
| Offline/invalid key/quota/rate limits | CloudStreamRetryPolicyTest, LLMErrorMapperTest (error mapping) | EQO-BYOK-005..008 |
| Lifecycle / network security | MainActivityIntentApplierSecurityTest; network_security_config.xml (both apps) | EQO-SEC-006..008 |
| Novice onboarding / usability | Onboarding implementation only (no tests) | EQO-ONB-001..004 |

## 7. Test matrix (proposed EQO tests — status all "PROPOSED, not executed")

Every row: Status = PROPOSED (no execution). Baseline column = reuse candidates (T2 existence only).

### 7.1 Wireless ADB pairing (mandatory; loss / revoke / reboot distinct from helper-app authorization)

| ID | Pri | Preconditions | Steps | Expected | Layer | Baseline | Blockers |
|---|---|---|---|---|---|---|---|
| EQO-WADB-001 | P0 | Device API 31+; wireless debugging on; EQO installed | Open pairing screen; enter pairing code+IP:port; complete SPAKE2+ pairing | Pairing succeeds; adb keys record persisted; status shows paired | Instrumented | AdbPairingClientInstrumentedTest (crypto reachability) | Needs real device/emulator with wireless debugging (none attached) |
| EQO-WADB-002 | P0 | Pairing screen open | Enter wrong pairing code | Failure surfaced distinctly ("invalid code"), no partial trust stored | Instrumented | AdbWirelessManagerTest error paths | same |
| EQO-WADB-003 | P0 | Pairing screen open | Enter empty PSK / malformed port | Rejected pre-network; error copy actionable for novice | Unit | AdbWirelessManagerTest L108 openPairPort_rejects_empty_psk | none (unit runnable in CI) |
| EQO-WADB-004 | P0 | Paired session active | Drop network mid-session (toggle Wi-Fi / switch AP) | Connection-loss state distinct from revocation; UI shows "connection lost" + retry; no data corruption | Instrumented | none | Emulator Wi-Fi control |
| EQO-WADB-005 | P0 | Connection lost (from 004) | Retry after network restored | Auto or 1-tap reconnect; session state preserved | Instrumented | none | same |
| EQO-WADB-006 | P1 | Paired | Reboot device, launch EQO | Post-reboot state = "authorized but disconnected"; reconnect flow offered; NOT shown as revoked or un-paired | Manual + Instrumented | none | Real device for reboot lane |
| EQO-WADB-007 | P0 | Paired | Revoke authorization (device-side "Revoke USB debugging authorizations" / adb keys removal) | Revocation detected; distinct UI state from connection loss; re-pairing required | Manual + Instrumented | pubkey check (AdbWirelessManagerTest L124) only | Real device |
| EQO-WADB-008 | P0 | EQO integrated manager only (no Shizuku Manager app installed) | Perform 001,004,007 with no separate helper app present | All flows succeed without any separately installed named helper; if a privileged helper is unavoidable, it is unnamed/absent from launcher per spec | Manual | closepaw shizuku bridge pattern | Spec ambiguity (see §9 B4) |
| EQO-WADB-009 | P1 | Helper-app authorization scenario | Toggle helper authorization (e.g., Shizuku user-service grant) | UI distinguishes "helper authorization revoked" from "pairing revoked" and from "connection lost" — 3 distinct states | Manual | none | none |

### 7.2 Integrated manager (no separate named helper)

| ID | Pri | Preconditions | Steps | Expected | Layer | Baseline | Blockers |
|---|---|---|---|---|---|---|---|
| EQO-MGR-001 | P0 | Fresh install | Inspect launcher + installed packages during onboarding | Exactly one EQO app; no companion "manager" app installed or launched | Manual | closepaw pattern | none |
| EQO-MGR-002 | P1 | Privileged functions used | Verify manager functionality (pairing mgmt, CDP bridge) reachable in-app | All manager features inside EQO UI; settings reachable in <=3 taps | Manual + Instrumented (Compose) | DisplayModeSettingsTest (UI test pattern) | none |
| EQO-MGR-003 | P1 | App info / permissions screens | Check labels, icons, permissions text | EQO branding consistently; no upstream project names exposed in user-visible strings | Manual | none | Branding strings not yet defined (B5) |

### 7.3 Chrome CDP navigation/forms, debugging preparation and restart

| ID | Pri | Preconditions | Steps | Expected | Layer | Baseline | Blockers |
|---|---|---|---|---|---|---|---|
| EQO-CDP-001 | P0 | Chrome present; CDP bridge prepared (devtools enabled) | Navigate to test page via CDP | Page loads; navigation events buffered | Instrumented | BrowserScriptRunnerInstrumentedTest L36 round-trip; ChromeCdpEventBuffer.kt | Needs Chrome + real webview/device |
| EQO-CDP-002 | P0 | Test form page | Fill fields + submit via CDP | Values entered; submit completes; no crash | Instrumented | none (forms gap) | same |
| EQO-CDP-003 | P1 | CDP connected | Kill/restart Chrome debugging (devtools restart) | EQO detects loss, re-prepares debugging, reconnects without user data loss | Manual + Instrumented | DevtoolsSetupError.kt (error surface) | Chrome restart automation |
| EQO-CDP-004 | P1 | CDP connected | Rotate device / background app during session | Session survives or clean reconnect; no leaked websockets | Instrumented | CdpTransport.kt | none |
| EQO-CDP-005 | P2 | CDP connected | Malformed CDP target id / closed tab | Graceful error; no ANR; error copy actionable | Unit + Instrumented | ChromeCdpTarget.kt | none |
| EQO-CDP-006 | P1 | Novice user | Complete CDP prep via onboarding guidance | Steps understandable without ADB knowledge; <=5 min | Manual | none | Onboarding copy not written |

### 7.4 Virtual display — app perception, input, cleanup

| ID | Pri | Preconditions | Steps | Expected | Layer | Baseline | Blockers |
|---|---|---|---|---|---|---|---|
| EQO-VD-001 | P0 | Virtual display created | Launch app on virtual display; capture via accessibility/screenshot | Perception pipeline returns correct content | Instrumented | KnownScreenScrapeInstrumentationTest.kt (opendroid) | Emulator w/ virtual display support |
| EQO-VD-002 | P0 | App visible on virtual display | Perform tap/swipe/text input | Input lands in correct target | Instrumented | ClickTargetingAccuracyInstrumentationTest.kt (opendroid) | same |
| EQO-VD-003 | P0 | Virtual display active | Stop task / kill session | Display destroyed; surfaces released; no leaked Surface/MediaProjection refs (memory check) | Instrumented + Manual | SessionServicesCleanupTest.kt (closepaw, session cleanup) | Leak detection tooling |
| EQO-VD-004 | P1 | Rotation/density change mid-task | Rotate; resume | Perception re-syncs; no stale coordinates | Instrumented | none | none |
| EQO-VD-005 | P2 | Low-memory device (4 GB class) | Create 2+ virtual displays | Fail gracefully with clear message; no system kill of EQO | Manual | none | Low-end device availability |

### 7.5 Pause / Stop, manual takeover races, in-flight irreversible effects

| ID | Pri | Preconditions | Steps | Expected | Layer | Baseline | Blockers |
|---|---|---|---|---|---|---|---|
| EQO-PS-001 | P0 | Agent running task | Pause | Agent reaches safe point; state "paused" only after confirmation | Unit | AgentSessionTest L201 paused-not-observable-before-confirmation | none |
| EQO-PS-002 | P0 | Pause requested, not yet confirmed | Resume attempt | Rejected until pause confirmed | Unit | AgentSessionTest L173-194 | none |
| EQO-PS-003 | P0 | Agent running | Stop (user) | "Session completed — user stopped" emitted; no orphaned coroutines/actions | Unit | AgentSessionTest L44 | none |
| EQO-PS-004 | P0 | Stop during in-flight irreversible action (e.g., message send / delete mid-call) | Stop pressed mid-action | Documented behavior: irreversible action completes OR is cancelled before dispatch; user warned pre-action via approval gate; never silent | Manual + Instrumented | none (gap — highest race risk) | Needs fault-injection harness |
| EQO-PS-005 | P0 | Manual takeover requested while agent mid-action | Takeover during tap sequence | Race-free: takeover wins deterministically; no double-input on same node | Unit + Instrumented | AgentSessionTest takeover tests; NodeActionPerformer.kt | none |
| EQO-PS-006 | P1 | Pause then network loss | Pause + drop network | Paused state stable across network loss; resume works after reconnect | Instrumented | none | Wi-Fi control |

### 7.6 Approvals

| ID | Pri | Preconditions | Steps | Expected | Layer | Baseline | Blockers |
|---|---|---|---|---|---|---|---|
| EQO-APR-001 | P0 | Action flagged risky (send/delete/purchase) | Trigger action | Explicit user approval required before dispatch; no auto-approve | Unit + Instrumented | NeverAutoApproveTest.kt, ActionRiskPolicyTest.kt (opendroid); CapsuleApprovalTest.kt (closepaw) | none |
| EQO-APR-002 | P0 | Approval dialog shown | Deny / timeout / dismiss | Action cancelled; no partial side effect | Instrumented | CapsuleApprovalTest.kt | none |
| EQO-APR-003 | P1 | Approval granted for one action | Next risky action | Fresh approval demanded (no session-wide blanket) unless user opted in explicitly | Unit | AutoApprovalPolicyTest.kt | none |
| EQO-APR-004 | P1 | Approvals list UI | Review past decisions | Complete audit trail with timestamps | Manual + Instrumented | none | UI not built |
| EQO-APR-005 | P2 | Auto-approve opted-in | Cancel auto-approve mid-run | Reverts to per-action approval immediately | Unit | none | none |

### 7.7 Secret redaction & network security

| ID | Pri | Preconditions | Steps | Expected | Layer | Baseline | Blockers |
|---|---|---|---|---|---|---|---|
| EQO-SEC-001 | P0 | BYOK key set; agent interacts | Capture logs, crash reports, traces, exported artifacts | Zero occurrences of API key/prefix across all outputs | Unit | CognitionTraceRedactorSecurityTest.kt, CrashLogRedactorTest.kt | none |
| EQO-SEC-002 | P0 | Key in storage | Inspect at-rest storage | Keystore-backed encryption, not plaintext prefs (per sources/main/agents/android/context/CONSTRAINTS.md L5) | Unit + Instrumented | ProviderCredentialStoreTest.kt, AndroidProviderCredentialStoreInstrumentationTest.kt | none |
| EQO-SEC-003 | P0 | Network capture during LLM calls | MITM proxy with user CA | Key only in Authorization header over TLS; no key in URLs/logs (CONSTRAINTS.md L4 "Never log … tokens or API keys") | Manual + Instrumented | network_security_config.xml (both apps) | Test cert provisioning |
| EQO-SEC-004 | P1 | UI screens showing key entry | Screen-record + screenshot | Key masked after entry; not recoverable from backup (android:allowBackup=false check) | Manual + static | none | none |
| EQO-SEC-005 | P1 | Freemium subsidized key (if used) | Static scan of APK + repo | Build-time injected only, never committed (CONSTRAINTS.md L8) | Static analysis | none | none |
| EQO-SEC-006 | P1 | Lifecycle: process death mid-task | Kill process; relaunch | State restored safely; no secrets in savedInstanceState | Instrumented | none | none |
| EQO-SEC-007 | P2 | Intent/exported components scan | Manifest audit | No unintended exported components; deep links validated | Static + Instrumented | MainActivityIntentApplierSecurityTest.kt | none |
| EQO-SEC-008 | P2 | Cleartext traffic attempt | Force http endpoint | Blocked per network_security_config | Instrumented | network_security_config.xml | none |

### 7.8 BYOK / OpenRouter — offline, invalid key, quota, rate limits

| ID | Pri | Preconditions | Steps | Expected | Layer | Baseline | Blockers |
|---|---|---|---|---|---|---|---|
| EQO-BYOK-001 | P0 | Fresh install | Enter OpenRouter key via settings; validate | Key accepted/validated; stored securely; provider "OpenRouter" selectable | Instrumented | SettingsLlmAuthTest.kt L120-123 | Live OpenRouter endpoint or mock |
| EQO-BYOK-002 | P0 | — | Enter invalid key | Clear "invalid key" error (distinct from network error); key not persisted as valid | Instrumented | HttpLlmCredentialValidator.kt (impl) | same |
| EQO-BYOK-003 | P0 | Airplane mode | Attempt validation + chat | Offline error distinct; retry offered; app fully non-network features usable (privacy default, CONSTRAINTS.md L3) | Instrumented + Manual | CloudStreamRetryPolicyTest.kt | none |
| EQO-BYOK-004 | P0 | Valid key; server returns 401 mid-run (key revoked at provider) | Run task | Runtime auth failure surfaces re-onboard prompt; no retry storm | Unit + Instrumented | LLMErrorMapperTest.kt, ProviderErrorDetailTest.kt | mock server |
| EQO-BYOK-005 | P0 | Provider returns 429 quota | Run task | Quota message distinct from rate limit; backoff honored; user told when to retry | Unit | CloudStreamRetryPolicyTest/RunnerTest.kt | none |
| EQO-BYOK-006 | P1 | Provider returns 429 rate limit (short window) | Rapid successive tasks | Backoff + single user-facing notice; no duplicate sends after limit clears | Unit + Instrumented | CloudStreamRetryRunnerTest.kt | none |
| EQO-BYOK-007 | P1 | Custom base URL (self-hosted/proxy) | Enter base URL | Validated (scheme/host); misconfiguration caught | Unit | OtherBaseUrlValidatorTest.kt | none |
| EQO-BYOK-008 | P2 | Two providers configured | Switch providers mid-session | Clean switch; per-provider error mapping retained | Unit | LLMClientFactoryTest.kt | none |

### 7.9 Branding & legal-notices exception

| ID | Pri | Preconditions | Steps | Expected | Layer | Baseline | Blockers |
|---|---|---|---|---|---|---|---|
| EQO-BRD-001 | P0 | Install EQO APK | Walk every user-visible screen | "EQO" branding only; no upstream names/logos (opendroid/closepaw/Shizuku) in UI strings/icons/labels | Manual + static string scan | none | Final brand assets undefined |
| EQO-BRD-002 | P0 | Settings → legal | Open legal-notices screen | Legal-notices screen MAY show upstream project names/licenses (explicit exception); matches license obligations of sources | Manual | open_source_licenses.json, OpenSourceLicensesPage.kt, NOTICE, LicenseScreen.kt | none |
| EQO-BRD-003 | P1 | About screen | Verify version/build info | Correct versionName; release notes link; privacy policy link per PRODUCT.md | Manual | none | none |

### 7.10 OS/OEM matrix (all P0/P1 cases above re-run on: Android 12 (API 31/32), 13 (33), 14 (34), 15 (35), 16 (36))

| ID | Pri | Scope | Notes | Blockers |
|---|---|---|---|---|
| EQO-COMPAT-12..16 | P0 | Full P0 suite per OS level | CI today exercises only API 26/36 (android-ci.yml L104) — 31/33/34/35 lanes absent; EQO minSdk 31 per closepaw precedent | Emulator lanes must be added (in EQO repo, not here) |
| EQO-OEM-001 | P0 | Samsung (One UI 6/7) | Wireless debugging placement differs; battery optimization kills background agent; accessibility timeout policies | Physical device access |
| EQO-OEM-002 | P0 | Xiaomi/MIUI, Oppo/ColorOS, Vivo/OriginOS | Autostart restrictions; background popup permission; ADB pairing UX differences | Physical devices |
| EQO-OEM-003 | P1 | Pixel (AOSP baseline) | Reference behavior | Emulator substitute acceptable |
| EQO-OEM-004 | P1 | OnePlus/Nothing | Battery/thermal throttling during virtual display workloads | Physical devices |

### 7.11 Novice onboarding & usability

| ID | Pri | Preconditions | Steps | Expected | Layer | Baseline | Blockers |
|---|---|---|---|---|---|---|---|
| EQO-ONB-001 | P0 | First launch, novice persona (no ADB knowledge) | Complete setup incl. pairing + BYOK without external help | Success in <=10 min; zero dead ends; every error has recovery action | Manual | Onboarding impl only (no tests) | Usability study participants |
| EQO-ONB-002 | P1 | Onboarding mid-flow abandonment | Kill app; relaunch | Resumes at correct step; no re-entry of stored secrets | Instrumented | OnboardingStore.kt | none |
| EQO-ONB-003 | P1 | Accessibility service enable flow | Grant accessibility permission | Clear explanation before Settings jump; return-to-app continuity | Manual | ServiceReadinessInstrumentationTest.kt | none |
| EQO-ONB-004 | P2 | Usability benchmark | Time-on-task vs documented targets; SUS-style score | Measured baseline recorded (no target claimed here) | Manual | none | Participants |

## 8. Release gates (measurable) — feasibility vs release readiness

Feasibility (can EQO QA be executed at all?): CONDITIONALLY FEASIBLE in CI, NOT on this machine.
- F1 (verified PASS): evidence chain integrity — SHAs match metadata (§1).
- F2 (BLOCKED locally): test execution — no JDK (java: command not found), no device (adb empty). CI has JDK 21 + emulator lanes (android-ci.yml L33-ish setup-java, L104 matrix).
- F3 (verified FAIL at baseline): lint clean — opendroid lint job failure at 6ff5a061 with 16 baseline issues; logs 401 so root-cause text unavailable. EQO must start from a clean lint policy or explicit baseline.
- F4 (feasible): matrix design — all EQO scope areas have at least one viable test layer (§7). Biggest design gaps: in-flight irreversible effects (EQO-PS-004), revoke/reboot distinction (EQO-WADB-006/007), onboarding tests.

Release readiness (should EQO ship?): NOT READY — 0 of the proposed EQO tests exist or have run; no EQO code exists yet (sources/main is docs/agents only; android/README.md only).
Measurable gates for a future release (each pass/fail computable, no metric invented now):
- G1: 100% of §7 P0 cases executed with recorded evidence on API 31 + 36 at minimum.
- G2: lintDebug exits 0 with zero new issues beyond a committed, reviewed baseline.
- G3: unit + instrumented suites green in CI for API 31/33/34/35/36 lanes.
- G4: EQO-SEC-001..003 pass (zero secret leakage — greppable assertion count = 0).
- G5: EQO-WADB-004/006/007 all pass with visibly distinct UI states (screenshot evidence).
- G6: EQO-PS-004 behavior defined in spec AND test passing.
- G7: OEM matrix: P0 suite on at least Samsung + Pixel + one Chinese OEM skin.
- G8: EQO-ONB-001 completed by >=1 novice without assistance.
- G9: branding scan EQO-BRD-001 clean; EQO-BRD-002 legal-notices exception verified.

## 9. Blockers (consolidated)

- B1 (hard, local): No Java SDK — zero Gradle execution possible here. Owner: environment. Unblocks: install JDK 21 (not done — no installs permitted).
- B2 (hard, local): No adb device/emulator attached — all instrumented/manual cases blocked locally. Owner: environment.
- B3: Upstream job logs are 401-authenticated (lint-evidence.json) and artifacts are empty (lint-artifacts.json) — lint failure root cause cannot be established beyond annotation "exit code 1" at L415 of the job log. Confidence in "lint failed" itself: HIGH (T1 metadata consistent).
- B4: "Mandatory wireless ADB pairing" vs "no separate named helper" is architecturally tense — wireless debugging pairing on Android 11+ is a platform feature, but privileged automation (Shizuku pattern) typically needs a helper process; closepaw resolves this via an in-app user-service bridge (ShizukuChromeDevtoolsBridge.kt). Spec must confirm which model EQO adopts before EQO-WADB-008 can be asserted.
- B5: EQO branding assets, legal-notices copy, and irreversible-action policy are not yet defined in sources/main (PRODUCT.md is a placeholder).
- B6: Test-device fleet for OEM variability (§7.10) unavailable.
- B7: No EQO implementation exists yet — entire §7 is proposed; nothing to execute against.

## 10. Ownership & layers (proposed)

| Layer | Scope | Owner (proposed) | Runs where |
|---|---|---|---|
| Unit (JVM) | EQO-PS-001..003,005; EQO-APR-001,003,005; EQO-SEC-001,002; EQO-BYOK-002,004..008; EQO-CDP-005; EQO-WADB-003 | QA lead + dev | CI (JDK 21) |
| Instrumented (emulator) | EQO-WADB-001,002,004,005; EQO-VD-001..004; EQO-CDP-001,002,004; EQO-SEC-006..008; EQO-ONB-002; EQO-BYOK-001,003 | QA lead + dev | CI emulator lanes API 31/33/34/35/36 |
| Manual / on-device | EQO-WADB-006..009; EQO-MGR-001..003; EQO-CDP-003,006; EQO-VD-005; EQO-PS-004,006; EQO-APR-002,004; EQO-SEC-003..005; EQO-BRD-001..003; EQO-OEM-001..004; EQO-ONB-001,003,004 | QA lead + human testers | Lab devices |
| Static analysis | Lint policy, manifest export audit, secret scan of APK | QA lead (CI-gated) | CI |

## 11. Confidence summary

- HIGH: source SHAs/provenance (local git verified); CI job conclusions at 6ff5a061 (T1, internally consistent); logs-401 and empty-artifacts facts; absence of local JDK and device (direct probes); existence/content of listed test files, workflows, baselines (T2 reads).
- MEDIUM: mapping of closepaw tests to EQO concerns (semantic mapping, not execution); lint baseline adequacy (16 issues seen, severity mix not triaged — lint output itself unavailable).
- LOW/NONE: any statement about actual test outcomes of the listed suites on any machine other than the recorded upstream CI metadata. Nothing in this report claims a test "passed" that was not run.

## 12. What was actually verified (audit trail)

1. git rev-parse/log on 5 source trees — SHAs in §1 (read-only).
2. Parsed evidence.json, ci-detail.json, lint-evidence.json, lint-artifacts.json — §4 context CONFIRMED in full (lint failed; unit/debug/release/instrumented-API26/36 succeeded; logs 401; artifacts empty) for opendroid run 33920897530 at 6ff5a061755b597b0558fed1f565587837ed4d51.
3. Executed read-only probes: java (absent), adb devices (empty).
4. Statically inspected: opendroid android-ci.yml (184 lines), lint-baseline.xml (16 issues), test trees (63 unit + 5 instrumented files in opendroid; 205 unit + 25 instrumented in closepaw), SDK levels, network_security_config files, NOTICE/license assets, onboarding package, CDP/wireless/session test files with line anchors in §7.
5. No Gradle task run. No file modified outside team/qa. No config change, install, or publication.

— EQO QA lead worker, 2026-10-01.

## 13. Supervising QA review and runtime verification

The independent CLI exited 0. Its final output identifies session `20261001_115423_849f39`. Read-only SQLite lookup in `C:/Users/<user>/AppData/Local/hermes/state.db` verified that exact session selected `model=mimo-v2.6-pro`, `billing_provider=opencode-go`, `billing_base_url=https://opencode.ai/zen/go/v1`, `source=oneshot`, with 22 tool calls and 13 API calls and end_reason `cli_close`. This verifies Hermes runtime selection, not an independent attestation of the provider's underlying model weights. CLI log: `team/qa/worker-cli-report.log`; invocation prompt: `team/qa/worker-prompt.md`. A nonfatal pre-existing runtime_footer YAML-list warning appeared; no configuration was changed.

Supervisor independently counted test filenames with Python pathlib (63/5 OpenDroid, 205/25 ClosePaw) and parsed lint XML with ElementTree: **16 actual issue elements**, not the worker's original 17 text matches. Report counts corrected; original CLI summary remains unedited as provenance. Baseline issue count does not explain the failed lint job.

The following acceptance clarifications override any weaker wording above:
- EQO-WADB-008 / B4 / §5: hiding or renaming a second helper APK is NOT acceptable. Exactly one branded APK must own integrated manager functionality. A Shizuku client/user-service bridge alone does not prove manager/server embedding or bootstrap feasibility. Treat integrated privileged server startup and authorization as unresolved engineering evidence, not an already proven ClosePaw solution.
- EQO-WADB-006: reboot must distinguish persisted pairing trust, wireless-debugging availability, current connection, privileged server startup and client grant. Never claim a server survives reboot. Inspect each state and present restart/reconnect/re-pair instructions only when actually needed; verify OEM-specific behavior rather than assuming automatic reconnection.
- EQO-WADB-009: helper/client authorization means the integrated privileged service's grant, not installation of a separate helper. Revoking that grant must not erase wireless pairing trust; revoking pairing must not be mistaken for an application grant problem.
- EQO-PS-004/005 and approvals: use a controllable fake side-effect endpoint and dispatch barrier; request Pause/Stop/takeover before dispatch, during dispatch and after commit. No new dispatch after acknowledged Stop/takeover; queued approvals and stale action tokens invalidated. A committed external effect cannot be rolled back by Stop; record it accurately and prohibit automatic replay after reconnect. Fix behavior and timing thresholds before marking these tests pass.
- EQO-BRD-001 / MGR-003: upstream names are prohibited in normal user-facing branding, not legally required notices. Keep required LICENSE/NOTICE attribution and audit redistribution obligations before release.
- G8 strengthened: proposed release gate is five first-time novice participants, at least four completing pairing, BYOK and first safe task unaided in ten minutes, all able to demonstrate Stop and recover from a pairing failure. Record timings and failures; these are proposed thresholds, not measured outcomes.

Supplementary concrete cases (PROPOSED; no execution):

| ID | Priority / owner / layer | Preconditions | Steps | Expected / evidence / blocker |
|---|---|---|---|---|
| EQO-A11Y-001 | P0 / QA+dev / device | Accessibility enabled; harmless queued task | Disable service in Settings while task waits, then resume | Capability becomes unavailable; no dispatch or false success; guided re-enable. Baseline ServiceReadiness test is partial; blocked by device absence. |
| EQO-CDP-007 | P0 / QA+browser dev / device | Fresh Chrome without debugging prep; unsaved form fixture | Start CDP; follow preparation; approve required Chrome restart; reconnect and navigate | No silent kill or claim of live debugging before readiness; warn of unsaved state; form submission requires separate approval. Existing CDP round-trip is partial; device/Chrome fixture missing. |
| EQO-APR-006 | P0 / QA+session dev / unit+device | Risky action pending approval | Stop/takeover; deliver late approval callback; restart session | Stale approval cannot authorize dispatch; new target/action requires new approval. Source approval tests are reuse candidates, not proof of this race; EQO harness missing. |
| EQO-VD-006 | P0 / QA+display dev / device | Virtual display active; resource counters recorded | Deny capture/input grant; kill process; disconnect privileged bridge in separate trials | No wrong-display input; protected screens handled as unavailable; display/threads/sockets released or reconciled on restart. Settings baseline is insufficient; hardware and instrumentation missing. |
| EQO-COMPAT-ALL | P0 / QA / emulator+OEM device | Fresh single EQO APK on API 31,32,33,34,35,36, plus selected OEMs | On each: complete A11Y/BYOK/pairing/CDP/display; inject disconnect, grant revoke, pairing revoke, reboot and Stop race | Each P0 case passes with OS/build/Chrome/APK SHA recorded. API26 success cannot establish Android12 onboarding; API36 alone cannot establish intermediate versions. Devices and EQO implementation absent. |

For §7.10 OEM cases, use the EQO-COMPAT-ALL preconditions/steps/expected protocol and retain per-device evidence. Future release sign-off requires zero open safety/security P0 defects, exact APK hash/signature and source SHA, reproducible build logs and machine-readable test reports; metadata-only green jobs are insufficient.
