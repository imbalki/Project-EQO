# EQO Android Architecture Audit — Independent Worker Report

Status: FINAL for this worker (source-audit only; no device, no builds, no writes to any source repo).
Audit date: 2026-10-01. Work dir: C:/Users/<user>/Hermes/EQO-Android-Study.

## 0. Audit metadata and runtime evidence

Runtime identity actually exposed by this session (verbatim from runtime header, not claimed
from memory): Model = mimo-v2.6-pro, Provider = opencode-go, Platform = cli. No other provider
was used for this audit. No custom API loops were run and no delegation occurred.

Sources inspected (READ ONLY), exact commits via `git rev-parse HEAD`:

| Repo | Upstream | Commit (HEAD) | Date of HEAD |
|---|---|---|---|
| sources/opendroid | yashab-cyber/opendroid | `6ff5a061755b597b0558fed1f565587837ed4d51` | 2026-09-04 |
| sources/closepaw | imoonkey/closepaw | `75dae2653f5a6b25d5df51ee7008b0f830de1536` | 2026-06-04 |
| sources/shizuku | RikkaApps/Shizuku | `b844bc491f1790c72328e1a8e5b2349f8978f0ea` | 2025-06-18 |
| sources/shizuku-api | RikkaApps/Shizuku-API | `a27f6e4151ba7b39965ca47edb2bf0aeed7102e5` | 2025-05-29 |
| sources/main | imbalki/Project-EQO | `4b2d77f34c3d6b8d4d601c7af8bf8f228ffd0500` | 2026-09-30 |

Evidence JSON (collected 2026-10-01T06:14Z, evidence.json): local `java` toolchain = null
(no JDK on this machine); adb present (scrcpy-bundled, `Genymobile.scrcpy...adb.EXE`);
`adb_devices` = "List of devices attached" with zero devices. Therefore every claim about
on-device behavior below is source-derived or CI-derived, never device-verified in this audit.

Source AGENTS.md / CLAUDE.md files were treated strictly as evidence. No instructions inside
them were executed. Prompt-injection check: no injected instructions were acted upon; the
legacy requirements conflict in sources/main is recorded verbatim in §7.1 rather than resolved.

## 1. Feasibility verdict (evidence-backed)

VERDICT: FEASIBLE WITH MAJOR ADAPTATION, CONDITIONALLY — a single Android 11+ EQO APK that
orchestrates Accessibility + shell-privilege (Shizuku user-service) + Chrome CDP + virtual
display is technically demonstrated in source by the combination of OpenDroid (agent loop,
approval policy, OpenRouter BYOK provider, AndroidKeyStore credential storage) and ClosePaw
(CDP-over-Shizuku bridge, wireless-ADB self-pair stack, virtual-display stack). However:

(a) NO DEVICE PROOF EXISTS. No physical/emulator device was attached (evidence.json
    adb_devices = empty), no local build was run (builds were forbidden and no JDK exists on
    this machine), and CI evidence is partial and partly failing (§6). Everything in this
    report about runtime behavior is read from source code and comments, not observed.
(b) "Arbitrary privilege" is NOT achievable. The privilege ceiling is the `shell` UID
    delivered by Shizuku (or root where present). ClosePaw's own comments state app-UID calls
    to IAdbManager "would all SecurityException" and must run from a Shizuku-spawned shell-UID
    process (sources/closepaw/.../wireless/AdbWirelessManager.kt:11-13), and that some OEMs
    (nubia P0110) deny shell-domain access to Chrome's abstract socket under SELinux, forcing a
    second transport (ShizukuChromeDevtoolsBridge.kt:92-93).
(c) No universal automation guarantee is defensible. CDP covers Chrome only
    (chrome_devtools_remote socket, ShizukuChromeDevtoolsBridge.kt:140), virtual display
    depends on hidden APIs with API-33 signature splits (ShizukuDisplayTransport.kt:45-55),
    and Accessibility depends on app UI trees. The brief's "no universal automation guarantee"
    is consistent with what the source can actually deliver.

The verdict is therefore: buildable as a sideloaded (not Play-store-safe) APK for Android 11+
(minSdk 30 per D-007 / ADR-0003; ClosePaw, the donor, itself targets minSdk 31 — closepaw/
app/build.gradle.kts:18-20, a donor fact and not an EQO requirement, so donor code may use APIs
above 30 and compile/lint in TASK-003/004/007 must surface them), with the
helper/setup boundary described in §4 and trust boundary in §7.2 — but acceptance must be
gated on the real-device gates in §8 before any capability claim is made to users.

## 2. Architecture / module / process / IPC diagram (text)

