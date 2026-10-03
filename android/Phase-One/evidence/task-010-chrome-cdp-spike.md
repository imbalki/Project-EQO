# TASK-010: Chrome DevTools (CDP) spike (S3) — evidence

Issue #15. Branch `agent/android/15-cdp`, worktree
`C:\Users\<user>\Claude\worktrees\task-010`. Base: `origin/main` @ `2076ae5` (TASK-008
wireless pairing merged as #55). Owner decisions applied: Android floor 11 (minSdk 30),
phone is project-only. **Lead instruction for this run: the OWNER IS AWAY and cannot touch
the phone, so NO device work was performed.** All code, host and unit work is done and
verified below; the device test plan (section 4) carries the exact commands and every
device-level acceptance criterion is marked **PENDING owner presence**.

Toolchain (section 2 quotes real output): JDK 21 (Temurin 21.0.12.101), Gradle 9.7.0,
AGP 9.3.1, Kotlin 2.4.0 (root `kotlin-gradle-plugin` pin), minSdk 30, compileSdk 36.

---

## 1. What was built

New module `:browser-cdp` (`android/browser-cdp/`), namespace `ai.eqo.browser.cdp`,
registered in `android/settings.gradle.kts`. It is the S3 spike surface: navigate Chrome and
fill a test form through the Chrome DevTools Protocol, after the documented debug
preparation.

**Extracted ClosePaw Chrome DevTools (CDP) stack (6 production files + 1 test helper).**
Donor: `imoonkey/closepaw` @ `75dae2653f5a6b25d5df51ee7008b0f830de1536`, package
`app/src/main/kotlin/ai/closepaw/browser/cdp/`. Files: `CdpTransport` (CdpConnection /
CdpConnectionFactory / CdpConnectionClosedException — the transport seam), `ChromeCdpCommand`
(CDP JSON framing: buildCdpRequest / parseCdpMessage), `ChromeCdpEventBuffer` (event ring +
DialogStateTracker), `ChromeCdpTarget` (real-page selection), `ChromeCdpClient` (the CDP
command sender: connect / attachToTarget / send with per-command timeout + stale-session
recovery), `DevtoolsHttpProtocol` (strict /json/version + /json/list parsing, re-homed from
the donor's `shizuku/` sub-package into the flat package), plus `FakeCdpConnection` (test,
adapted from the donor test to JUnit). Every extracted file carries an `// Origin:` header
with the donor commit and upstream path.

**EQO-authored (:browser-cdp) — the setup orchestration and payoff capability.**
- `CdpConsent` (informed-consent gate; consent screen shown + accepted before any preparation).
- `CdpSetupError` (typed setup errors — AC5).
- `ChromeControl` (device seam for Chrome lifecycle + debug-flag prep + port teardown).
- `DevtoolsEndpoint` (verified debugging socket + DevTools /json/version endpoint check — AC2).
- `ChromeCdpSetup` (the setup orchestrator: consent -> debug-flag prep -> cold restart ->
  verified socket -> per-function readiness, plus teardown — AC1/2/4).
- `ChromePageController` (navigate + fill a form end to end with read-back proof — AC3).

**Changes vs donor (complete ledger; also in each file's provenance header):**
1. Package renamed `ai.closepaw.browser.cdp` -> `ai.eqo.browser.cdp` (incl. imports). The
   donor's `shizuku/` and `wireless/` sub-packages are NOT part of this module: `wireless/`
   was already extracted to `:adb-pairing` in TASK-008, and `shizuku/` (binder bridge) is out
   of scope for this spike. `DevtoolsHttpProtocol` is re-homed into the flat package.
2. Branding: the donor's synthetic dialog-query method `"ClosePaw.getDialog"` -> `"Eqo.getDialog"`
   and the DevTools `User-Agent: ClosePaw-DevTools-Bridge` -> `Eqo-DevTools-Bridge` (the
   branding gate rejects the upstream name in any user-visible string).
3. `ChromeCdpTarget`/`DevtoolsHttpProtocol` `PageTarget`/`DevtoolsVersion` are plain data
   classes (the donor's `@Serializable`/`@SerialName` are dropped — these are constructed by
   hand from `parseToJsonElement`, never `decodeFromString`).
4. **`containsAbstractSocket` (in `DevtoolsEndpoint`) is tightened vs donor:** the `_<pid>`
   socket suffix must be all digits, so `@chrome_devtools_remote_unrelated_thing` is rejected.
   The donor's prefix-only `startsWith("@<name>_")` would have accepted any `_<suffix>`, even
   though its comment claims it rejects that case. This is a real, if minor, correctness fix.
   The token is also recognized when it is the whole trimmed line (donor returned "" for a
   bare token).
5. ktlint formatting to this repo's style; comment-only provenance headers added.

**NOT extracted (out of scope / deferred to the device phase):** the concrete WebSocket /
socket / Shizuku transport (`CdpConnectionFactory` has no production implementation here), the
`ShizukuChromeDevtoolsBridge` / `ChromeDevtoolsUserService` binder relay, `CommandLineWriter` /
`ChromeFlagDeepLink` / `ShellRunner` (the Shizuku-backed debug-flag file writer and
chrome://flags deep-link) — these are represented by the `ChromeControl` seam instead, so the
orchestrator is host-testable and no privileged transport is wired into the module.

## 2. Host + unit verification (real output, exit codes)

Run in `worktrees/task-010/android` with
`JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`,
`ANDROID_HOME=C:\Users\<user>\Android\Sdk`.

- `./gradlew :browser-cdp:compileDebugKotlin` -> `EXIT=0`.
- `./gradlew :browser-cdp:testDebugUnitTest --rerun-tasks` -> `BUILD SUCCESSFUL`, `EXIT=0`;
  test totals from `browser-cdp/build/test-results/testDebugUnitTest/*.xml`:
  `TESTS 35 skipped 0 failures 0 errors 0`.
- `./gradlew :browser-cdp:ktlintCheck :browser-cdp:detekt` -> `BUILD SUCCESSFUL`, `EXIT=0`
  (detekt complexity findings for the extracted code are recorded in
  `android/browser-cdp/detekt-baseline.xml`, the same convention as `:adb-pairing`).
- `bash scripts/check-branding.sh` -> `BRANDING GATE PASSED`, `EXIT=0`;
  `kt files: 288; provenance rows: 288` (exact set match; `:browser-cdp` adds 18 rows).

**Full-repo gate** (the lead's verify command):
`./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2`
-> `BUILD SUCCESSFUL in 8m 34s`, `GRADLE_EXIT=0`, no FAILED tasks; all-module test totals
`TESTS 563 skipped 1 failures 0 errors 0`. `bash scripts/check.sh` -> `OK`, `CHECK_EXIT=0`
(secret scan + shell syntax + branding gate). One earlier full-gate attempt hit a stale
Gradle `in-progress-results-generic.bin` / `EOFException` in `:core-llm:testDebugUnitTest`;
`:core-llm:testDebugUnitTest` passed in isolation (`BUILD SUCCESSFUL`, `CORELLM_EXIT=0`,
261 tests / 0 failures) and the clean re-run above was green — an infrastructure flake in the
parallel test-results writer, not a test failure and unrelated to `:browser-cdp`.

The 35 unit tests cover the acceptance criteria that are host-provable: CDP framing, the
token-aware DevTools socket match and endpoint verification (AC2), the consent gate (AC1),
the setup orchestrator's consent-before-prep / verify-before-action / cleanup / typed-error
behaviour (AC1/2/4/5), and navigate + fill + read-back (AC3, against an in-memory
`FakeCdpConnection` that simulates Chrome's DevTools responses).

## 3. Acceptance criteria mapping

| AC | Where proven | Status |
|----|-------------|--------|
| Consent shown before any preparation | `CdpConsentTest`, `ChromeCdpSetupTest.consentShownBeforeAnyPreparation` | DONE (host) |
| After restart, endpoint verified before any action | `DevtoolsSetupTest`, `ChromeCdpSetupTest.readinessBlocksUntilEndpointVerifiedThenReady` / `prepareFailsTypedWhenSocketNotBound` | DONE (host) |
| Navigate and fill a local test form end to end | `ChromePageControllerTest.fillFormNavigatesFillsAndReadsBackEndToEnd` | Host-proven; **on-device PENDING owner presence** |
| Cleanup leaves no open devtools port | `ChromeCdpSetupTest.teardownLeavesNoOpenPort` / `teardownFailsTypedWhenPortRemainsOpen` | DONE (host) |
| Failures surface as typed setup errors | `CdpSetupError` sealed class; asserted across `ChromeCdpSetupTest` | DONE (host) |

## 4. Device test plan — PENDING owner presence

These require the project-only physical phone with Chrome and a debuggable preparation. They
were NOT run (owner away). Exact commands, in order. `adb` is the host-side Android SDK
platform-tools (`%ANDROID_HOME%\platform-tools\adb.exe`).

Preconditions:
```
adb devices                       # phone connected & authorized (TASK-008 pairing if needed)
adb shell getprop ro.build.version.sdk   # expect >= 30 (Android 11 floor)
```

1. **Consent (AC1).** Launch EQO, open the CDP capability surface. **PENDING owner presence.**
   Assert the informed-consent screen (`CdpConsent.consentText`) is shown BEFORE any
   preparation: `adb logcat -d | grep -i consent` shows the consent-present event before any
   `chrome-command-line` write. Decline once -> assert no `debug_flag_prepared` step and the
   typed `consent_required` error is surfaced.

2. **Debug-flag preparation.** Accept consent. EQO writes
   `/data/local/tmp/chrome-command-line` (content
   `_ --remote-debugging-socket-name=chrome_devtools_remote --enable-features=NetworkService`)
   and guides the user to flip
   `chrome://flags/#enable-command-line-on-non-rooted-devices`. **PENDING owner presence.**
   Verify: `adb shell cat /data/local/tmp/chrome-command-line`.

3. **Chrome cold restart + verified socket (AC2).** EQO force-stops and relaunches Chrome.
   **PENDING owner presence.** Then the endpoint MUST be verified before any action:
   ```
   adb shell cat /proc/net/unix | grep -F '@chrome_devtools_remote'
   adb forward tcp:9222 localabstract:chrome_devtools_remote
   curl -s http://127.0.0.1:9222/json/version   # expect a Browser/Protocol-Version JSON
   ```
   `Chrome must create its own socket; forwarding does not create it` — the `/proc/net/unix`
   line must exist from Chrome's own binding BEFORE `adb forward` is useful. If absent, the
   typed `devtools_socket_missing` error must surface (not a generic failure).

4. **Navigate + fill a local test form end to end (AC3).** Serve a local form (e.g.
   `python -m http.server` with a page containing `<input id=name>` and `<input id=email>`).
   **PENDING owner presence.** EQO's `ChromePageController.fillForm` navigates and fills the
   fields, then reads them back (`FormFillResult.allFieldsMatch == true`). Capture the run.

5. **Cleanup (AC4).** Tear down. **PENDING owner presence.** Assert no open devtools port:
   ```
   adb forward --remove tcp:9222
   adb shell cat /proc/net/unix | grep -F '@chrome_devtools_remote' || echo "socket closed"
   ```
   The typed `cleanup_incomplete` error must surface if a port remains.

6. **Typed setup errors (AC5).** Force each failure (decline consent; skip the flag; stop
   Chrome; block the socket) and assert the specific `CdpSetupError` code is surfaced, not a
   generic error. **PENDING owner presence.**

## 5. Evidence required (device logs + short screen recording) — PENDING owner presence

- **Device logs:** `adb logcat -d > task-010-logcat.txt` captured across steps 1-6, grepped for
  the setup step markers (`consent_shown`, `debug_flag_prepared`, `chrome_restarted`,
  `endpoint_verified`, `cleaned_up`) to show the AC1/AC2 ordering on-device. **PENDING.**
- **Screen recording:** `adb shell screenrecord /sdcard/task-010.mp4` covering the consent
  screen -> Chrome restart -> navigate + fill -> cleanup. **PENDING.**

## 6. Security disposition (SF-1 carry-over)

The TASK-008 security pass recorded (SF-1) that the connect-plane TLS accepts any server cert
(AOSP parity) and is loopback-only; a pinned-server requirement must be met BEFORE any CDP
relay gets a production caller. **TASK-010 does not add such a caller.** The `:browser-cdp`
module wires no concrete transport (`CdpConnectionFactory` has no production implementation),
`DevtoolsEndpoint`'s `devtoolsGet` is injected and loopback-only, and the privileged
Shizuku/wireless relay is deliberately left out (represented by the `ChromeControl` seam, not
a live trust-all connection). So the spike adds no production dependency on the trust-all
connect plane. When a production CDP caller is eventually introduced, it must land behind a
pinned-server connect plane first.

## 7. Provenance

Per-file rows for all 18 `:browser-cdp` Kotlin files are in
`android/Phase-One/evidence/task-005-provenance-map.md` (7 EXTRACT + 11 EQO-NEW), verified to
exactly cover `git ls-files 'android/**/*.kt'` (288/288) by `scripts/check-branding.sh`.
`android/browser-cdp/detekt-baseline.xml` records the extracted-code detekt complexity
findings (same convention as `:adb-pairing`).
