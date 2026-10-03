# TASK-008: Wireless ADB pairing on the phone (S2) — evidence

Issue #13. Branch `agent/android/13-adb-pairing`, worktree `C:\Users\<user>\Claude\worktrees\task-008`.
Base: `origin/main` @ `f84e079` (TASK-007 helper #51 + TASK-012 loop #52). Owner decisions applied:
Android floor 11 (minSdk 30), phone is project-only. Lead instruction for this run: owner is away,
so all device work is recorded as **PENDING owner presence** and the device test plan below carries
the exact commands. Sections 1-5 preserve the original implementation record;
**section 6 supersedes the dependency pin, base and gate status: alignment fixed;
its final loopback-only disposition subsection is current. The remaining full-gate
blocker is Bouncy Castle's upstream trust-all EST helper.**

## 1. What was built

New module `:adb-pairing` (`android/adb-pairing/`), namespace `ai.eqo.adb.pairing`:

**Extracted ClosePaw wireless-ADB pairing stack (12 production files, 2 adapted tests).**
Donor: `imoonkey/closepaw` @ `75dae2653f5a6b25d5df51ee7008b0f830de1536`, package
`app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/`. Files: AdbPairingPacket, AdbPairingTls,
AdbPairingClient, AdbProtocol, AdbTlsClient, AdbWireProtocolClient, Spake25519, AndroidPubkey,
TlsExporter, AdbCryptoKeyStore, WirelessAdbProviders, PairOnceCache (plus AdbPairingPacketTest and
AdbProtocolTest adapted from the donor tests to JUnit asserts). Dependency pins are the donor's:
`org.bouncycastle:bcprov-jdk18on:1.84` + `bcpkix-jdk18on:1.84`, `org.conscrypt:conscrypt-android:2.5.2`,
`net.i2p.crypto:eddsa:0.3.0` (gradle/libs.versions.toml). Every extracted file carries an
`// Origin:` header with the donor commit and upstream path.

**Changes vs donor (complete ledger; also stated in each file's provenance header):**
1. Package renamed `ai.closepaw.browser.cdp.wireless` -> `ai.eqo.adb.pairing` (incl. full-path imports).
2. Default peer label `"ClosePaw"` -> `"EQO"` (AdbPairingClient deviceLabel default).
3. ktlint formatting to this repo's style (repo gate `ktlintCheck`; formatting only).
4. Comment-only adjustments: blank line between the added provenance header and donor block
   comments (AdbPairingClient, AndroidPubkey, Spake25519); inline `/* autoClose = */` comment
   removed in AdbTlsClient (argument value unchanged: `true`); provenance header line wrapped
   to the repo's line-length rule.
5. NOT extracted (out of scope for the guided manual pairing flow): `AdbWirelessManager` (the
   helper-binder IAdbManager facade for QR/self-PSK pairing), `WirelessAdbSelfPairTransport`
   (CDP relay transport), `ProcNetTcpListeners` (QR pair-port discovery). The TASK-008 flow is
   the on-phone user-code path: the owner reads the "Pair device with pairing code" code and
   ports from Android's own screens.

**EQO-authored (:adb-pairing):** `AndroidSettingsNames` (verbatim Android-owned names),
`WirelessAdbEndpoints` (pairing vs connection port, confusion check), `AdbPairingCode`
(six-digit parse), `ActivationCheck` (pair, connect, helper start, authorize, binder health),
`ActivationFailure` (typed failures + recovery guidance), `FailureClassifier` (signal ->
failure), `PairingGuide` (first-run steps), `ActivationGate`/`WirelessAdbActivation`
(fresh install = "Activation required"; privileged entry points refuse with guidance),
`ActivationSequence` (five separately failing checks, per-check outcome records),
`WirelessAdbActivationRunner` (device-side runner on the extracted stack; helper checks enter
through the `HelperHooks` seam — the TASK-007 privileged helper's start/authorize/binder
surface, kept a separate auth plane per the task Notes).

**App wiring (:app):** MainActivity renders `activation_status` ("Activation required" on a
fresh install) and exposes a privileged entry point (`privileged_action_button`) that refuses
with the gate's guidance until activation completes; strings in `res/values/strings.xml`.

**Provenance:** all 35 new Kotlin files are recorded in `task-005-provenance-map.md`
("TASK-008 additions"), 14 EXTRACT rows and 21 EQO-NEW rows.

## 2. Host verification (real command output)

All commands from `C:\Users\<user>\Claude\worktrees\task-008\android` unless noted.

### Module tests

    ./gradlew :adb-pairing:test        -> BUILD SUCCESSFUL (75 tests, 0 failed)
    ./gradlew :app:testDebugUnitTest   -> BUILD SUCCESSFUL
    (see section 4 for the full-gate run that re-executes every module's tests)

### Full gate

    ./gradlew --max-workers=1 ktlintCheck detekt test
    -> BUILD SUCCESSFUL, GATE_EXIT=0

(`--max-workers=1` only serializes workers; this machine's page file could not back concurrent
ktlint/test worker JVMs earlier in the run — one `java.io.EOFException` test-worker crash in
`:core-llm:testDebugUnitTest` was reproduced then cleared by an isolated re-run
`./gradlew --max-workers=1 :core-llm:testDebugUnitTest` -> BUILD SUCCESSFUL, exit 0. It was
environmental, in a module this task does not touch.)

detekt: `:adb-pairing` has `detekt-baseline.xml` generated with the sanctioned
`./gradlew :adb-pairing:detektBaseline` task (55 entries, same pattern as :core-agent/:core-llm).
All 55 entries are in extracted donor files (MagicNumber/ThrowsCount/complexity in the wire
protocol code); **zero entries in EQO-authored files** (new code adds no baseline entries, per
the TASK-004 convention).

### Branding + provenance gates

    bash scripts/check-branding.sh  -> BRANDING GATE PASSED, exit 0
      (includes: "provenance map covers exactly the tracked Kotlin files ... OK")

### What the host tests prove, per acceptance criterion

- **C1 (fresh install shows "activation required"; privileged entry points refuse with
  guidance until done)** — `ActivationGateTest` (6 tests: fresh-install status + literal,
  refusal with guidance naming "Developer options"/"Wireless debugging"/"Pair device with
  pairing code", run only after activation), `ActivationWiringTest` (:app, 5 tests: the
  strings.xml literal equals the gate's message, MainActivity wires the gate and the two
  views). Host-verified.
- **C2 (pairing succeeds on Android 12 and 13 + one OEM skin)** — device work. **PENDING
  owner presence.** Owner decision recorded: no Android 12/13 device exists in this
  project; the available device is Android 11 (Realme RMX2193, an OEM skin of Android 11),
  and 12/13 will be recorded as untested per the lead's TASK-008 note.
- **C3 (wrong code, port confusion, revoke, reboot, Wi-Fi change recover with guidance,
  not silently)** — host-verified at the logic layer: `FailureClassifierTest` (8 tests:
  every signal maps to a typed failure whose guidance is non-empty and names the right
  Android settings), `ActivationSequenceTest` (8 tests: per-check independent failure with
  later checks explicitly NotRun, the five scenarios surfacing as WrongCode/PortConfusion/
  PairingRevoked/DeviceRebooted/WifiChanged guidance, gate never flips on partial success),
  `WirelessAdbActivationRunnerTest` (9 tests: helper checks fail separately, refused loopback
  ports report PORT_REFUSED and never pass, transport-error mapping table). Device records per scenario: **PENDING owner
  presence** (section 3).
- **C4 (Android-owned settings names verbatim)** — `AndroidSettingsNamesTest` (9 tests pin
  each literal), `PairingGuideTest` (5 tests: the guide names every screen verbatim and
  spells out that the pairing port and the connection port are different ports). Host-verified.

## 3. Device test plan (PENDING owner presence)

Device: Realme RMX2193, Android 11 (OEM skin), project-only phone. Android 12/13: untested
(no such device in this project — owner decision, recorded in the TASK-008 lead note).

Build and install (exit codes to be recorded with each run):

    cd C:\Users\<user>\Claude\worktrees\task-008\android
    .\gradlew.bat :app:assembleDebug
    adb install -r app\build\outputs\apk\debug\app-debug.apk

| # | Scenario | Steps (exact commands / taps) | Expected record | Status |
| --- | --- | --- | --- | --- |
| D1 | Fresh install shows activation required | `adb shell pm clear ai.eqo.app` then `adb shell am start -n ai.eqo.app/.MainActivity`, screenshot `adb exec-out screencap -p > d1.png` | "Activation required" visible; tapping "Run privileged action" shows the refusal guidance naming "Developer options" > "Wireless debugging" > "Pair device with pairing code" | PENDING owner presence |
| D2 | Guided pairing succeeds (Android 11 OEM skin) | Owner: Settings > "Developer options" > "Wireless debugging" ON; tap "Pair device with pairing code"; read the six-digit "Pairing code" and the dialog's "IP address & Port"; also read the "IP address & Port" on the "Wireless debugging" screen (connection port). Run the activation sequence with both ports and the code; record each of the five checks separately | PAIR passes, CONNECT passes (mTLS A_CNXN against the connection port); helper checks recorded separately | PENDING owner presence |
| D3 | Wrong code recovers with guidance | D2 but type a stale/incorrect code (or let the dialog expire) | PAIR fails with WrongCode guidance ("Pairing code" rejected; reopen "Pair device with pairing code"); later checks NotRun | PENDING owner presence |
| D4 | Port confusion recovers with guidance | D2 but enter the connection port in the pairing-port field | PAIR fails with PortConfusion guidance naming "Pair device with pairing code", "Pairing code" and "IP address & Port" | PENDING owner presence |
| D5 | Revoke recovers with guidance | After a passing D2: Settings > "Developer options" > "Revoke USB debugging authorizations", then re-run CONNECT | CONNECT fails with PairingRevoked guidance naming "Revoke USB debugging authorizations"; re-pair from "Wireless debugging" | PENDING owner presence |
| D6 | Reboot recovers with guidance | After a passing D2: `adb reboot`, wait for boot (`adb wait-for-device`), re-run the sequence with the OLD ports | CONNECT fails with DeviceRebooted guidance ("Developer options" > "Wireless debugging", new "IP address & Port") | PENDING owner presence |
| D7 | Wi-Fi change recovers with guidance | After a passing D2: switch the phone to another Wi-Fi network, re-run the sequence | CONNECT fails with WifiChanged guidance ("Wi-Fi", reopen "Wireless debugging", new "IP address & Port") | PENDING owner presence |
| D8 | Android 12 and 13 + OEM skin | Same as D2 on Android 12/13 devices | Record per device | PENDING owner presence; owner decision: no Android 12/13 device in this project, will be recorded as untested |

On-device driver: the five checks are `WirelessAdbActivationRunner` (PAIR/CONNECT on the
extracted stack) behind `ActivationSequence`; helper checks enter via the `HelperHooks` seam
(TASK-007 helper surface). The interactive input surface for code+ports is TASK-015's guided
onboarding; for the TASK-008 device session the runner is driven directly (instrumentation
harness or a debug entry), and the harness command will be recorded here verbatim when the
phone session runs. **No device command in this table has been executed — no phone was
available (lead: owner away, do not wait).**

## 4. Acceptance criteria status

| Criterion | Status | Where |
| --- | --- | --- |
| Fresh install shows "activation required"; privileged entry points refuse with guidance until done | **Host-verified** (exit 0) | `ActivationGateTest`, `ActivationWiringTest` |
| Pairing succeeds on Android 12 and 13 (plus one OEM skin) | **PENDING owner presence** (12/13: untested per owner decision; Android 11 OEM = D2) | section 3 |
| Wrong code, port confusion, revoke, reboot, Wi-Fi change tested and recover with guidance | **Host-verified** at the logic layer (exit 0); device records PENDING owner presence | `FailureClassifierTest`, `ActivationSequenceTest`, `WirelessAdbActivationRunnerTest`; D3-D7 |
| Android-owned settings names shown verbatim | **Host-verified** (exit 0) | `AndroidSettingsNamesTest`, `PairingGuideTest` |

## 5. What is not verified

- No device scenario has run (no phone; owner away). Device records D1-D8 are PENDING owner presence.
- Android 12/13 pairing is untested by owner decision (no such device in this project).
- The end-to-end on-device run of `WirelessAdbActivationRunner` against a live adbd (pair +
  connect over real TLS) has not executed; its transport-error mapping is host-tested only.
- `HelperHooks` has no production implementation yet: helper start/authorize/binder health are
  separate auth planes owned by the TASK-007 helper surface; their wiring is follow-up work, not
  claimed here.
- Pairing, helper authorization, accessibility and CDP consent remain separate auth planes; this
  task's gate covers the wireless-ADB pairing plane only (task Notes).

## 6. 16 KB alignment fix and fresh full gate (2026-10-04)

### Dependency and rebase

The initial donor pin `org.conscrypt:conscrypt-android:2.5.2` was not 16 KB aligned.
The catalog now uses **2.7.0**, the latest stable release in Maven Central metadata
retrieved during this run. No ABI was removed, no lint baseline was added, and no lint
rule or trust-manager finding was suppressed.

Sources inspected directly (search backend returned HTTP 403; direct retrieval worked):
- [Maven Central metadata](https://repo.maven.apache.org/maven2/org/conscrypt/conscrypt-android/maven-metadata.xml)
  lists 2.5.3, 2.6.0 through 2.6.3, and 2.7.0 in addition to alpha versions.
- [Conscrypt 2.6.0 release notes](https://github.com/google/conscrypt/releases/tag/2.6.0)
  explicitly state `conscrypt-android is 16 KB-aligned` and that it is built with NDK v27.
- [Conscrypt 2.7.0 release notes](https://github.com/google/conscrypt/releases/tag/2.7.0)
  describe the stable Android/OpenJDK release, X25519MLKEM768 as the TLS default,
  ECH and new signature algorithms. Alignment was verified on the actual artifact,
  not inferred from those notes.

The three original TASK-008 commits were mechanically rebased from `f84e079` onto
`origin/main` **f188854514db1e288cca89c5ea061074d9a1bd0a** (docs-only drift).
The rebased implementation before this fix commit is `262e4eb38415f1fc6a107f445695906853d32bea`.
Push announcement is on kanban task `t_b4638be0`; only `agent/android/13-adb-pairing`
is to be force-pushed with `--force-with-lease`. No PR is created.

### Actual toolchain

| Component | Version / verification |
| --- | --- |
| JDK | Temurin OpenJDK 21.0.12.1+1-LTS (`java -version` and wrapper `--version`) |
| Gradle | 9.7.0 (wrapper `--version`) |
| AGP | 9.3.1 (`gradle/libs.versions.toml`) |
| Kotlin | 2.4.0 (catalog and wrapper output) |
| Conscrypt Android | 2.7.0 (catalog, resolved AAR, packaged libraries) |
| Project native helper NDK | r29, 29.0.14206865 (`helper-server/build.gradle.kts`, SDK `source.properties`) |
| ELF inspector | NDK r29 `llvm-readelf`, LLVM 21.0.0 (`--version`) |

The NDK used to inspect our APKs/build the helper is not a claim that Conscrypt's
prebuilt AAR was compiled using our NDK; upstream 2.6.0 notes specify v27.

### Binary proof

Downloaded:
`https://repo.maven.apache.org/maven2/org/conscrypt/conscrypt-android/2.7.0/conscrypt-android-2.7.0.aar`

AAR SHA-256: `86072ce711795e188e1e82214d023789d2983fafe342bcae83f046125cf64e7f`.
Extracted every `jni/*/libconscrypt_jni.so` and ran:

    C:/Users/<user>/Android/Sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe -lW <extracted-library>

Then repeated on every `lib/*/libconscrypt_jni.so` in both built APKs:
`app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release-unsigned.apk`.
Python asserted exactly four Conscrypt libraries per APK, exactly three LOAD headers
per library, and alignment **0x4000 for every LOAD**. All assertions passed.
Raw packaged LOAD headers and per-library SHA-256 values are recorded in
[task-008-conscrypt-alignment.txt](task-008-conscrypt-alignment.txt).
The debug and release libraries have identical hashes per ABI.

All four ABIs remain shipped: **arm64-v8a, armeabi-v7a, x86, x86_64**.
Parsed all eight module `lint-results-debug.xml` reports: **zero Aligned16KB issues**.
This fixes the alignment defect, but does not mean lint as a whole passes.

### Full gate: actual results, not green

From `android/`, one Gradle invocation at a time:

    ./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m

1. Initial run: **BUILD FAILED in 17m 35s** after building debug/release APKs.
   `:core-llm:testDebugUnitTest` failed with `NoSuchFileException` for
   `build/test-results/testDebugUnitTest/binary/in-progress-results-generic.bin`.
   Root cause was not established; this is not reported as a test assertion failure.
2. Exact retry after the first run ended: **exit 1, BUILD FAILED in 3m 37s**.
   Tests completed without assertions failing; `:adb-pairing:lintDebug` now reports
   three trust-manager errors, not alignment errors.
3. Repeated with only `--continue` added to collect every remaining gate:
   **exit 1, BUILD FAILED in 3m 54s** (767 tasks: 87 executed, 680 up-to-date).
   `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `ktlintCheck`, `detekt`
   completed; only `:adb-pairing:lintDebug` and `:app:lintDebug` failed.
   Each failing module reports three trust-manager errors; the app report repeats
   findings from the Bouncy Castle JAR and transformed pairing classes.
   All other module lint reports have zero issues.
4. Explicit fresh pairing compilation/test execution:

       ./gradlew :adb-pairing:testDebugUnitTest --rerun-tasks --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m

   **exit 0, BUILD SUCCESSFUL in 40s**, all 15 tasks executed.
   Parsed JUnit XML: **75 tests, 0 failures, 0 errors, 0 skipped**.
5. From repo root (the scripts live in root `scripts/`, not `android/scripts/`):
   `scripts/check-branding.sh`: **exit 0, BRANDING GATE PASSED**;
   `scripts/check.sh`: **exit 0, OK** (also reruns branding).

Final debug JUnit XML totals, aggregated programmatically:

| Module | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| adb-pairing | 75 | 0 | 0 | 0 |
| app | 13 | 0 | 0 | 0 |
| core-agent | 62 | 0 | 0 | 0 |
| core-llm | 261 | 0 | 0 | 1 |
| core-security | 47 | 0 | 0 | 0 |
| helper-server | 3 | 0 | 0 | 0 |
| platform-a11y | 60 | 0 | 0 | 0 |
| **Total** | **521** | **0** | **0** | **1** |

Some unchanged suites/tasks were UP-TO-DATE; these counts are the resulting XML,
not a claim that all 521 tests were freshly executed in one successful gate.
No phone work or live pairing was performed.

### Per-site TLS security assessment — STOP, security pass required

Lead's mid-run instruction: no blanket suppression/baseline; validate each site and
only consider a narrowly scoped exception with a wrong-peer rejection test when
there is actual authentication. **No suppression or security implementation change
is made in this fix.**

1. **`AdbPairingTls.kt:129`, `TrustAllManager` (`CustomX509TrustManager`).**
   Both trust callbacks unconditionally return Unit (lines 130-138): the TLS manager
   accepts any certificate and performs no PKI or pin check. However the outer
   pairing protocol is not entirely unauthenticated: `AdbPairingClient.runPair`
   (lines 59-81) mixes the user PSK with the TLS exporter, performs SPAKE2, derives
   an AES key with HKDF, and decrypts peer info with AES-GCM before returning success.
   This is code evidence of password-based, channel-bound authentication after TLS,
   not proof that the trust-manager callbacks validate certificates. No live or
   integrated wrong-peer rejection test was executed. A protocol-specific exception
   cannot be claimed safe from the existing passing tests alone. Leave finding
   unsuppressed; security pass must validate wrong PSK, wrong TLS exporter/channel,
   invalid GCM tag and success-gating before deciding a narrow exception.
2. **`AdbTlsClient.kt:192`, `TrustAllManager` (`CustomX509TrustManager`).**
   All six client/server trust callbacks unconditionally return Unit (lines 193-225).
   `connectWithStls` installs this manager (line 112), completes TLS and returns a
   usable channel (lines 120-122) without validating or pinning the server key.
   The comment at lines 39-40 says adbd authenticates OUR client certificate;
   that is the opposite direction and does not authenticate the server to EQO.
   A peer accepting our certificate is not proof that it is the paired adbd.
   **This is an unaddressed server-authentication security defect**, not a harmless
   lint warning. No suppression is justified. Stop rather than invent a pin or
   silently use public CA trust (adbd uses its own certificate identity).
   Security pass must define authenticated enrollment/persistence of the device
   key and its rotation/re-pairing behavior, then enforce it on every connection
   and add a test proving a wrong server key is rejected before channel use.
3. **`bcpkix-jdk18on:1.84` (`TrustAllX509TrustManager`).**
   Inspected the actual JAR with `javap -c -p`. The offending class is
   `org.bouncycastle.est.jcajce.JcaJceUtils$1`: `checkServerTrusted` consists solely
   of bytecode `return`; `checkClientTrusted` throws IllegalStateException.
   The separate `$2` implementation does PKIX validation, but is not the reported
   empty implementation. No source references to the EST/JcaJceUtils API were found
   in the pairing module; Bouncy Castle is used for certificates and crypto.
   This bundled implementation accepts arbitrary server certificates if selected;
   absence of an observed call is not grounds for a blanket suppression. Security
   pass must prove reachability and choose a safe upstream fix or narrowly scoped
   dependency treatment/removal, preserving required certificate generation.

**Decision: stop and report with the alignment fix preserved.** The full gate remains
red on these TLS findings. No wrong-peer rejection test is claimed, no lint baseline
was added, and no @SuppressLint was added. The branch is not ready for a green-gate
handoff until the server-authentication defect and dependency finding are resolved.

### Loopback-only lead disposition — current follow-up status

The lead subsequently authorized keeping the inherited trust managers with narrowly
scoped annotations, a loopback-only transport guard, rejection tests, and an explicit
security-pass decision item. This subsection supersedes the earlier STOP assessment
for the two local sites only; it does not claim that loopback authenticates a server.
The original dependency/alignment proof and toolchain table above remain applicable:
Temurin 21.0.12.1+1-LTS, Gradle 9.7.0, AGP 9.3.1, Kotlin 2.4.0,
Conscrypt 2.7.0, NDK 29.0.14206865 / LLVM 21.0.0.

#### AOSP verification (actual source, pinned revision)

Retrieved directly from Gitiles with `?format=TEXT` and base64-decoded the source;
the web-extract service failed with HTTP 403. Resolved main to
**1cf2f017d312f73b3dc53bda85ef2610e35a80e9**, then fetched the pinned files and verified
they were byte-identical to the initially inspected main files.

- [tls/tls_connection.cpp](https://android.googlesource.com/platform/packages/modules/adb/+/1cf2f017d312f73b3dc53bda85ef2610e35a80e9/tls/tls_connection.cpp),
  lines 235-238: `SSL_CTX_set_cert_verify_callback(ssl_ctx_.get(), SSLSetCertVerifyCb, this);`
  when a custom callback exists. Lines 264 onward request a peer certificate with
  `SSL_VERIFY_PEER | SSL_VERIFY_FAIL_IF_NO_PEER_CERT`; this does not override the
  callback's unconditional success. `SSLSetCertVerifyCb` delegates to `cert_verify_cb_(ctx)`.
- [transport.cpp](https://android.googlesource.com/platform/packages/modules/adb/+/1cf2f017d312f73b3dc53bda85ef2610e35a80e9/transport.cpp),
  client branch, lines 539-540, verbatim:

      // Allow any server certificate
      tls_->SetCertVerifyCallback([](X509_STORE_CTX*) { return 1; });

  Server branch, lines 542-544, instead calls `adbd_tls_verify_cert(ctx, auth_key)`.
  This confirms the inherited client behavior. It does NOT establish that a malicious
  server accepting our client certificate has authenticated itself to us.
- [pairing_connection/pairing_connection.cpp](https://android.googlesource.com/platform/packages/modules/adb/+/1cf2f017d312f73b3dc53bda85ef2610e35a80e9/pairing_connection/pairing_connection.cpp),
  lines 180-181, verbatim:

      // Allow any peer certificate
      tls_->SetCertVerifyCallback([](X509_STORE_CTX*) { return 1; });

  Lines 190-191 explain, verbatim:

      // To ensure the connection is not stolen while we do the PAKE, append the
      // exported key material from the tls connection to the password.

  Lines 192-199 export TLS material, append it to `pswd_`, and create pairing auth.
- [daemon/auth.cpp](https://android.googlesource.com/platform/packages/modules/adb/+/1cf2f017d312f73b3dc53bda85ef2610e35a80e9/daemon/auth.cpp),
  `adbd_tls_verify_cert`, lines 301-355, extracts the client's certificate public key,
  iterates known public keys, and returns `authorized ? 1 : 0` (when auth is required).
  This is client-key authorization by adbd, not server-key validation by the client.

#### Guard and tests

`LoopbackAdbHost.requireAddress` permits canonical four-octet IPv4 in 127.0.0.0/8
and numeric IPv6 loopback (including the expanded ::1 spelling). It rejects LAN,
public, wildcard, DNS hostname, scoped address and ambiguous abbreviated/octal IPv4
inputs. DNS names are deliberately not supported, including `localhost`: numeric
inputs only. IPv6 is restricted to hexadecimal digits and colons before parsing.
The checked `InetAddress` is used directly by `InetSocketAddress` at TCP dial time,
so no checked hostname can be re-resolved to a different destination.

The guard runs at the start of `AdbPairingClient.pair`, `AdbPairingTls.connect`, and
`AdbTlsClient.connectWithStls`, before provider initialization, key creation or sockets.
Production `WirelessAdbActivationRunner` already uses `LOCALHOST = "127.0.0.1"`
for both connections; no app path requiring a non-loopback host was found.

Seven new `LoopbackAdbHostTest` tests verify IPv4-range acceptance, IPv6 acceptance,
LAN/public/wildcard refusal, ambiguous/malformed/DNS input refusal, and remote-host
refusal at each of the three transport entry points. The pairing/connect entry tests
also assert no key-store directory was created. These are real host tests, not an
integrated wrong-certificate/PSK handshake proof and not device tests.

#### Each trust-manager site and remaining security decisions

1. `AdbPairingTls.TrustAllManager`: kept per lead disposition. Peer authentication
   occurs after TLS in `AdbPairingClient.runPair`, using PSK plus TLS exporter,
   SPAKE2/HKDF and authenticated AES-GCM peer info before returning success.
   Dialing is loopback-only. `CustomX509TrustManager` is suppressed only on the
   private class; `TrustAllX509TrustManager` only on its two trust callbacks.
2. `AdbTlsClient.TrustAllManager`: kept per lead disposition and confirmed AOSP
   client behavior. Pairing authenticates the prior enrollment exchange, and adbd
   authorizes our client key; subsequent server certificates remain unverified.
   Loopback prevents dialing a LAN/public host, not local listener impersonation.
   `CustomX509TrustManager` is suppressed only on the private object;
   `TrustAllX509TrustManager` only on its six trust callbacks.
   Class-only suppression was tested: it cleared source lint but NOT the app's
   bytecode empty-method checks. Method-only suppression cleared bytecode lint
   but NOT source custom-class lint. The final scopes match each detector's exact
   reported object/method, with no file/module-wide exception or baseline.
3. `bcpkix-jdk18on:1.84`, `org.bouncycastle.est.jcajce.JcaJceUtils$1`: remains
   unsuppressed. Its empty server check cannot receive a source annotation in EQO.
   The EST API is unused by this module's source; certificate generation depends
   on bcpkix's separate cert/operator APIs. No artifact rewrite, certificate-generation
   rewrite, rule disable or baseline was silently introduced. This is the ONLY
   remaining finding, repeated once in each of adb-pairing/app lint reports.

**SECURITY PASS MUST DECIDE:** accept/reject the local-listener impersonation risk
and whether post-pairing server-key pinning/enrollment/rotation is required; validate
wrong PSK/exporter/GCM rejection in the integrated protocol; prove the unused EST
helper's reachability disposition and authorize a narrowly dependency-scoped lint
exception or a reviewed dependency replacement/removal. AOSP parity is evidence
of compatibility, not proof of security. The object/method-only authorization does
not authorize suppressing arbitrary dependency bytecode via lint.xml.

#### Final actual gates

One Gradle invocation at a time, from `android/`, using the exact requested worker
and JVM limits:

    ./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m

Final method/class-scoped code: **exit 1, BUILD FAILED in 1m 50s**, 633 tasks
(65 executed, 568 up-to-date). `:adb-pairing:lintDebug` fails on the single bcpkix
finding. Then repeated with only `--continue` added: **exit 1, BUILD FAILED in 40s**,
767 tasks (29 executed, 738 up-to-date). Builds, tests, ktlintCheck and detekt complete;
only `:adb-pairing:lintDebug` and `:app:lintDebug` fail, each on that same upstream
helper. Six other module lint XML reports are empty; all eight reports contain
**zero Aligned16KB findings**, no local trust-manager findings remain.

The targeted module retry (after fixing three new-code detekt style findings,
without baseline changes) passed compilation, tests and detekt. The original 75
pairing tests plus seven new guard tests now give **82 tests, 0 failures/errors/skips**.
Resulting debug XML totals across modules are **528 tests, 0 failures, 0 errors,
1 skipped** (core-llm's existing skip); unchanged suites can be UP-TO-DATE.

Branding and repository scripts from repo root both passed exit 0; the provenance
map covers all 270 tracked Kotlin files including the two EQO-authored additions.
`git diff --check` passed. Both newly built APKs were reopened and all four
Conscrypt libraries rechecked with NDK r29 `llvm-readelf -lW`: every library still
has exactly three LOAD headers, each **0x4000**. Per-ABI hashes match the earlier
alignment artifact. No ABI removed; no Aligned16KB exception.

Raw final exact/continue gate output and targeted-test retry output are preserved
in `task-008-loopback-gates.txt`. No phone was available; live pairing remains
PENDING owner presence. The follow-up is a normal push of our branch, not another
force-push, and no PR. **Not a green full-gate completion: awaiting explicit
upstream dependency disposition.**

### TASK-008 fix 3 — authorized dependency-only lint exception (2026-10-04)

This section supersedes the red-gate/unsuppressed-bcpkix status above, not the
remaining security risks. Lead authorization in task `t_15d24243` explicitly
permits a dependency-path-only exception. No phone was available.

`app/lint.xml`, already shared by `:adb-pairing`, now ignores only
`TrustAllX509TrustManager` at the Gradle artifact path matching
`bcpkix-jdk18on/1.84/<cache-hash>/bcpkix-jdk18on-1.84.jar` (either path separator).
There is no severity override for this issue, no module-wide disable, no baseline,
no source-path ignore, and no generic classes.jar ignore. A dependency version
change will no longer match and requires review. The lint report gives only a JAR
location, not a class location: scope is this exact artifact/version for this one
issue, not a claim that the matcher distinguishes classes within that JAR.

Library: `org.bouncycastle:bcpkix-jdk18on:1.84`. Real `javap -p -c` of the cached
upstream JAR confirms `org.bouncycastle.est.jcajce.JcaJceUtils$1` implements
`X509TrustManager`; `checkServerTrusted` contains only `0: return`, while
`checkClientTrusted` throws `IllegalStateException`. This is upstream bytecode,
not EQO-authored source. Raw output is `task-008-bcpkix-bytecode.txt`.

Source grep over all 311 tracked Kotlin/Java files under Android `src/`, pattern
`JcaJceUtils|org\.bouncycastle\.est`, finds **zero hits**. Separate cert/operator
import search shows the actual certificate-generation usage in
`AdbCryptoKeyStore.kt` and `AdbPairingTls.kt`; see
`task-008-bcpkix-source-search.txt`. This supports no direct EST trust-manager use;
it is not a whole-program/reflection reachability proof. A dependency bump would
be smaller in line count only if a verified upstream fix exists; none was
established here. Replacement/removal changes certificate-generation APIs and is
not demonstrated to be a smaller safe change. Applied the authorized exception
now, with no dependency version changes.

**SECURITY PASS MUST DECIDE:** accept/reject this unused upstream EST bytecode
exception and validate its reachability disposition; accept/reject local-listener
impersonation risk and the need for post-pairing server-key pinning, enrollment,
and rotation; verify integrated wrong-PSK/exporter/GCM rejection. A green lint gate
is not a security approval or a live pairing test.

#### Negative control and restoration

With the exact artifact exception enabled, both `:adb-pairing:lintDebug` and
`:app:lintDebug` first pass: **exit 0, 1m 15s**, 138 tasks (29 executed,
109 up-to-date), recorded in `task-008-bcpkix-lint-positive.txt`.

Temporarily removed only the method-scoped
`@SuppressLint("TrustAllX509TrustManager")` from
`AdbPairingTls.TrustAllManager.checkServerTrusted`, leaving all other annotations
and the dependency exception unchanged. Re-ran both lint tasks with `--continue`:
**exit 1, BUILD FAILED in 29s**, 138 tasks (25 executed, 113 up-to-date).
`:app:lintDebug` reports exactly one `TrustAllX509TrustManager` error in the
transformed EQO `classes.jar`; the bcpkix artifact is still excluded.
`:adb-pairing:lintDebug` remains green because the source detector is separately
covered by the retained class annotation. This demonstrates our own bytecode
finding is NOT hidden by the dependency-path ignore. Raw output and both XML
reports are `task-008-bcpkix-lint-negative.txt` and
`task-008-bcpkix-negative-{app,adb-pairing}.xml`.

Restored the callback annotation exactly. No production Kotlin source diff remains.
The full gate below then re-analyzed both modules and passed.

#### Final full gate and versions

From `android/`, one Gradle invocation at a time:

    ./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m

**Exit 0, BUILD SUCCESSFUL in 2m 35s**, 767 tasks (96 executed,
671 up-to-date). Debug and release assemblies, unit tests, all lint, ktlintCheck,
and detekt pass. Parsed all eight module lint XML reports: **zero issues**, hence
zero Aligned16KB findings; no alignment rule exception was added.
Parsed resulting debug JUnit XML: **528 tests, zero failures/errors, one existing
skip**; pairing: **82 tests, zero failures/errors/skips**. Some unchanged suites
were UP-TO-DATE; these totals are report totals, not a claim all tests re-executed.
Raw gate output: `task-008-bcpkix-full-gate.txt`; machine-readable totals:
`task-008-bcpkix-results.json`.

Versions from the actual build/catalog: Gradle **9.7.0**, AGP/lint **9.3.1**,
Kotlin **2.4.0**, ktlint Gradle plugin **14.2.0**, detekt **1.23.8**,
bcprov/bcpkix **1.84**, Conscrypt Android **2.7.0**; JDK path is Adoptium
`jdk-21.0.12.101-hotspot`. No toolchain or TLS dependency changes in this fix.

Repository-root `bash scripts/check-branding.sh` and `bash scripts/check.sh`
both pass **exit 0**, with all 270 tracked Kotlin files covered by provenance.
Raw logs: `task-008-bcpkix-branding.txt`, `task-008-bcpkix-check.txt`.
`git diff --check` passes. No new gate failure. No phone/device claims; live pairing
remains pending owner presence. Normal branch push only; no PR.