```
[ User device, Android 11+ (API 30+) ]

  EQO MAIN APK  (one APK, com.eqo.* — single Kotlin orchestrator)
  ├─ UI (Compose) + OrchestratorService (foreground service; OpenDroid pattern:
  │     OpenDroidService fgs microphone|specialUse — opendroid AndroidManifest.xml:100-108)
  ├─ Kotlin orchestrator core (proposed, §5; OpenDroid AgentLoop as skeleton:
  │     core/agent/AgentLoop.kt:96-99,247 job-cancel; AutoApprovalPolicy.kt:5-23;
  │     ActionRisk.kt:5-36)
  ├─ LLM gateway: OpenRouterProvider BYOK (opendroid core/llm/providers/OpenRouterProvider.kt:28-37)
  │     key retrieved per request from ProviderCredentialStore (AndroidKeyStore AES-256-GCM,
  │     core/security/KeystoreSecretStorage.kt:95-111)
  │
  ├─ ACCESS LAYER A — AccessibilityService (in-APK process)
  │     opendroid .accessibility.OpenDroidAccessibilityService (Manifest:113-119)
  │     closepaw .app.AgentService (closepaw Manifest:63-71)
  │     IPC: OS binder to AccessibilityManagerService. Capabilities: UI-tree read/click/
  │     gesture dispatch. NO shell, NO file, NO raw input to other displays, NO Chrome internals.
  │
  ├─ ACCESS LAYER B — Shizuku UserService (helper process spawned in SHELL UID)
  │     AIDL: closepaw app/src/main/aidl/ai/closepaw/browser/cdp/shizuku/
  │       IChromeDevtoolsUserService.aidl
  │     Host impl: ChromeDevtoolsUserService.kt:33-56 (runs under shell UID; hard-wired
  │       abstract socket name = privilege-escalation guard, :23-28)
  │     Binder delivery: rikka.shizuku.ShizukuProvider (opendroid Manifest:92-97;
  │       closepaw Manifest:74-80), permission moe.shizuku.manager.permission.API_V23
  │     Capabilities: shell commands (PrivilegedCommandExecutor.kt:12-48 backend cascade
  │       SHIZUKU→ROOT→APP_SHELL), hidden system-service calls, IAdbManager (MANAGE_DEBUGGING),
  │       IDisplayManager/IInputManager/IActivityTaskManager via ShizukuBinderWrapper
  │       (ShizukuClient.kt:11-18), /data/local/tmp writes (CommandLineWriter.kt:8-11).
  │
  ├─ ACCESS LAYER C — Chrome CDP (browser-only)
  │     Setup: write /data/local/tmp/chrome-command-line with
  │       "_ --remote-debugging-socket-name=chrome_devtools_remote --enable-features=NetworkService"
  │       (CommandLineWriter.kt:43-55) + user flips chrome://flags#enable-command-line-on-
  │       non-rooted-devices (ChromeFlagDeepLink.kt). Probe: /proc/net/unix token match with
  │       shell fallback (ChromeCdpProbe.kt:18-40).
  │     Runtime: HTTP /json/version + /json/list (ShizukuChromeDevtoolsBridge.kt:45-55),
  │       WS relay: device-side TCP relay on 127.0.0.1, token-gated WS Upgrade
  │       X-ClosePaw-Token (ChromeDevtoolsUserService.kt:261-271,300-326; bridge :19-23).
  │     Transport cascade: USER_SERVICE (shell-UID proxy to abstract socket) →
  │       WIRELESS_ADB_SELF_PAIR (adbd route) — ShizukuChromeDevtoolsBridge.kt:8-11,88-116.
  │
  ├─ ACCESS LAYER D — Virtual display (any app, offscreen)
  │     create: hidden IDisplayManager.createVirtualDisplay via reflection, API33
  │       VirtualDisplayConfig$Builder path vs legacy path (ShizukuDisplayTransport.kt:118-181,
  │       185-249, 264-315), hidden-API exemption HiddenApiBypass (ShizukuRuntimeGateway.kt:10).
  │     launch: hidden ActivityOptions launch-display (ShizukuActivityLauncher.kt:7-19).
  │     input: IInputManager.injectInputEvent (ShizukuInputTransport.kt:14-19) with shell
  │       `input tap/swipe --display` fallback (VirtualDisplayPlatform.kt:~295-300).
  │     capture: ImageReader headless vs SurfaceView live preview switch
  │       (VirtualDisplayPlatform.kt:264-284), PixelCopy path (VirtualDisplayCaptureCoordinator.kt).
  │     destroy: releaseVirtualDisplay + display-scoped task cleanup via IActivityTaskManager
  │       (ShizukuDisplayTransport.kt:95-113; ShizukuActivityTaskTransport.kt:4-6;
  │       VirtualDisplayPlatform.kt:209-261).
  │     lifetime: VdLifecycleArbiter states Stopped/Running/Draining/Broken
  │       (VdLifecycleArbiter.kt:11-22); binder death → Broken + resource rollback
  │       (VirtualDisplayPlatform.kt:132-196).
  │
  └─ IPC summary: EQO app UID ⇄ (Shizuku binder, permission-gated) ⇄ shell-UID user service;
        EQO app UID ⇄ 127.0.0.1 token-gated TCP relay ⇄ adbd or shell-UID ⇄ abstract socket
        chrome_devtools_remote ⇄ Chrome.

[ Helper / privilege plane — OUTSIDE the main APK ]

  Privilege server (Shizuku server pattern, shizuku/server/src/main/java/rikka/shizuku/server/
  ShizukuService.java): started by adb shell (wireless debugging) or root via
  moe.shizuku.starter.ServiceStarter (shizuku/starter/.../ServiceStarter.java:1-40). Lives in
  the adb shell domain. Process dies on reboot; must be restarted (§4).
  Shizuku Manager (shizuku/manager) supplies: wireless-ADB pairing UX (AdbPairingService
  foreground service, adb/AdbPairingService.kt:21,107), mDNS discovery (adb/AdbMdns.kt),
  server start UI (home/StartWirelessAdbViewHolder.kt), binder/permission delivery.

[ Wireless ADB pairing plane — SEPARATE from app authorization ]

  discovery (mDNS _adb-tls-pair._tcp / _adb-tls-connect._tcp;
    shizuku/manager/.../adb/AdbMdns.kt; closepaw ProcNetTcpListeners.kt /proc/net/tcp diff,
    AdbWirelessManager.kt:42-52)
  → pair port: TLS 1.3 + SPAKE2-PSK (AdbPairingTls.kt:29-48 TLSv1.3/Conscrypt;
    AdbPairingClient.kt:32-66; Spake25519.kt; names/HKDF per AOSP AdbPairingClient.kt:97-100)
  → peer info exchange: RSA-2048 android_pubkey written by adbd to
    /data/misc/adb/adb_keys (AdbCryptoKeyStore.kt:31-57; AdbWirelessManager.kt:56-63)
  → connect port: plain TCP A_CNXN → A_STLS → TLS 1.3 mTLS (cert validated against
    adb_keys) → A_CNXN (AdbTlsClient.kt:21-35) → A_OPEN localabstract:chrome_devtools_remote
    (WirelessAdbSelfPairTransport.kt:24-28).
```

