# Emulator feasibility probe — Android 12+ testing (read-only)

- Author: eqo-qa worker (board task t_581a71c6)
- Date: 2026-10-04 (~10:20 IST)
- Mandate: READ-ONLY. No Gradle run, no emulator started, no system image
  downloaded, no AVD created, no installs, phone untouched. All facts below
  come from existing SDK state, read-only CLI probes (`emulator -accel-check`,
  `-version`, `sdkmanager --list[_installed]`, `avdmanager list avd`), a
  read-only HTTPS fetch of Google's repository XML, filesystem/registry reads,
  and one prior launch captured in `C:\Users\<user>\Claude\worktrees\emu-diag.log`
  (from 2026-10-01, an earlier session — not this probe).

## TL;DR

- **Verdict: feasible.** The emulator (37.2.12) is installed, WHPX hardware
  acceleration is **installed and usable** (verified twice: `emulator
  -accel-check` exits 0 saying "WHPX(10.0.26200) is installed and usable", and
  a prior 2026-10-01 launch reached "Windows Hypervisor Platform accelerator is
  operational" in `emu-diag.log`).
- Two AVDs already exist: **eqo-api31 (Android 12)** and **eqo-api33
  (Android 13)**, both Pixel 6-profile, 2 GB RAM, 4 cores. Reusable as-is.
- Remote catalog confirms google_apis x86_64 images exist for API 31–36
  (zip 1.47–1.90 GB, ~6–8 GB unpacked each). 63 GB free disk is enough for the
  remaining levels (34, 35, 36) but leaves little slack; budget per image.
- **The memory budget is the real constraint** (16 GB total, ~0.9 GB free with
  the current Gradle daemons resident). Emulator (~2.6 GB host footprint) and
  an active Gradle build (~4–5 GB) **do not cohabit safely** on 15.7 GB usable.
  Serialize: stop Gradle daemons (`gradlew --stop`), run the emulator, then
  build. First emulator boot budget 5–15 min depending on load.
- Recommended API order: **31 (Android 12) → 33 (Android 13) → 35 (Android 15,
  includes 16 KB page-size variant) → 36 (Android 16) → 34 (Android 14)**.
  Skip 32 (minor delta, extra disk); physical phone covers 30/Android 11 OEM
  behaviour. Do 31/33 first — zero downloads needed.

---

## 1. SDK state

SDK root: `C:\Users\<user>\Android\Sdk`

| Component | State |
|---|---|
| `emulator/emulator.exe` | Installed, v37.2.12 (build_id 16428233, 2024-era) |
| `emulator/qemu/windows-x86_64/qemu-system-x86_64-headless.exe` | Present (used by prior headless launch) |
| `cmdline-tools/latest` (sdkmanager.bat, avdmanager.bat) | Present, SDK Manager CLI 23.0.0 (deprecation note: new `android` CLI suggested) |
| `platform-tools` 37.0.1 | Installed |
| `platforms/android-36` 2.0.0 | Installed (supports targetSdk 36 = EQO's target) |
| `build-tools/36.0.0`, cmake 3.31.6, NDK 28.2.13 + 29.0.14 | Installed |
| System images installed | `system-images/android-31/google_apis/x86_64` (rev 14) **4.53 GB**, `system-images/android-33/google_apis/x86_64` (rev 17) **8.77 GB** — both complete (system.img, vendor.img, kernel-ranchu, ramdisk, etc. present) |
| SDK total on disk | **19.71 GB** |

AVDs (`avdmanager list avd` with `ANDROID_AVD_HOME=C:\Users\<user>\Android\avd`):

| AVD | Image | Profile | Config highlights |
|---|---|---|---|
| eqo-api31 | android-31 google_apis x86_64 | pixel_6 | 2 GB RAM, 4 cores, data 10G, 1080x2400@420, PlayStore off |
| eqo-api33 | android-33 google_apis x86_64 | pixel_6 | same shape |

- AVD home is the **non-default** `C:\Users\<user>\Android\avd`; it is **not**
  persisted in user/machine environment (`ANDROID_AVD_HOME` is empty in
  registry and process env). The prior session set it via script
  (`$env:ANDROID_AVD_HOME='C:\Users\<user>\Android\avd'` in
  `worktrees/emu-test.ps1`). A future run card must set it explicitly, or
  `avdmanager`/`emulator` will look in the default
  `%USERPROFILE%\.android\avd` and find nothing.
- AVD userdata qcow2 files exist (eqo-api31 userdata-qemu.img.qcow2
  566 MB) — the AVDs have been booted before (see §2).
- `emu-test.ps1` / `emu-wait.ps1` / `emu-diag.log` in
  `C:\Users\<user>\Claude\worktrees\` are the prior session's harness:
  headless boot of eqo-api31 with `-no-window -no-audio -no-snapshot -gpu
  swiftshader_indirect -memory 3072`. Note the prior script overrides RAM to
  3 GB (`-memory 3072`); a future run should keep 2 GB to protect this host.

## 2. Hardware acceleration (Windows 11 Home)

| Probe | Result |
|---|---|
| `emulator -accel-check` (2026-10-04) | exit 0 — **`WHPX(10.0.26200) is installed and usable.`** |
| `emu-diag.log` (2026-10-01 launch) | "Ok: Hypervisor compatibility to run avd: eqo-api31 are met", "WHPX on Windows 10.0.26200 detected", **"Windows Hypervisor Platform accelerator is operational"**, QEMU boot config built with `-enable-whpx` |
| `systeminfo` | "**A hypervisor has been detected**"; Virtualization-based security: Running; OS: Windows 11 Home Single Language, build 26200 |
| CPU | 12th Gen Intel Core i5-1240P, 12 cores / 16 logical processors |
| `Win32_Processor.VirtualizationFirmwareEnabled` | reports False — **expected** when a hypervisor is already running (VT-x is in use by WHPX; the working accel-check is the authoritative answer, not this flag) |
| Hyper-V feature bits | Not enumerable without admin (`Get-WindowsOptionalFeature`/`dism` need elevation; `bcdedit` likewise). WHPX being fully operational is a stronger, direct signal than any optional-feature flag, and matches "hypervisor detected". **No feature was enabled or changed by this probe.** |

Conclusion: hardware acceleration is a **pass**. The emulator will run on
WHPX threads, not pure software emulation. Windows 11 Home lacks Hyper-V
Manager, but the emulator does not need it — it needs the Windows Hypervisor
Platform API, which is proven working.

Vendor GPU note: `emu-diag.log` shows NVIDIA GPU present (`10de:25a9`). The
prior launch used `-gpu swiftshader_indirect` headless, which is the safe lane
for a memory-constrained headless box; `-gpu host` is unnecessary and risks
GPU/compositor contention while other workers run.

## 3. Disk space (SDK drive = C:)

| Item | Value |
|---|---|
| C: total / free | 476 GB / **63 GB free** (87% used) |
| SDK | 19.71 GB |
| Remote image zip sizes (google_apis x86_64, from Google repo XML 2026-10-04) | API 31: 1.47 GB, 32: 1.54 GB, 33: 1.71 GB, 34: 1.56 GB, 35: 1.74 GB, 36: 1.90 GB |
| Unpacked on-disk estimate (~4x zip; observed API31=4.5 GB, API33=8.8 GB on this machine) | ≈ 6–8 GB per image |

Headroom: adding API 34+35+36 (~23–27 GB unpacked) leaves ~35 GB free — workable.
Smaller alternative for CI-style/lighter boots: **ATD images**
(`aosp_atd`/`google_atd`) at 0.55–0.86 GB zip / ~2.2–3.4 GB unpacked — fine for
instrumented/connected tests but stripped UI; prefer google_apis for manual
onboarding/a11y walks.

16 KB page-size images (owner's API-35 interest): `google_apis_ps16k` x86_64
exists for API 35 (1.70 GB zip) and 36 (1.85 GB); `google_apis_playstore_ps16k`
also exists for 35/36. These boot with 16 KB pages and validate
native-lib/page-size compatibility — relevant to EQO only if it ships native
code (ClosePaw/Shizuku-derived native pieces); EQO itself is Kotlin-first, so
treat as a one-time smoke check, not a matrix dimension.

## 4. Memory: emulator vs Gradle build on 15.7 GB

Measured at probe time (2026-10-04, while Gradle daemons idle in background):

- Total physical: 16,088 MB (~15.7 GiB usable)
- **Free physical: 903,968 KB (~0.86 GB)**; commit charge 45,084 MB of 50,756 MB max (89%)
- Running: two java processes (Kotlin/Gradle daemons) at 2,294 MB + 1,066 MB WS together, claude desktop ~0.84 GB + 0.33 GB, vmmemWSL 0.55 GB, chrome 0.42 GB, MemCompression 0.27 GB, MsMpEng 0.25 GB
- EQO Gradle config: `android/gradle.properties` sets `org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g` → a build can reach ~4–5 GB in one JVM plus a separate Kotlin daemon at up to 4 GB (2.3 GB observed idle). **This exceeds the task brief's 1.5–2.5 GB build assumption.**

Budget table (host footprint, guest RAM + QEMU/WHPX overhead ≈ 25–30%):

| Workload | Host RAM footprint |
|---|---|
| One emulator, 2 GB guest (`hw.ramSize=2G`) | ≈ 2.4–2.8 GB |
| Emulator at 3 GB (`-memory 3072`, as prior script did) | ≈ 3.5–4 GB |
| One Gradle build (Gradle daemon + Kotlin daemon, `-Xmx4g`) | ≈ 4–5 GB (idle daemon already 3.3 GB) |
| Windows 11 + agents/desktop chrome/WSL baseline | ≈ 5–6 GB (free was only ~0.9 GB with daemons idle) |

**Can emulator + build run at the same time? Effectively no.** With baseline
already eating to 0.9 GB free, adding 2.6 GB (emulator) + 4–5 GB (active
build) pushes commit charge past 50 GB — hard paging or OOM. The task brief's
"1.5–2.5 GB build" does not match this repo's `-Xmx4g` config. **Serialize:**
stop Gradle daemons (`android\gradlew --stop` from the repo, or let them
expire), run the emulator, kill it, then build. On 15.7 GB even the emulator
alone needs the daemons stopped (~3.3 GB freed) to boot comfortably.

## 5. Remote package availability (API 31–36, x86_64)

Read-only `sdkmanager --list` + Google repository XML fetch (2026-10-04), API 31–36:

| Package variant | API 31 | 33 | 34 | 35 | 36 |
|---|---|---|---|---|---|
| google_apis (full, manual-testing grade) | 14.0.0 | 17.0.0 | 14.0.0 | 9.0.0 | 7.0.0 |
| google_apis_playstore (Play Store baked in, not needed for EQO) | 9.0.0 | 9.0.0 | 14.0.0 | 9.0.0 | 7.0.0 |
| google_atd / aosp_atd (CI-style, light) | yes / yes | yes / yes | 1.0.0 / yes | 1.0.0 / 1.0.0 | 1.0.0 / 1.0.0 |
| google_apis_ps16k (16 KB pages) | — | — | — | 1.0.0 (1.70 GB) | 1.0.0 (1.85 GB) |

API 32 images also exist; API 30 (Android 11) exists too but the physical
Realme already covers the Android-11 OEM lane (§8).

Download sizes (zip): see §3. All licenses already accepted in SDK
(`licenses/` present) — `sdkmanager --install` would be non-interactive for
these package ids.

## 6. What the emulator can and cannot verify for EQO

**Can verify (emulator = AOSP/Google baseline, no OEM skin):**
- Accessibility service: enable via Settings UI or `adb shell settings put
  secure enabled_accessibility_services ...`; service binding, a11y events,
  `dumpsys accessibility`. (EQO-ONB-003, EQO-A11Y-001.)
- Full manual onboarding walks per API level: BYOK key entry
  (EQO-BYOK-001/002, EQO-ONB-001/002), first-run "activation required" gating.
- CDP / Chrome-in-emulator: Chrome can be sideloaded on google_apis images
  (no Play Store needed); EQO-CDP-001/002/004 style flows; webview behavior.
- Instrumented tests (connectedAndroidTest) per API level — the G3 gate
  (API 30/31/33/34/35/36 lanes) can be reproduced locally on this machine's
  emulator rather than only in CI.
- Pause/Stop, approval UI, virtual-display behaviors at OS-version level
  (EQO-PS-*, EQO-APR-*, EQO-VD-001..004 for the API-version dimension).
- 16 KB page-size smoke on API 35/36 ps16k images (if any native lib ships).
- **Helper activation via `adb shell`: probably yes.** google_apis images are
  userdebug builds with adb root available; the Shizuku-style helper start
  (rish/starter path) is typically exercisable headlessly. Needs confirmation
  on the first run card, not guaranteed for the app's exact helper flow.
  Wireless debugging on-emulator (API 30+ toggle exists) may even pair
  loopback — do not treat as evidence for the phone flow (§8).

**Cannot verify (emulator limitation):**
- **Wireless-debugging pairing as the user experiences it** (mDNS
  discovery `_adb-tls-connect`, pairing codes, Wi-Fi event handling): the
  emulator's network stack is NAT/simulated; pairing UX and failure modes
  (EQO-WADB-004/005) are not faithful. Treat emulator runs as non-authoritative
  for pairing; keep EQO-WADB-006/007 (reboot/revoke persistence) and
  EQO-WADB-008/009 on the real device.
- **Realme/ColorOS OEM behaviour** on the owner's RMX2193: battery/autostart
  killing, Realme UI 2.0 permission dialogs, OEM accessibility quirks — the
  physical Android-11 phone remains the only OEM-skin lane (§8,
  EQO-OEM-002/Oppo-ColorOS family, and D-007's Realme requirement).
- Play-facing surfaces (Play Integrity, Play install/update paths) — not
  applicable to a sideloaded APK anyway.
- True low-memory device behaviour (EQO-VD-005 4 GB-class): a 2 GB guest is a
  reasonable proxy but not a 4 GB retail device; treat as indicative only.

## 7. Recommendation: which API levels first

MinSdk is 30 (Android 11) per D-007; physical Realme covers 11. Emulator
priority, balancing risk × setup cost:

1. **API 31 — Android 12** — image + AVD already on disk, zero download, most
   linear path to a first validated emulator run. Start here (unblocks
   TASK-008/016 Android-12 lanes immediately).
2. **API 33 — Android 13** — image + AVD already present (second zero-cost
   lane; covers notification-permission and per-app-language API changes).
3. **API 35 — Android 15** — google_apis 1.74 GB zip (≈7 GB disk); includes
   the owner's **16 KB page-size** interest (ps16k 1.70 GB) and app-compat
   framework changes. Do this before 36 (15 is the runtime most Android-16
   devices actually ship with today).
4. **API 36 — Android 16** — targetSdk 36 is already in the build; a 36 lane
   mirrors CI's instrumented API-36 lane. 1.90 GB zip.
5. **API 34 — Android 14** — lowest incremental risk vs 33/35; slot last or
   skip if disk pressure. 1.56 GB zip.
   API 32: skip — minor delta between 31 and 33, extra ~6 GB disk, no test
   matrix entry in Phase-One plans names 32.

Full matrix = 31, 33, 34, 35, 36 (5 images). With 31+33 already installed,
three downloads (~5.2 GB zips, ~21 GB unpacked) complete the set.

## 8. Step list for the future run card (do NOT run now)

State: env — set `ANDROID_AVD_HOME=C:\Users\<user>\Android\avd`
(prefer user-level `setx` so it survives across sessions/workers) and keep
`ANDROID_HOME` pointing at the SDK.

1. Pre-flight (this probe's rules apply until a run card is created): confirm
   no Gradle build is running and RAM free ≥ 6 GB; if daemons are resident,
   run `android\gradlew --stop` (or wait for idle timeout) before launching.
2. Boot an existing AVD headless (eqo-api31 first):
   `emulator -avd eqo-api31 -no-window -no-audio -no-snapshot -gpu swiftshader_indirect`
   (keep 2 GB `hw.ramSize`; do not copy the older `-memory 3072`).
3. Wait for boot: poll `adb shell getprop sys.boot_completed` == 1 (first
   cold boot 5–15 min on this machine; subsequent boots faster).
4. Install EQO APK: `adb install -r <app-debug|release>.apk`.
5. Activation first: verify "activation required" gating, then a11y enable
   (UI walk EQO-ONB-003; scripted `settings put` for repeats).
6. Helper activation via adb shell: attempt the app's supported path; if the
   app's helper requires wireless pairing, verify with `adb shell cmd
   connectivity`/wifi loopback only as a smoke — record as "emulator, not
   evidence for on-device pairing".
7. Scenario sweep per TASK-016 gate table (BYOK, approvals, Pause/Stop,
   CDP with sideloaded Chrome) recording `getprop` + `logcat` + screenshots;
   label evidence "SDK emulator x86_64 google_apis - NOT the OEM skin device".
8. Serialize discipline: kill the emulator (`adb emu kill`) before any Gradle
   build starts; never co-run build + emulator (see §4).
9. Add missing levels when approved: `sdkmanager "system-images;android-3X;
   google_apis;x86_64"` then `avdmanager create avd -n eqo-api3X -d pixel_6
   -k "system-images;android-3X;google_apis;x86_64"`, reusing the
   eqo-api31/33 config shape (2 GB RAM, 4 cores, 1080x2400@420).
10. Cleanup: delete created AVDs/images if space drops below ~30 GB free.

## 9. Risks / caveats

- **Memory is the binding constraint, not hardware acceleration.** Everything
  else (WHPX, images, AVDs, disk, licenses) is already in place; 15.7 GB with
  resident Gradle daemons is what forces serialization.
- WHPX is enabled at the OS level — no admin change needed; do not "fix"
  `VirtualizationFirmwareEnabled=False` (it is an artifact of a running
  hypervisor, not a defect).
- `sdkmanager --list` prints a deprecation notice and suggests the new
  `android` CLI; sdkmanager still works and was used read-only here. Future
  installs may also use `android sdk` — same package ids.
- AVD home not persisted → any worker/script that calls `avdmanager` or
  `emulator` without `ANDROID_AVD_HOME` set will report "no AVDs". Fix once at
  env level, or every run card must set it.
- Emulator 37.2.12 supports API ≤ 37-era images; do not pair it with
  experimental canary images. Stable API 31–36 images are all compatible.
- 16 KB: only API 35/36 ps16k images exercise 16 KB pages; EQO is
  Kotlin/Compose-first, so scope to a smoke test unless native libs appear.
- vmms (WSL2, 0.55 GB observed) adds baseline load; if WSL workloads are
  heavy, treat that as part of the baseline already accounted for in §4.

## 10. Answers to the task's five probes

1. SDK: emulator installed (37.2.12); system images installed: android-31 and
   android-33 google_apis x86_64; AVDs exist: eqo-api31, eqo-api33.
2. Acceleration: WHPX installed + usable (accel-check exit 0, prior boot
   reached "accelerator is operational"); hypervisor detected by systeminfo;
   CPU 12th-gen i5-1240P 12C/16T; C: has 63 GB free. Nothing was enabled.
3. Remote catalog: google_apis x86_64 images exist for API 31–36 (also
   google_apis_playstore, ATD variants, and 16 KB ps16k for API 35/36); zip
   sizes 1.47–1.90 GB, ~6–8 GB unpacked. Read-only fetch succeeded.
4. Memory: one 2 GB emulator ≈ 2.4–2.8 GB host; one Gradle build with this
   repo's `-Xmx4g` config ≈ 4–5 GB (brief's 1.5–2.5 GB understates it).
   Together with baseline ≈ 11–14 GB commitments beyond what 15.7 GB provides
   with daemons resident → **run serially, not in parallel**.
5. Recommendation: 31 → 33 → 35 (incl. 16 KB smoke) → 36 → 34; skip 32;
   physical Realme stays the only OEM/Android-11 lane; emulator verifies
   a11y/BYOK/CDP/instrumented/OS-level behaviour but not wireless pairing or
   Realme OEM behaviour; helper activation via adb shell probably yes
   (google_apis is userdebug); run-card steps in §8.