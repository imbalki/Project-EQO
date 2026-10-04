# Owner-return checklist — phone tests queued for the owner

Companion to `docs/agents/PHASE-ONE-STATUS.md`. This is the ordered queue of phone work that runs when the owner is back with the project phone. **Nothing in this file has been run yet:** the owner was away for TASK-008, TASK-010 and TASK-015, so every device criterion in those tasks is still **PENDING owner presence** (each task's evidence doc says so). TASK-016 (device matrix and exit review) cannot start until the records below exist.

Legend:

- **OWNER** — needs the owner's taps or a change in Android Settings on the phone.
- **LEAD** — the lead (or any agent with `adb`) can run it over USB with no owner interaction.
- **BOTH** — the lead drives `adb` while the owner taps inside a window the lead announces.

## Safety rules (absolute)

- Use only the SDK adb: `C:\Users\<user>\Android\Sdk\platform-tools\adb.exe`. A second `adb.exe` on `PATH` makes the phone show `offline`.
- Never `adb kill-server`.
- Never write secure settings (`settings put secure ...`; nothing uses `WRITE_SECURE_SETTINGS`). Read-only `settings get secure ...` is fine.
- Never touch `com.pocketpalai` (the owner's own app) or any of the owner's apps or data.
- Only one agent/worker uses the phone at a time. Announce before any irreversible or external action.
- The phone is project-only: install, run and erase only the `ai.eqo.*` packages named here.

## Device under test

Realme Narzo 20 (RMX2193), Android 11 (API 30), Realme UI 2.0, serial `<DEVICE_SERIAL>`, USB debugging authorized (connected and showing `device` as of this writing). Android 12 and 13 exist only as the `eqo-api31` / `eqo-api33` emulators — label any emulator evidence "emulator" next to physical evidence.

## Phase 0 — Prep (LEAD; owner present, phone unlocked)

1. **OWNER:** connect the phone by USB; keep it unlocked and on when a step needs taps.
2. **LEAD:**
   ```
   ADB="C:/Users/<user>/Android/Sdk/platform-tools/adb.exe"
   "$ADB" devices                                   # expect: <DEVICE_SERIAL>   device
   "$ADB" shell getprop ro.build.version.release    # expect: 11
   ```
3. **LEAD** toolchain for any build:
   ```
   export JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
   export ANDROID_HOME='C:\Users\<user>\Android\Sdk'
   cd C:\Users\<user>\Claude\project-eqo\android
   ```

## Phase 1 — TASK-015 study APK: install and first-run walkthrough (the first usable build)

Do this first so the same APK is on the phone for everything else. Source: `android/Phase-One/evidence/task-015-study-apk.md` §5.

**1a. Build + install (LEAD; 15-30 min, one Gradle run at a time):**
```
./gradlew :app:assembleDebug
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk 2>&1 | tee task-015-install.log
"$ADB" shell pm list packages | grep ai.eqo.app          # expect: package:ai.eqo.app
```
Caveat (real): the task's own install command names `app-release.apk`, but the release build here is produced **unsigned** (`app-release-unsigned.apk`) and `android/app/build.gradle.kts` has no signing config, so the unsigned release cannot sideload. Install the debug APK for the walkthrough, and settle the signing decision before claiming TASK-015 acceptance criterion 1 ("the release APK builds reproducibly and installs").

**1b. First-run walkthrough recording (BOTH):**
```
"$ADB" shell screenrecord /sdcard/task-015-first-run.mp4 &
"$ADB" shell am start -n ai.eqo.app/ai.eqo.MainActivity
```
(The launcher component is `ai.eqo.app/ai.eqo.MainActivity`; the class lives in namespace `ai.eqo`, not `ai.eqo.app` — verified on the device with `cmd package resolve-activity`.)

**OWNER** walks the app only: Set up EQO -> model key -> accessibility -> wireless debugging -> browser consent -> task screen -> legal notices. When done:
```
"$ADB" pull /sdcard/task-015-first-run.mp4
```
Expected: every screen reachable without outside instructions. Honest state: the wireless-ADB and browser rows show "did not run — not available in the study build" (the study-flow gates are off pending the TASK-008 / TASK-010 SF-1 work). Acceptance criterion 2 ("a person who has not seen the project completes setup using only the app") therefore cannot be claimed until that pinning/socket work lands.

**1c. Per-capability readiness (BOTH):** every row shows its own state. Kill the helper / toggle accessibility off mid-run and confirm only the affected row and its dependents change. Nothing may flip a row it does not own.

**1d. Approvals, pause, stop, takeover (OWNER):** run the sample task; the compose step shows the approval card with a 60s countdown; let it expire once (must cancel, not run), approve once (composer opens, nothing sent), pause mid-run, take over, stop (run ends, receipts shown), resume (dialog -> explicit confirmation required).

**1e. Recovery classes (OWNER + LEAD):** inject the faults per `PRD §7.10` — revoke the model key (401), throttle (429), cut Wi-Fi, disable accessibility mid-task, kill the helper, reboot. Each must show its named state + repair text and stay paused until an explicit resume.

**1f. No secrets in logs (LEAD):**
```
"$ADB" logcat -d | tee task-015-logcat.txt
# grep task-015-logcat.txt for the entered key material -> must be 0 hits
```

**1g. Play Protect / restricted settings (OWNER):** record what Android shows on the sideload install and on the accessibility grant (Android 13 "restricted settings"); do NOT bypass either.

## Phase 2 — TASK-008 wireless ADB pairing (device criteria D1-D8)

Source: `android/Phase-One/evidence/task-008-wireless-adb-pairing.md` §3. Device: Android 11 (OEM skin); Android 12/13 recorded as untested (no such device, by owner decision).

**Status: D1 is reachable in the merged build; D2-D7 are BLOCKED today** — they need the connect-plane server-key pinning (TASK-008 SF-1) to land and a direct runner harness/debug entry to drive the activation sequence (`WirelessAdbActivationRunner`), because the study build keeps the wireless-ADB transport off. Do not flip the study gate without the pinning work.

Build/install (LEAD):
```
cd C:\Users\<user>\Claude\project-eqo\android
./gradlew :app:assembleDebug
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
```

| # | Scenario | Who | Steps (exact commands / taps) | Expected record | Status |
| --- | --- | --- | --- | --- | --- |
| D1 | Fresh install shows activation required | LEAD (+OWNER to see it) | `"$ADB" shell pm clear ai.eqo.app` then `"$ADB" shell am start -n ai.eqo.app/ai.eqo.MainActivity`, screenshot `"$ADB" exec-out screencap -p > d1.png` | "Activation required" visible; the privileged entry point refuses with guidance naming "Developer options" > "Wireless debugging" > "Pair device with pairing code". (In the merged build the first-run surface is the TASK-015 setup hub; record what is actually shown.) | Run now |
| D2 | Guided pairing succeeds | OWNER taps + LEAD | Settings > "Developer options" > "Wireless debugging" ON; tap "Pair device with pairing code"; read the six-digit "Pairing code" and the dialog's "IP address & Port"; also read the "IP address & Port" on the "Wireless debugging" screen (connection port). Run the activation sequence with both ports and the code; record each of the five checks separately | PAIR passes, CONNECT passes (mTLS A_CNXN against the connection port); helper checks recorded separately | BLOCKED (SF-1 + harness) |
| D3 | Wrong code recovers with guidance | OWNER | D2 but type a stale/incorrect code (or let the dialog expire) | PAIR fails with WrongCode guidance ("Pairing code" rejected; reopen "Pair device with pairing code"); later checks NotRun | BLOCKED |
| D4 | Port confusion recovers with guidance | OWNER | D2 but enter the connection port in the pairing-port field | PAIR fails with PortConfusion guidance naming "Pair device with pairing code", "Pairing code" and "IP address & Port" | BLOCKED |
| D5 | Revoke recovers with guidance | OWNER | After a passing D2: Settings > "Developer options" > "Revoke USB debugging authorizations", then re-run CONNECT | CONNECT fails with PairingRevoked guidance naming "Revoke USB debugging authorizations"; re-pair from "Wireless debugging" | BLOCKED |
| D6 | Reboot recovers with guidance | OWNER + LEAD | After a passing D2: `"$ADB" reboot`, `"$ADB" wait-for-device`, re-run the sequence with the OLD ports | CONNECT fails with DeviceRebooted guidance ("Developer options" > "Wireless debugging", new "IP address & Port") | BLOCKED |
| D7 | Wi-Fi change recovers with guidance | OWNER | After a passing D2: switch the phone to another Wi-Fi network, re-run the sequence | CONNECT fails with WifiChanged guidance ("Wi-Fi", reopen "Wireless debugging", new "IP address & Port") | BLOCKED |
| D8 | Android 12 and 13 (+ OEM skin) | LEAD (emulator) | Same as D2 on the `eqo-api31` / `eqo-api33` emulators | Record per device; if no 12/13 device exists, record "untested" (owner decision) | BLOCKED |

## Phase 3 — TASK-010 Chrome CDP (device acceptance criteria)

Source: `android/Phase-One/evidence/task-010-chrome-cdp-spike.md` §4. Requires the project phone with Chrome.

**Status: BLOCKED** — the module wires no production CDP transport (`CdpConnectionFactory` has no implementation; the privileged relay is deliberately left out) and the study build keeps `CHROME_CDP_IN_STUDY_FLOW = false`. Run only after a production caller and the socket-owner verification (TASK-010 SF-1) land.

Preconditions (LEAD):
```
"$ADB" devices
"$ADB" shell getprop ro.build.version.sdk      # expect >= 30 (Android 11 floor)
```
1. **Consent (AC1, OWNER):** launch EQO, open the CDP capability surface; assert the consent screen is shown BEFORE any preparation (`"$ADB" logcat -d | grep -i consent` shows the consent-present event before any `chrome-command-line` write); decline once -> no `debug_flag_prepared` step and the typed `consent_required` error.
2. **Debug-flag preparation (BOTH):** accept consent; EQO writes `/data/local/tmp/chrome-command-line` (content `_ --remote-debugging-socket-name=chrome_devtools_remote --enable-features=NetworkService`) and guides the owner to flip `chrome://flags/#enable-command-line-on-non-rooted-devices`. Verify: `"$ADB" shell cat /data/local/tmp/chrome-command-line`.
3. **Chrome cold restart + verified socket (LEAD):** EQO force-stops and relaunches Chrome. Then the endpoint must be verified before any action:
   ```
   "$ADB" shell cat /proc/net/unix | grep -F '@chrome_devtools_remote'
   "$ADB" forward tcp:9222 localabstract:chrome_devtools_remote
   curl -s http://127.0.0.1:9222/json/version      # expect Browser/Protocol-Version JSON
   ```
   Chrome must create its own socket; forwarding does not create it. If the socket is absent, the typed `devtools_socket_missing` error must surface (not a generic failure).
4. **Navigate + fill a local test form end to end (AC3, BOTH):** serve a local form (`python -m http.server` with a page containing `<input id=name>` and `<input id=email>`); EQO navigates and fills, then reads the fields back (`FormFillResult.allFieldsMatch == true`). Capture the run.
5. **Cleanup (AC4, LEAD):**
   ```
   "$ADB" forward --remove tcp:9222
   "$ADB" shell cat /proc/net/unix | grep -F '@chrome_devtools_remote' || echo "socket closed"
   ```
   Assert no open devtools port; the typed `cleanup_incomplete` error must surface if a port remains.
6. **Typed setup errors (AC5, BOTH):** force each failure (decline consent; skip the flag; stop Chrome; block the socket) and assert the specific `CdpSetupError` code, not a generic error.

Evidence (LEAD): `"$ADB" logcat -d > task-010-logcat.txt` across steps 1-6, grepped for the step markers (`consent_shown`, `debug_flag_prepared`, `chrome_restarted`, `endpoint_verified`, `cleaned_up`); and a short screen recording `"$ADB" shell screenrecord /sdcard/task-010.mp4` covering consent -> restart -> navigate/fill -> cleanup, then `"$ADB" pull`.

## Phase 4 — TASK-016 device matrix and exit review

Source: `agents/android/tasks/TASK-016-device-matrix-exit-review.md`. Depends on the records above. Run every exit gate on Android 11 (physical) plus Android 12 and 13 (emulator and/or physical; label which). Record pass / fail / defect id per gate. **Gate 6 is recorded as "deferred by owner decision D-008"** — not pass and not fail.

The eight exit gates (`android/Phase-One/FEASIBILITY-REPORT.md` §8):

1. Reproducible build of the combined EQO APK on the pinned toolchain (lint clean or baseline-shrunk with named findings) — host.
2. Integrated EQO-branded helper: starts over wireless ADB, authorizes EQO, survives binder death — device.
3. Wireless ADB pairing + authenticated connection + loss/revocation/reboot recovery — device (Android 11 + 12/13).
4. Accessibility observe/tap/scroll/text on a test app — device.
5. Chrome + CDP navigate + fill a test form after the documented debug prep/restart — device.
6. Virtual display — **DEFERRED (D-008)**.
7. Pause / Stop / takeover with in-flight action reconciliation — device.
8. Branding scan clean; secrets never in prompts/logs; approvals enforced — host + device.

Output: `android/Phase-One/evidence/exit-review.md` with the gate table, the open defects with owners, and a recommendation (proceed to Phase Two planning, or a fix list).

## Phase 5 — Clean-up (LEAD; ~5 min)

- **Leftover helper process:** `"$ADB" shell ps -A | grep eqo_helper`. The helper exits when activation is revoked; if a stray `eqo_helper_server` remains after the tests, stop it only inside a window announced in the task thread (it is restarted by a new activation). Never `adb kill-server`.
- **Test packages** to remove once the device work is over (switch the `ai.eqo.test` accessibility service off first in Settings > Additional Settings > Accessibility > Downloaded services):
  ```
  "$ADB" uninstall ai.eqo.test                 # TASK-009 instrumentation
  "$ADB" uninstall ai.eqo.app.test             # TASK-007 instrumentation
  "$ADB" uninstall ai.eqo.helper.client.test   # TASK-007 mismatched-permission fixture
  ```
- **`ai.eqo.app`** (the study build): uninstall only if the owner is done testing.
- **Never** uninstall or touch `com.pocketpalai`.

## Carry-overs — what must be done before

| Source | What | Must be done before |
|---|---|---|
| TASK-007 SF-1 | The helper keeps upstream's `WRITE_SECURE_SETTINGS` self-grant to the manager (`helper-server/.../ShizukuService.java`); where the server runs as root it silently grants EQO `WRITE_SECURE_SETTINGS` | Remove or consent-gate it before release |
| TASK-007 SF-2 / SF-3 | No signing-cert / uid pin if the manager allowlist grows past one id; no binder-provenance check if the broadcast is ever sent cross-uid | Before the manager allowlist grows past one id / before the broadcast is sent cross-uid |
| TASK-008 SF-1 | The connect-plane TLS accepts any server certificate (AOSP parity; loopback-only guard + annotations only) | Post-pairing server-key pinning / enrollment / rotation before any production caller uses the connect plane; it also blocks TASK-008 D2-D7 and the TASK-015 study gate |
| TASK-010 SF-1 | The DevTools endpoint check is name-only | Socket-owner verification before any production CDP caller; it also blocks the TASK-010 device criteria and the TASK-015 study gate |
| TASK-015 notes | Study-flow gates off (wireless-ADB + CDP transports not in the study build); release APK is unsigned (no signing config); the sample plan is a fixed 3-step demo; the model-list half of REQ-BYOK-01 is not implemented | A signing decision + finishing the onboarding gaps before claiming TASK-015 criteria 1-2 and before the exit review |
| Android 12/13 untested | No such device exists; only the `eqo-api31` / `eqo-api33` emulators | Test on 12/13, or record "untested", before TASK-016 gates 3/5/8 |
| Shizuku naming | `NOTICE` flags the trademark question and claims nothing | Settle before any public distribution |
| Issues #44 and #50 | Open follow-ups: #44 (cost-disclosure gate + redaction wiring), #50 (residual secure-window / untrusted-screen-text items) | Close as the work lands, before release |