## 3. Reuse-versus-build table (source-cited)

| Module | Source (file:line) | Reusable as-is? | Required work |
|---|---|---|---|
| Wireless ADB pairing (SPAKE2/TLS/adb protocol/mTLS client) | closepaw `browser/cdp/wireless/` AdbPairingClient.kt:32-66, AdbPairingTls.kt:29-48, AdbTlsClient.kt:21-35, AdbProtocol.kt:21-22, Spake25519.kt, AndroidPubkey.kt, AdbCryptoKeyStore.kt:31-57 | HIGH reuse — self-contained Kotlin, no Android UI | Rebrand, wire key storage policy, unit-test vectors vs AOSP; QA on Android 11–16 |
| Wireless-ADB self-pair transport + token-gated WS relay | closepaw WirelessAdbSelfPairTransport.kt:24-28,80+; ChromeDevtoolsUserService.kt:261-326 | HIGH reuse | Rename token header (X-ClosePaw-Token), audit relay thread lifecycle on service destroy |
| CDP HTTP/WS bridge + cascade transport | closepaw ShizukuChromeDevtoolsBridge.kt:8-11,88-116,139-144; DevtoolsHttpProtocol.kt; ChromeCdpClient.kt | HIGH reuse (Chrome-only scope) | Model/tool mapping for EQO actions; verification hooks |
| Chrome debug-socket setup | closepaw CommandLineWriter.kt:43-55, ChromeFlagDeepLink.kt, ChromeCdpProbe.kt:18-40 | HIGH reuse | EQO UX flow; the chrome://flags user step cannot be removed (source never force-stops Chrome, CommandLineWriter.kt:8-11) |
| Virtual display create/launch/input/capture/destroy | closepaw platform/virtualdisplay/: ShizukuDisplayTransport.kt:118-315, ShizukuInputTransport.kt:14-19, ShizukuActivityLauncher.kt:7-19, ShizukuActivityTaskTransport.kt:4-6, VirtualDisplayPlatform.kt:132-320, VdLifecycleArbiter.kt:11-52 | MEDIUM reuse | Hidden-API surface is Android/OEM-version-sensitive (API33 signature split at ShizukuDisplayTransport.kt:45-55); must be retested per OS release; Play-store distribution risk |
| Shizuku binder/user-service plumbing | closepaw ShizukuUserServiceProvider.kt (bind timeouts, single-flight, idempotent close: lines 28-68), ShizukuRuntimeGateway.kt:20-80, ShizukuClient.kt:11-70; rikka.shizuku.ShizukuProvider (opendroid Manifest:92-97) | HIGH reuse | Re-consent handling after `adb install -r` UID change already documented (ShizukuRuntimeGateway.kt:52-70) |
| Privilege server + starter | shizuku server/ (ShizukuService.java, ShizukuUserServiceManager.java), starter/ServiceStarter.java:1-40 | REBRANDABLE (Apache-2.0, §7.6) | "EQO-branded helper" = fork/rebrand of Shizuku Manager+server; must keep attribution (LICENSE header preserved in all four repos' LICENSE files); maintainership burden is substantial |
| Shell command execution cascade | opendroid PrivilegedCommandExecutor.kt:12-48 | MEDIUM reuse | Add allowlist + audit log; today it runs arbitrary strings with `sh -c`/`su -c` |
| LLM BYOK OpenRouter | opendroid OpenRouterProvider.kt:28-37 (per-request key from settings) | HIGH reuse | Hardcode OpenRouter as sole provider per brief; strip others; model list is stale (`google/gemini-2.0-flash-exp:free`, OpenRouterProvider.kt:27) and must be updated |
| Credential storage | opendroid KeystoreSecretStorage.kt:95-111, ProviderCredentialStore.kt:25-86; closepaw RelayAuthToken.kt (183 lines, per-session token) | HIGH reuse | Redaction audit of logs (both repos log extensively via android.util.Log) |
| Approval policy / risk model | opendroid AutoApprovalPolicy.kt:5-38, ActionRisk.kt:5-36, ActionSchema.kt:12,204+ (neverAutoApprove flags) | MEDIUM — skeleton exists | Brief demands explicit side-effect approvals + cancellation + verification; OpenDroid has plan-level approval and job-cancel (AgentLoop.kt:247,336) but NO postcondition verification and NO per-side-effect envelope → build (§5) |
| Agent loop | opendroid core/agent/AgentLoop.kt (currentJob cancel, CancellationException handling :311,352,479) | LOW-MEDIUM | Substantial rewrite to a single action-envelope orchestrator; OpenDroid loop is plan/fallback oriented (AutoApprovalPolicy.kt:24-31) |
| Accessibility actions | opendroid accessibility/ (OpenDroidAccessibilityService.kt, AccessibilityNodeTraversal.kt), actions/ | MEDIUM reuse | EQO action set narrowing; per-app reliability is the evergreen problem (no code fixes that) |
| Termux Python bridge | closepaw build.gradle.kts:107-117 (copyClosePawBridge → closepaw_bridge_py), Manifest `com.termux.permission.RUN_COMMAND` (closepaw Manifest:5) | EXCLUDE | Conflicts with brief "no Node/Python runtime on phone" (it delegates to a Termux install); drop |
| Leap SDK local inference | closepaw app/build.gradle.kts:13-20 (minSdk 31 "Required by LiquidAI Leap SDK" — donor fact: it forces ClosePaw's own minSdk 31) | EXCLUDED by owner decision D-007 | EQO does not use the LiquidAI Leap SDK; the brief mandates OpenRouter BYOK, so the donor's Leap dependency is not carried over and the EQO floor is minSdk 30 (ADR-0003) |

Substantial adaptation summary: everything "agent semantics" (action envelope, approvals,
cancellation semantics, verification) is BUILD; everything "device access plumbing" (pairing,
CDP relay, virtual display, Shizuku plumbing) is REUSE with rebranding and per-OS QA.

## 4. Setup state machine (detailed)

Two INDEPENDENT authorization planes. Conflating them is the most common design error.

PLANE 1 — Wireless ADB pairing (adbd trust; persists across reboots in /data/misc/adb/adb_keys):

```
S0 NOT_CONFIGURED
  → user opens EQO setup; EQO requests notification/overlay perms as needed
S1 DISCOVERY          (find pair port: mDNS _adb-tls-pair._tcp or /proc/net/tcp diff —
                       AdbWirelessManager.kt:42-52; if Shizuku server already running,
                       EQO can call IAdbManager.enablePairingByQrCode via shell-UID binder)
  → obtain PSK: either user types the 6-digit pairing code from Developer options
    (manual pairing), or self-pair via enablePairingByQrCode(name, psk) (AdbWirelessManager.kt:44-52)
S2 PAIRING_HANDSHAKE  TLS 1.3 + SPAKE2-PSK on pair port (AdbPairingClient.kt:32-66);
                      exchange PEER_INFO (RSA-2048 pubkey + GUID)
S3 KEY_DEPOT          adbd appends <base64 android_pubkey> <name> to /data/misc/adb/adb_keys
                      (AdbCryptoKeyStore.kt:53-57). EQO stores its private key + self-signed
                      cert in app-private storage (AdbCryptoKeyStore.kt:31-33).
S4 PAIRED             verify by substring-matching adb_keys (tri-state: AUTHORIZED / NOT /
                      UNREADABLE — AdbWirelessManager.kt:56-63+)
S5 CONNECT            to _adb-tls-connect port: A_CNXN → A_STLS → mTLS (cert must match
                      adb_keys) → wait A_CNXN (AdbTlsClient.kt:21-35)
S6 CHANNEL_OPEN       A_OPEN localabstract:chrome_devtools_remote (or shell: for general
                      shell access the same mTLS channel carries shell streams)
Failure edges: S2 fail → re-show pairing UI; S4 UNREADABLE (OEM drops shell from `adb` group)
  → fall back to manual pairing; S5 fail after cached-pair skip → invalidate PairOnceCache
  and force re-pair (WirelessAdbSelfPairTransport.kt:44-64).
REBOOT: adb_keys persists → S4 still AUTHORIZED; but wireless debugging toggle resets →
  re-run S5 enablement (BSSID-scoped on Android 14+, AdbWirelessManager.kt:24-31).
REVOCATION: delete our key line from adb_keys (needs shell), or Developer options
  "Revoke USB debugging authorizations"; EQO should expose "Forget device key" which wipes
  local private key and instructs the user to revoke.
```

PLANE 2 — Helper/privilege authorization (Shizuku binder consent; per-UID):

```
H0 HELPER_ABSENT
  → install helper (rebranded Shizuku Manager or user-installed Shizuku), then start server:
    via wireless ADB (Plane 1 must be at S6) or root (ServiceStarter.java:1-40)
H1 SERVER_RUNNING     server process lives in adb shell domain; binder published to apps
H2 CONSENT            Shizuku permission dialog; grant is keyed by UID and stored in
                      /data/local/tmp/shizuku/shizuku.json (documented at
                      closepaw ShizukuRuntimeGateway.kt:52-70). `adb install -r` changes the
                      app UID → consent silently stale → EQO must call requestPermission again
                      (requestPermissionAndAwait, ShizukuRuntimeGateway.kt:52-80)
H3 AUTHORIZED         user-service spawn (Shizuku.bindUserService) now permitted
REBOOT: server process dies → back to H1 transition (must re-start); consent file persists.
BINDER DEATH: OnBinderDeadListener (ShizukuClient.kt:45-55) → VdState.Broken + rollback
  (VirtualDisplayPlatform.kt:174-196); CDP transport falls back USER_SERVICE→WIRELESS_ADB
  (ShizukuChromeDevtoolsBridge.kt:88-116).
```

PLANE 3 — Chrome CDP readiness (browser-only gate): user flips
chrome://flags#enable-command-line-on-non-rooted-devices → EQO writes /data/local/tmp/
chrome-command-line (CommandLineWriter.kt:43-55, idempotent) → user restarts Chrome →
probe /proc/net/unix for @chrome_devtools_remote (ChromeCdpProbe.kt:18-40) → Bound.

## 5. Single Kotlin orchestrator — contract (PROPOSED, does not exist yet)

Everything in §5 is a proposed design built on existing skeletons (cited), not existing code.

Action envelope (proposed data class, kotlinx-serialization):

```kotlin
// PROPOSED — not in any source tree
@Serializable data class ActionEnvelope(
  val actionId: String,            // unique, idempotencyKey = hash(actionId+params)
  val capability: Capability,      // A11Y | SHELL | CDP | VIRTUAL_DISPLAY | READ_ONLY
  val verb: String,                // e.g. "chrome.navigate", "vd.tap", "shell.exec"
  val params: JsonObject,
  val declaredSideEffects: List<SideEffect>,   // SENDS_MESSAGE | PURCHASE | DELETES_DATA ...
  val risk: RiskLevel,             // mirror ActionRisk.kt:5-14 (READ_ONLY..IRREVERSIBLE)
  val approvalPolicy: ApprovalPolicy,          // ALWAYS | NEVER_AUTO | SESSION_GRANT
  val preconditions: List<Probe>,  // e.g. cdpSocketBound, shizukuAuthorized
  val postcondition: Probe?,       // read-back check executed after the action
  val timeoutMs: Long,
  val retry: RetryPolicy? = null   // retries only when idempotent=true
)
```

Approval design (adapted from opendroid AutoApprovalPolicy.kt:5-38 and ActionSchema.kt:12,204+
neverAutoApprove flags): every envelope with declaredSideEffects non-empty or risk >= SENSITIVE
requires explicit per-action user approval unless a session grant covers that exact verb.
neverAutoApprove verbs (payments, credential changes, message sends to humans, account/auth
changes — ActionSchema.kt marks e.g. lines 204,226,313 as neverAutoApprove) are NEVER
auto-approved and a session grant cannot cover them. YOLO/all-auto mode is OUT of scope for
EQO per the brief's explicit-approval mandate (note: OpenDroid ships a YOLO mode,
AutoApprovalPolicy.kt:16-17 — deliberately not carried over).

Cancellation design (mark UNCERTAIN where source is uncertain): cancellation is cooperative
(Kotlin Job cancel as in AgentLoop.kt:247,336 with CancellationException handling :311,352).
Semantics contract: cancel stops the orchestrator from issuing NEW envelopes and stops
retries; an in-flight non-atomic side effect (e.g. a message already handed to
ACTION_SEND) is NOT guaranteed to stop — the envelope result is then
CANCELLED_UNVERIFIABLE. Atomic verbs (vd.tap in progress, cdp.navigate before commit) are
interruptible. This mirrors real behavior seen in source: OpenDroid cancels the coroutine but
has no compensation logic (AgentLoop.kt — no rollback symbols), and ClosePaw's VD arbiter
drains in-flight ops before teardown (VdLifecycleArbiter.kt:24-52) — the same pattern applies
to orchestrator cancellation.

Duplicate-side-effect defense: idempotencyKey checked against a persisted (Room) executed-
action ledger before dispatch; a cancelled-then-retried action with the same key must be
rejected or re-approved, never auto-resent. (No such ledger exists in either source: BUILD.)

Postcondition verification: after each envelope, run its postcondition Probe through an
INDEPENDENT access layer where possible (e.g. verify a CDP click via VD screenshot, or via
/accessibility tree), and record VERIFIED / FAILED / UNVERIFIABLE with evidence (screenshot
hash, JSON read-back). Status surfaced per action. (Neither repo implements result
verification — OpenDroid returns ActionResult (actions/base/ActionResult.kt) without checks;
BUILD.)

Result reporting: orchestrator emits ResultEnvelope(actionId, outcome, verifiedState,
evidenceRefs, residualSideEffects). Credentials never appear in either envelope; log redaction
layer mandatory (audit risk: both codebases log via android.util.Log extensively).

## 6. Build / toolchain assessment (source + evidence JSON only; CI ≠ local ≠ runtime)

Local machine (evidence.json): NO JDK (`local_tools.java: null`), adb exists, zero devices.
No local build was attempted (build writes forbidden by brief). Hence: zero local build proof.

Per-repo toolchain (source-cited):

| Repo | AGP | Gradle | Kotlin | Java/JVM | SDK (min/target/compile) | Native | Citation |
|---|---|---|---|---|---|---|---|
| opendroid | 9.3.1 | 9.7.0 (sha256-pinned) | 2.4.0 | 21 | 26 / 36 / 36 | none | opendroid/build.gradle:14-25, gradle-wrapper.properties:1-5, app/build.gradle:12-19,88-90 |
| closepaw | 8.9.1 | 8.11.1 | 2.3.0 | 17 | 31 / 36 / 36 | none (AIDL for Shizuku) | closepaw/build.gradle.kts:9-14, app/build.gradle.kts:13-30,72-75 |
| shizuku | (root) | 8.14 | — | 21 | 24 / 36 / 36 | NDK 29.0.13113456, CMake (manager) | shizuku/build.gradle:12-21, manager/build.gradle:18-19,44-51 |
| shizuku-api | — | 8.14 | — | 11 (api module) | — | rish C++ (CMakeLists) | shizuku-api/api/build.gradle:16-17, rish/src/main/cpp/ |

Integration conflicts to resolve when merging into one EQO APK: AGP 9.3.1/Kotlin 2.4.0
(opendroid) vs AGP 8.9.1/Kotlin 2.3.0 (closepaw) — pick one (AGP 9.x line is the forward path;
closepaw's license plugin 0.9.8 has known config-cache breakage, closepaw/app/build.gradle.kts:
140-148); JVM target 17 vs 21; ClosePaw's minSdk-31 Leap dependency (donor fact — it forces ClosePaw's own minSdk 31) is not carried over: the LiquidAI Leap SDK is EXCLUDED by owner decision D-007 and EQO does not use it (§3).

