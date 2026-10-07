# TASK-080: enable wireless pairing with server pinning (fail closed) - evidence

Issue #20. Branch `cloud/80-wireless-pairing` from `origin/main` @ `4938db9`.
Closes security carry-over TASK-008 SF-1 (connect plane trusted any server certificate).

## 1. What changed

**:adb-pairing**
- `ServerPin.kt` (new): `ServerKeyFingerprint` (SHA-256 of the server certificate's DER public key, `toString()` redacted),
  `EnrolledServer`, typed `ServerNotEnrolledException` / `ServerKeyMismatchException` (sealed `ServerPinException`),
  and `PinnedServerTrustManager` (accepts exactly one key; every other certificate, an empty chain and any client-side
  check fail).
- `ServerEnrollmentStore.kt` (new, reached as `AdbCryptoKeyStore.enrollment`): persists the enrollment in the existing
  app-private key store directory.
- `AdbTlsClient.connectWithStls` (the only connect path): loopback-only guard first (unchanged), then
  an existing-pin requirement BEFORE any key, provider or socket work (except the explicit fresh-pair capability), then the TLS
  handshake with `PinnedServerTrustManager`; a pin failure surfaces as `ServerKeyMismatchException`. After the
  handshake the negotiated peer key is re-checked before the channel is returned. `TrustAllManager` is deleted.
- `AdbPairingClient.pair` completes SPAKE2 + authenticated peer info but never persists its TLS key.
- `ConnectKeyEnrollment` (PR #77 correction): the setup runner creates an in-memory, one-use capability only after
  fresh pairing succeeds, bound to the same numeric loopback address and the entered CONNECT port. The first
  CONNECT attempt consumes it; its 30-second monotonic window is checked before dial, at every certificate callback,
  at the negotiated-peer recheck, and before commit. Failed attempts cannot retry enrollment. The CONNECT key is
  persisted only after successful TLS plus peer recheck. Reconnect and helper start never receive this capability.
- `AdbPairingTls`: the pairing-plane manager is now `PairingCaptureTrustManager`, documented as capture-only (see 2).
- `AdbShellLauncher.kt` + `HelperStartCommand` (new): the only use of ADB. Builds
  `<nativeLibraryDir>/libeqo-starter.so --apk=<base.apk>` (the command recorded in task-007-helper-spike.md) from two
  paths validated against `/[A-Za-z0-9_.=+~/-]+` and runs it over the pinned `shell:` service; any other command is
  refused before dialling.
- `WirelessAdbActivationRunner`: pin failures map to `StepSignal.SERVER_NOT_ENROLLED` / `SERVER_KEY_MISMATCH`
  (`ActivationFailure.NeedsRepair`, guidance names Android's own screens); `startHelper` runs the validated command when a
  command is supplied, then the helper hook. `ActivationSequence.reconnect` connects again without a new code and fails
  closed when nothing is enrolled. `WirelessLink.stateOf` derives the four plain-language states.

**:app**
- `StudyFlowGate.WIRELESS_CONNECT_PLANE_IN_STUDY_FLOW = true` (CDP stays false). `WIRELESS_CONNECT_PENDING_WORK`
  replaced by `WIRELESS_CONNECT_SAFEGUARD`.
- `WirelessAdbSetupActivity`: enter pairing code + pairing port + connection port from Android's Wireless debugging
  screen, "Pair and connect", "Connect again (already paired)", "Forget this pairing"; shows one of: Not paired / Paired and
  connected / Paired but not connected right now / Needs re-pair, plus a line per activation check. The two-ports-two-names
  explanation is kept. Work runs on a background thread, only from a tap.
- `StudyHelperHooks` (new): authorize/binder checks answered by the helper binder itself.
- `StudySetup.probeWirelessAdb(context)` reports the row from the same state (no more GATED).
- The key store lives in `noBackupFilesDir/adb-keys`.

No new permission, no new manifest entry, no `WRITE_SECURE_SETTINGS`, no `Runtime.exec`/`su`/termux. INTERNET was already
declared; loopback needs nothing else.

## 2. How pinning works (and why this key)

The PAIR protocol (AOSP `pairing_connection.cpp`) exchanges, inside the SPAKE2-authenticated AES-GCM channel, the
client's RSA public key and the server's **device GUID** only; it does not hand EQO a server public key. What the
protocol does give is a TLS session whose exporter keying material is mixed into the SPAKE2 password: a man in the middle
has a different TLS session, so its exporter differs and SPAKE2 fails. The certificate the pairing server presented on
that very TLS session is therefore authenticated once pairing succeeds. However, it is NOT adbd's CONNECT key.

Security review confirmed stock AOSP 11/12 uses two independent keys: system_server's pairing service calls
`pairing_server_new_no_cert` and generates a fresh RSA-2048 key per pairing session; adbd's CONNECT TLS uses a
random RSA key per daemon process. The earlier assumption that these keys match was false, so that implementation
failed closed on every first CONNECT. The lead approved bounded CONNECT-plane TOFU instead.

Only the user-tapped Pair and connect flow grants the capability, after SPAKE2 success. Its trust manager captures
one nonempty CONNECT certificate leaf key and rejects any change on later callbacks/recheck. The pairing GUID is
metadata, not proof of the CONNECT key: neither SPAKE2 nor the GUID cryptographically authenticates that separate key.

- Enrollment record (`server-enrollment.v1`, app-private, non-backed-up, never logged): version, SHA-256 of the client
  public key, server key fingerprint, sanitized GUID, time, and an HMAC-SHA256 keyed from the client private key. It is
  discarded (reads as "not enrolled") if the MAC fails (edited), the client key differs (copied in, or the key store was
  reset) or the file is malformed. It holds a hash of a public key, not a secret; the file is not separately encrypted
  (Android file-based encryption protects the app-private dir at rest) but it cannot be forged without the client key.
- Plain connect: no enrollment -> `ServerNotEnrolledException` before anything else. Enrolled -> handshake accepts
  only that key; otherwise `ServerKeyMismatchException`. Only the explicit fresh-pair capability permits first-key
  enrollment, once, at the same address/CONNECT port, within 30 seconds. Both refusals become `NeedsRepair`; nothing retries.
- Re-pair replaces the enrollment atomically. "Forget this pairing" deletes it (next connect refuses).
- Pairing-plane note: the pairing TLS cannot pin (it is where the key is learned), so `PairingCaptureTrustManager`
  still accepts the presented certificate, as AOSP does. It never gates an ADB connection; its key is NOT enrolled.
  This is the one remaining unchecked accept-all callback in production code; a unit test pins that it
  exists only in `AdbPairingTls.kt` and not on the connect plane.

## 3. Tests (host JVM)

PR #77 correction: `ConnectKeyEnrollmentTest` adds 13 tests with real X.509 certificates and a fake monotonic clock:
failed pairing yields no capability/pin; success does not persist the PAIR key; TLS verification alone does not
persist; only CONNECT key commits; one claim/commit; later mismatch fails; plain connect cannot use a pending token;
window starts after pairing and expires at 30 seconds; expiry during handshake/before commit; backward clock refusal;
address/port binding; unclaimed/second-attempt refusal; empty/changed certificate refusal (terminally poisons
the capability so commit cannot follow a rejected certificate); expiry after claim immediately before dial;
explicit re-pair replacement;
and production source guards for the enrollment writer and fresh-pair runner path.

Local verification: `:adb-pairing:ktlintCheck :adb-pairing:detekt :adb-pairing:testDebugUnitTest :adb-pairing:lintDebug`
passed. JUnit XML: 122 tests, zero failures/errors (13 in the new class). No assertion was weakened in existing tests.

`ServerPinTest` (15): enrollment stored and read back; match accepted on all three trust callbacks; mismatch rejected
(flag set); empty/null chain rejected; client checks refused; no enrollment fails closed before keys/network (directory
not created); keys without enrollment still refused; cleared enrollment refuses; re-pair replaces (old key now
rejected); edited record rejected; record copied to another client key rejected; reset drops enrollment; fingerprint
never in `toString`/messages; production `Log.` lines carry no key/code/fingerprint terms; no TrustAll on the connect
plane and the pairing capture manager is the only unchecked acceptor.
`WirelessReconnectTest` (10): reconnect fails closed without enrollment, runs only post-pair checks with it, key
mismatch -> NeedsRepair and nothing activates, the four states, repair guidance names Android's screens, helper command
exact form and rejection of shell metacharacters/other commands, launcher refuses other commands, ADB service names only
in the launcher. `WirelessAdbActivationRunnerTest` gains no-enrollment and pin-mapping cases (the existing refused-port
test now enrols a stand-in server first, since an un-enrolled connect refuses earlier by design; its assertion is
unchanged). `StudyFlowGateTest` is updated honestly: wireless gate true only while `AdbTlsClient` source requires an
enrollment and has no TrustAll; CDP still false; only the setup screen may construct the runner; raw `AdbTlsClient`
and CDP clients stay banned from app sources. `WirelessProbeTest` (2) covers the hub row.

## 4. Not verified on a real device (the Realme RMX2193, Android 11)

1. **Different keys on both planes are now expected.** Verify successful Pair and connect on the Realme, then
   Connect again without a new code. Restarting adbd (including reboot or wireless-debugging restart) changes its
   per-process key; expect Needs re-pair, never automatic replacement. OEM behavior remains device-unverified.
2. End-to-end: SPAKE2, bundled Conscrypt mTLS, and enrollment commit against real adbd remain device-only; host
   tests exercise the capability and trust decisions with real X.509 certificates, not a claimed phone handshake.
3. Starting the helper: the `shell:` service behaviour with the starter (daemonizing, stream close), the 15 s binder
   wait, and `Shizuku.checkSelfPermission` being granted for the allowlisted `ai.eqo.app` manager.
4. Wrong-code, wrong-port, revoke, reboot and Wi-Fi-change recovery (TASK-008 D3-D7) with the new UI; Android 12/13 untested.
5. Whether the platform keeps the pairing dialog open while the pair step runs on this OEM skin.

## 5. Threat notes

- Local impostor after enrollment: refused (key mismatch) unless it holds the enrolled private key. It cannot
  complete the PAIR exchange without the six-digit code. HOWEVER a malicious local listener that wins the entered
  CONNECT address/port during the fresh 30-second enrollment window can have its own key enrolled even after an
  honest SPAKE2 pair. Bounded TOFU narrows this first-use risk; it does not eliminate it or cryptographically bind
  the two services. No shell/helper command is sent until the pin is committed; all later connections require it.
- Offline guessing of the code: SPAKE2 limits a MITM to one online guess per pairing attempt; the code expires with the dialog.
- Enrollment tampering needs the app sandbox; with it the attacker already has the client private key.
- ADB capability: loopback only, one validated command (helper start). Anything beyond is rejected by `HelperStartCommand`
  and a source scan test.
- Residual: the pairing capture trust manager (above); and the helper itself (TASK-007) is a separate authorization plane.

## 6. Gate

See the PR description for the exact command results and CI status.