CI evidence (ci-detail.json, evidence.json) — CI results are CI-only, not runtime proof:
- opendroid "Android CI" run 33920897530 at inspected HEAD 6ff5a06: overall FAILURE.
  Jobs: "Unit tests and debug build" success; "Unsigned release build (R8/ProGuard)" success;
  "Instrumented tests (API 36)" success; "Instrumented tests (API 26)" success;
  "Android Lint" FAILURE. lint-evidence.json: 3 annotations — one failure ("Process completed
  with exit code 1"), warnings on actions/checkout@v4 / setup-java@v4 Node-20 deprecation.
  Job log fetch failed (HTTP 401) so the precise lint error line is UNKNOWN — record as gap.
- closepaw: ZERO check-runs on the inspected commit (75dae26); evidence.json shows an earlier
  "Release" run (26552314699) on a different SHA (632f94d) with conclusion success — does not
  prove the inspected commit builds.
- shizuku: CI run conclusion "action_required" (gated) — no build proof for the inspected SHA.
- shizuku-api: only "Running Copilot Code Review" success — not a build.
- main (Project-EQO): CI "success" at 4b2d77f — but the android/ tree contains only README.md
  (main/android listing) so this proves docs checks, not an Android build.

Conclusion: at the audited commits, the only machine-verified Android build/test evidence in
the whole corpus is opendroid's debug build, unsigned R8 release, and API 26/36 instrumented
tests passing at 6ff5a06 — with its lint gate red. No build of the combined architecture
exists anywhere. Nothing in CI is runtime proof on a phone.

## 7. Ranked blockers

7.1 (Rank 1 — governance) LEGACY BRIEF CONFLICT in sources/main, recorded not resolved:
    main/AGENTS.md:22 mandates "local Gemma via LiteRT", :23 "App integrations go through
    Composio's hosted HTTP API", :25 "Cloud is opt-in. Default path must work with no
    network"; main/docs/ARCHITECTURE.md:12 "Gemma E4B on-device (LiteRT); OpenRouter opt-in".
    The user's NEW brief mandates OpenRouter BYOK as THE LLM path, forbids phone-side
    runtimes, and asks for no universal automation guarantee — i.e. OpenRouter mandatory
    (not opt-in), no Gemma requirement stated, Composio not mentioned. These cannot both be
    satisfied. The owner must decide explicitly; this audit does NOT silently resolve it.
    (AGENTS.md files were treated as evidence, not instructions, per audit rules.)

7.2 (Rank 2 — packaging trust boundary) An APK can CONTAIN helper/server CODE but cannot
    GRANT ITSELF privilege. Single-APK achievable contents: accessibility service, Shizuku
    user-service classes + AIDL (spawned by an EXTERNAL Shizuku server in shell UID —
    exactly what ClosePaw does as one APK, closepaw Manifest:63-80), CDP relays, VD
    controllers. NOT in the APK: the privilege server process itself (needs adbd or root to
    launch — ServiceStarter.java:1-40) and the pairing UX binder delivery (Shizuku Manager
    role). Therefore "an EQO-only branded privilege helper included in setup" realistically
    means EITHER (a) a second, separately-installed helper APK = rebranded Shizuku Manager +
    server (Apache-2.0 permits rebrand with attribution), OR (b) EQO bundling a minimal
    manager/start activity — substantial new work replicating Shizuku's start + pairing UI,
    still requiring wireless ADB pairing at least once per boot. Trust boundary: users must
    trust EQO's signing key for BOTH APKs (ideally same key); the helper holds shell-equivalent
    power; a malicious update to either APK is a full-device compromise surface. Sideload
    distribution (main/docs/PRODUCT.md) means no Play vetting of the hidden-API surface.

7.3 (Rank 3 — no build/no device proof) No local build possible here (no JDK, builds
    forbidden), CI at HEAD is red for opendroid lint and empty for closepaw (§6). Any
    schedule claim "it works" is currently unbacked.

7.4 (Rank 4 — hidden API / OEM fragility) Virtual display + input injection rely on
    reflection over hidden system interfaces with API-33 signature splits
    (ShizukuDisplayTransport.kt:45-55,185-249) and HiddenApiBypass (ShizukuRuntimeGateway.kt:10).
    OEM SELinux already breaks shell→abstract-socket on nubia P0110
    (ShizukuChromeDevtoolsBridge.kt:92-93; ChromeDevtoolsUserService.kt:22-28) and breaks
    app-uid /proc reads (ChromeCdpProbe.kt:28-40). Android 11→16 drift must be assumed (EQO's floor is Android 11).

7.5 (Rank 5 — scope ceilings) CDP covers Chrome only; requires a manual chrome://flags flip;
    socket lifetime is tied to the Chrome process and to the debug flag surviving restarts.
    Payments/auth/2FA apps: virtual display + injection can drive them but SHOULD NOT (risk
    IRREVERSIBLE per ActionRisk.kt:33-36 policy direction); brief already excludes guarantees.

7.6 (Rank 6 — licensing) Licenses: shizuku-api = MIT (LICENSE), shizuku = Apache-2.0,
    closepaw = Apache-2.0 + NOTICE (third-party inventory generated at build,
    closepaw/NOTICE:8-24), opendroid = Apache-2.0 text WITH a custom "OpenDroid License &
    Copyright Notice" header (opendroid/LICENSE:1-5) and GitHub reports NOASSERTION
    (evidence.json) — legal review needed before reusing OpenDroid code verbatim. Rebranding
    Shizuku as "EQO helper" is license-allowed but attribution must be preserved.

7.7 (Rank 7 — cancellation/verification gaps) Neither source implements postcondition
    verification or idempotent side-effect ledgers (§5). Cancel semantics for non-atomic
    actions are inherently uncertain; shipping without the CANCELLED_UNVERIFIABLE state would
    be dishonest.

7.8 (Rank 8 — credential hygiene) Storage is strong (AndroidKeyStore AES-GCM,
    KeystoreSecretStorage.kt:95-111; per-session relay token, ShizukuChromeDevtoolsBridge.kt:19-23
    with slowloris/token gate ChromeDevtoolsUserService.kt:300-326) but log redaction across
    both codebases is not evidenced; CDP relay binds 127.0.0.1 only (ChromeDevtoolsUserService.kt:261)
    which is the correct scope.

## 8. Real-device acceptance gates (all must pass before any capability claim)

1. Android 11+ matrix: API 30, 31, 33, 34, 35/16 emulator + ≥2 OEM physical devices (one
   Samsung/Xiaomi, one "other" e.g. Nothing/OnePlus), including the owner's physical Realme
   Narzo 20 (Realme UI 2.0, Android 11) as the OEM-skin device; label each result emulator or
   physical. Gate: setup completes on all.
2. Chrome gate: stock Chrome stable + ≥1 Chrome variant (e.g. Chrome Beta, Bromite-class is
   NOT covered by the devtools-socket path — record as unsupported). Gate: CDP
   /json/version fetch + one verified navigation + one verified screenshot.
3. OEM SELinux gate: reproduce the nubia-class failure (shell→abstract socket denied) and
   confirm the WIRELESS_ADB_SELF_PAIR fallback engages (ShizukuChromeDevtoolsBridge.kt:88-116).
4. Reboot gate: reboot phone with helper enabled → Shizuku server is dead → EQO offers and
   completes re-start via wireless ADB without a PC; adb_keys still authorized (S4 persists);
   wireless-debugging re-enable (BSSID scoping) works on Android 14+.
5. Network gate: Wi-Fi off (cellular only) → self-pair must FAIL CLEANLY with actionable
   error ("No Wi-Fi BSSID available", AdbWirelessManager.kt:24-31), never hang; Wi-Fi
   BSSID change mid-session → reconnect or clean error.
6. Pairing & permission denial gate: user declines pairing; user declines Shizuku binder
   consent; user revokes (adb_keys revoke + Shizuku deny). Each → app remains usable in a
   degraded mode with explicit capability badges, no crash loops (re-consent after
   adb install -r UID change — ShizukuRuntimeGateway.kt:52-70 — must be exercised).
7. Socket lifetime gate: kill Chrome mid-CDP-session; toggle chrome://flags off; restart
   Chrome without the flag. Probe must report NotBound/Unknown truthfully
   (ChromeCdpProbe.kt:36-40 tri-state) and the relay must not leak threads/sockets
   (ChromeDevtoolsUserService.kt:275-277 stop path).
8. Virtual display destruction gate: create VD → launch app → inject tap → capture →
   destroy. Verify display count returns to baseline, tasks removed
   (ShizukuDisplayTransport.kt:95-113 + ShizukuActivityTaskTransport.kt:4-6), ImageReader
   closed, no orphan surfaces after binder death (VirtualDisplayPlatform.kt:174-196) and
   after process kill of EQO (OS-level reclaim).
9. Cancellation/duplicate gate: cancel during (a) CDP navigate, (b) multi-step plan,
   (c) shell command; verify exactly-once side effects for message-send-type actions via the
   idempotency ledger; verify CANCELLED_UNVERIFIABLE is reported when a non-atomic action
   raced the cancel; verify no auto-retry of non-idempotent verbs.
10. Credential leakage gate: full logcat capture during setup + one full task; grep for the
    OpenRouter key, adb private key material, relay token, PSK — zero hits; backup/
    dataExtraction paths must not include key material (note: opendroid Manifest:79-82 sets
    allowBackup="true" — EQO must set allowBackup="false" as ClosePaw does, closepaw
    Manifest:28).
11. Foreground/background gate: EQO backgrounded during a long task; FGS must hold (OpenDroid
    fgs types Manifest:32-35,100-108 pattern); task completion + notification verified;
    no silent restart duplicates.

## 9. Limitations (concise)

- NO DEVICE PROOF: zero attached devices (evidence.json); all behavior claims are source/CI
  derived. No APK was built (forbidden) and no JDK exists locally (evidence.json java=null).
- CI is not runtime: opendroid lint red at HEAD with unknown root cause (job log 401);
  closepaw has zero CI on the inspected commit; shizuku CI gated.
- Chrome CDP scope = Chrome with the debug flag enabled; other browsers/webviews uncovered.
- Hidden APIs may break on any Android/OEM update; per-release re-validation required.
- Legacy-vs-new brief conflict (§7.1) is unresolved by design and blocks a final stack choice
  (Gemma/LiteRT/Composio vs OpenRouter-only).
- OpenDroid license has a custom notice header (NOASSERTION) — legal review pending.
- This report covers architecture/access/security surfaces; it is not a line-by-line code
  review and does not certify any module as production-ready. Existing drafts in reports/
  and other team/ folders were NOT trusted or used as sources.
