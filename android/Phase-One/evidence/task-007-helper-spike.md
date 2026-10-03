# TASK-007 Evidence — EQO-branded privileged helper spike (S1)

Branch: `agent/android/12-helper-spike` (from `origin/main` `edf9403`; re-based
onto current `origin/main` `e6f82b1` per the TASK-007 fix card)
Area: android (`:helper-server`, `:helper-client`, `:app` wiring) · Gate 2 (DEV-06, DEV-07)
Device: physical **Realme RMX2193 (Narzo 20), Android 11**, serial `<DEVICE_SERIAL>` (owner phone)
Date: 2026-10-03 (run 82)

## Upstream provenance (verified commits)

Study clones at the FEASIBILITY-REPORT §1 pins (both SHAs verified with `git rev-parse HEAD`):

- `RikkaApps/Shizuku` @ `b844bc491f1790c72328e1a8e5b2349f8978f0ea`
- `RikkaApps/Shizuku-API` @ `a27f6e4151ba7b39965ca47edb2bf0aeed7102e5`
  (`git -C shizuku ls-tree HEAD api` -> `a27f6e4151ba7b39965ca47edb2bf0aeed7102e5` — Shizuku's
  `api` submodule pins exactly this Shizuku-API commit; the two pins are consistent.)

Every forked file carries `// Origin: <repo> @ <sha>, path: <original upstream path>`.
Kotlin files are listed in `task-005-provenance-map.md` (TASK-007 section, kind FORK/EQO-NEW).

## Scope of the fork

`:helper-server` = Shizuku `server/` + `starter/` + `common/` + manager native starter
(`starter.cpp misc.cpp selinux.cpp cgroup.cpp` + headers) + Shizuku-API `server-shared/`
+ EQO-authored `EqoManagerAllowlist` + manager-role provider/manifest. `:helper-client` =
Shizuku-API `aidl/` + `shared/` + `api/` + `provider/` + EQO-authored
`HelperActivationState`. **Documented spike cuts** (comments at each site):
rish remote shell (Shizuku-API `rish/` + its native pty host; `Service`/`ShizukuService`/
`starter.cpp` rish wiring removed), and the `adb_pairing.cpp` wireless-pairing library
(TASK-008 scope). The fork runs **without** `dev.rikka.tools.refine`: `BinderSender`'s
`ActivityManagerHidden` constants are inlined (values copied from AOSP android11-release
`ActivityManager.java:492,743-755`) and `UserService`'s two refine-only calls became
reflection (the method already used reflection for the adjacent hidden calls).

## D-004 lockstep rename (server and client in one change)

| Site | Upstream | EQO |
|---|---|---|
| application id (ServerConstants, starter.cpp `PACKAGE_NAME`, ServiceStarter, ShizukuProvider) | `moe.shizuku.privileged.api` | `ai.eqo.app` (EQO's `applicationId`, app/build.gradle.kts:12) |
| permission strings (ServerConstants, BinderSender, ShizukuProvider, manifests) | `moe.shizuku.manager.permission.API_V23` / `.MANAGER` / `permission-group.API` | `ai.eqo.app.helper.permission.API_V23` / `.MANAGER` / `ai.eqo.app.helper.permission-group.API` |
| binder extra key (ShizukuService, ServiceStarter, ShizukuManagerProvider, ShizukuProvider) | `moe.shizuku.privileged.api.intent.extra.BINDER` | `ai.eqo.app.helper.intent.extra.BINDER` |
| provider authority suffix (server, starter, client, manifest) | `<pkg>.shizuku` | `<pkg>.helper` |
| broadcast action (ShizukuProvider) | `moe.shizuku.api.action.BINDER_RECEIVED` | `ai.eqo.app.helper.action.BINDER_RECEIVED` |
| server process name (starter.cpp, ShizukuService) | `shizuku_server` | `eqo_helper_server` |
| starter binary (CMake target, packaged in APK) | `libshizuku.so` | `libeqo-starter.so` |

Class and package names are kept per D-004 (`rikka.shizuku.*`, `moe.shizuku.*`,
`moe.shizuku.server.IShizukuService` binder descriptor). The "manager app must be
installed" hard gate (`ShizukuService` ctor + `MANAGER_APPLICATION_ID` equality) is
replaced by the **EQO app-id allowlist** (`EqoManagerAllowlist`, ids: `ai.eqo.app`):
`checkCallerManagerPermission`, `checkCallerPermission`, `attachApplication`,
`dispatchPermissionConfirmationResult`, `getFlagsForUid`/`updateFlagsForUid` and the
manager lookup all go through it.

## Acceptance criteria — commands and real output

All device commands use only `C:\Users\<user>\Android\Sdk\platform-tools\adb.exe -s <DEVICE_SERIAL>`.
Activation is always: `adb shell <nativeLibraryDir>/libeqo-starter.so --apk=<base.apk>`.

### 1. Starter builds with the pinned NDK — PASS

**Correction (fix 2, 2026-10-03):** the original 58-second build and device runs
used AGP's default NDK **28.2.13676358**, not the previously claimed r29.
`:helper-server` did not set `ndkVersion`; the review identified its r28 CMake
cache, Clang 19.0.1 stamp and matching packaged binary. Those historical device
results below remain r28 results. No phone was touched in this fix run.

`:helper-server/build.gradle.kts` now explicitly sets `ndkVersion = "29.0.14206865"`.
A repository-wide `externalNativeBuild`/`ndkVersion` inspection found only
`:helper-server` builds native code; `:app` packages its JNI output and
`:helper-client` builds no native code, so no additional module pin is needed.
CMake remains 3.31.6. From `android/`:

```
./gradlew clean :helper-server:assembleDebug :helper-server:testDebugUnitTest --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m
BUILD SUCCESSFUL in 1m 49s
62 actionable tasks: 52 executed, 10 up-to-date
```

The initial attempt compiled the r29 starter but failed compiling the new JVM
test because Android's `Files` API lacks `readString`; switched the test to
UTF-8 `readAllBytes`, then re-ran the command above from clean successfully.
The full gate below also built Debug and Release APKs with r29.

Real proof commands (from `android/`; `python` is the installed interpreter):

```bash
python -c 'from pathlib import Path; roots=[Path("helper-server/.cxx/Debug/1p0392v6"),Path("helper-server/.cxx/RelWithDebInfo/205kz385")]; [(print(p),print("\n".join(s for s in p.read_text().splitlines() if s.startswith(("ANDROID_NDK:","CMAKE_ANDROID_NDK:"))))) for root in roots for p in sorted(root.rglob("CMakeCache.txt"))]'
python -c 'from pathlib import Path; roots=[Path("helper-server/.cxx/Debug/1p0392v6"),Path("helper-server/.cxx/RelWithDebInfo/205kz385")]; [(print(p),print("\n".join(s for s in p.read_text().splitlines() if s.startswith("set(CMAKE_CXX_COMPILER ") or s.startswith("set(CMAKE_CXX_COMPILER_VERSION "))))) for root in roots for p in sorted(root.rglob("CMakeCXXCompiler.cmake"))]'
```

All four ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) in both active
configuration directories report (representative arm64 output):

```
helper-server/.cxx/Debug/1p0392v6/arm64-v8a/CMakeCache.txt
ANDROID_NDK:UNINITIALIZED=C:\Users\<user>\Android\Sdk\ndk\29.0.14206865
CMAKE_ANDROID_NDK:UNINITIALIZED=C:\Users\<user>\Android\Sdk\ndk\29.0.14206865
helper-server/.cxx/Debug/1p0392v6/arm64-v8a/CMakeFiles/3.31.6/CMakeCXXCompiler.cmake
set(CMAKE_CXX_COMPILER "C:/Users/<user>/Android/Sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe")
set(CMAKE_CXX_COMPILER_VERSION "21.0.0")
```

`clean` retains old, unused `.cxx` configuration caches: Debug `4l261es6` and
RelWithDebInfo `3i4o3j65` still record r28. Do not mistake them for this build's
active r29 hashes above. The packaged APK compiler stamp independently proves
that the actual starter output is r29, not just a changed Gradle declaration:

```bash
python -c 'import os,zipfile,subprocess; from pathlib import Path; z=zipfile.ZipFile("app/build/outputs/apk/debug/app-debug.apk"); [(print(i.filename,i.file_size)) for i in z.infolist() if i.filename.endswith("libeqo-starter.so")]; p=Path(os.environ["HERMES_KANBAN_WORKSPACE"])/"packaged-arm64-eqo-starter.so"; p.write_bytes(z.read("lib/arm64-v8a/libeqo-starter.so")); subprocess.run(["C:/Users/<user>/Android/Sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe","-p",".comment",str(p)],check=True)'
```

```
lib/arm64-v8a/libeqo-starter.so 276432
lib/armeabi-v7a/libeqo-starter.so 170020
lib/x86/libeqo-starter.so 260072
lib/x86_64/libeqo-starter.so 269744
String dump of section '.comment':
[     0] Android (13989888, +pgo, +bolt, +lto, +mlgo, based on r563880c) clang version 21.0.0 (https://android.googlesource.com/toolchain/llvm-project 5e96669f06077099aa41290cdb4c5e6fa0f59349)
[    b8] Android (13989888, based on r563880c) clang version 21.0.0 (https://android.googlesource.com/toolchain/llvm-project 5e96669f06077099aa41290cdb4c5e6fa0f59349)
[   157] Linker: LLD 21.0.0 (/mnt/disks/build-disk/src/googleplex-android/llvm-r563880-release/out/llvm-project/llvm 5e96669f06077099aa41290cdb4c5e6fa0f59349)
```

Packaged as a loadable library inside the **single EQO APK** (no separate helper app);
`packaging.jniLibs.useLegacyPackaging=true` in `:app` so the starter is extracted and
executable (same reason upstream Shizuku's manager sets it). The original r28 device
binary was 310400 bytes; the new r29 binary was not installed or device-tested.

### 2. Server starts via activation on Android 11+ with no Shizuku manager — PASS

`pm list packages | grep -i shizuku` -> exit 1 (**no Shizuku manager installed**).
Activation command and output (run A):

```
$ adb shell /data/app/~~Rps3-2D12uiyBIB5jhdfTQ==/ai.eqo.app-UysQ5vmE70Yrbi7v5QdHKw==/lib/arm64/libeqo-starter.so --apk=.../base.apk
info: starter begin
info: killing old process...
info: use apk path from argv
info: apk path is /data/app/~~Rps3-2D12uiyBIB5jhdfTQ==/ai.eqo.app-UysQ5vmE70Yrbi7v5QdHKw==/base.apk
info: starting server...
info: eqo_helper_server pid is 29623
info: eqo_starter exit with 0
activation_exit=0
$ adb shell ps -A | grep eqo_helper
shell 29623 1 4542828 118660 SyS_epoll_wait 0 S eqo_helper_server
```

### 3. EQO receives the binder; privileged test call returns the shell uid — PASS

`adb shell am instrument -w -e class ai.eqo.helper.HelperSpikeDeviceTest#activationAndShellUid
ai.eqo.app.test/androidx.test.runner.AndroidJUnitRunner` -> `OK (1 test)`, Time 24.535.
logcat: `I EqoHelperSpike: SPIKE A binder received; getUid() returned 2000 (expected 2000)`
(2000 = shell uid; the harness runs inside the real EQO app `ai.eqo.app`).

### 4. Negative test: mismatched permission string fails loudly — PASS

Fixture: `ai.eqo.helper.client.test` (`helper-client/src/androidTest/AndroidManifest.xml`)
declares the **pre-rename** `moe.shizuku.manager.permission.API_V23` — the one-sided-rename
mismatch D-004 warns about. With the server active (`eqo_helper_server pid is 1715`):

`adb shell am instrument -w -e class ai.eqo.helper.client.MismatchedPermissionTest
ai.eqo.helper.client.test/androidx.test.runner.AndroidJUnitRunner` -> `OK (1 test)`, 15.045s.
logcat: `I EqoHelperSpikeNeg: SPIKE NEG ok: no binder delivered; privileged call failed
loudly: java.lang.IllegalStateException: binder haven't been received` — no binder is
delivered to the mismatched client and the privileged call fails with a typed, loud error.

### 5. Binder death -> clean state and a re-activation prompt — PASS

`HelperSpikeDeviceTest#binderDeathAndReactivation` (host kills the server mid-run with
`adb shell kill <pid>`, then re-activates). Instrumentation -> `OK (1 test)`, 33.514s. logcat:

```
I EqoHelperSpike: SPIKE C start: waiting for ACTIVE, then host kills the helper server
I EqoHelperSpike: SPIKE C binder death -> clean state NEEDS_REACTIVATION, prompt: The EQO helper connection was lost. Re-activate the helper to continue.
I EqoHelperSpike: SPIKE C re-activation restored ACTIVE; transitions=[ACTIVE, NEEDS_REACTIVATION, ACTIVE]
```

First attempt (run C1) failed on a harness race (asserted the async state callback
synchronously: `expected:<ACTIVE> but was:<INACTIVE>`); second attempt failed on the
server-side attach bug described under FINDINGS; attempt 3 (post-fix) is the PASS above.

### 6. Server survives an EQO app restart; exits cleanly when activation is revoked — PASS

Restart: `HelperSpikeDeviceTest#survivesAppRestart` in a **fresh app process** with no
re-activation -> `OK (1 test)`, 0.027s; logcat `SPIKE B binder re-delivered to fresh app
process; getUid()=2000`; `ps -A` shows the same server pid 29623 before and after.
Revocation: `HelperSpikeDeviceTest#revocationExitsCleanly` (manager calls `exit()`) ->
`OK (1 test)`, `SPIKE D revocation: server exited and binder is dead (clean state)`, and
`adb shell ps -A | grep eqo_helper` -> empty afterwards (the `exit()` call ends with
`DeadObjectException` on the client, expected when the server exits first).

## FINDINGS (real, fixed in this branch)

1. **Upstream attach handshake is fragile without the manager**: `ShizukuService.attachApplication`
   calls `PermissionManagerApis.grantRuntimePermission(WRITE_SECURE_SETTINGS)` for the manager and
   upstream catches only `RemoteException`. With activation over `adb shell` (server uid 2000) on
   this device the call throws
   `SecurityException: grantRuntimePermission: Neither user 2000 nor current process has android.permission.GRANT_RUNTIME_PERMISSIONS`,
   which aborted `attachApplication` before `bindApplication` — the client never received its
   binder-received callbacks (`W ShizukuApplication: java.lang.SecurityException...` at
   `Shizuku.attachApplicationV13`). Fork fix: the grant is best-effort (catch `Throwable`, logged
   `grant WRITE_SECURE_SETTINGS (skipped, non-fatal)`), handshake continues. **For the security
   pass:** upstream's manager `WRITE_SECURE_SETTINGS` self-grant is kept as upstream behavior
   (only succeeds where the server runs as root); whether EQO keeps it at all is a decision left
   to the security pass.
2. `useLegacyPackaging` is required for the starter-as-library packaging (without it
   `extractNativeLibs="false"` leaves nothing executable on disk).

## Receiver export flag (TASK-007 fix card: lint error)

`helper-client/src/main/java/rikka/shizuku/ShizukuProvider.java`
`requestBinderForNonProviderProcess` registers the `ACTION_BINDER_RECEIVED`
receiver. The pre-T branch used the flagless `registerReceiver`, which
`lintDebug` rejects with `[UnspecifiedRegisterReceiverFlag]` ("receiver is
missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag"). Fixed with
`ContextCompat.registerReceiver(..., ContextCompat.RECEIVER_NOT_EXPORTED)` --
no lint suppression, no baseline.

**Why NOT_EXPORTED (decided from the code).** The fix card's note said the
broadcast is "sent by the helper server process, a different uid". The code
says otherwise: the only `sendBroadcast` in the tree is
`ShizukuProvider.handleSendBinder()` (`getContext().sendBroadcast(intent)`
with `.setPackage(getContext().getPackageName())`). `handleSendBinder()` runs
when the helper server (`eqo_helper_server`, uid shell/root) calls
`ContentProvider#call(METHOD_SEND_BINDER)` on `ai.eqo.app.helper` -- but a
ContentProvider call executes in the provider's process, i.e. inside the EQO
app (uid `ai.eqo.app`), and that is where the broadcast is sent from. The
receiver is registered by other processes of the same app
(`requestBinderForNonProviderProcess`), same uid, same package. So the
*broadcast* is same-uid (only the Binder *payload* originates cross-uid), and
`RECEIVER_NOT_EXPORTED` is both correct (same-uid delivery is exactly what it
allows) and the least-privilege choice (it keeps other apps from spoofing a
`BinderContainer` into this receiver). The API-33+ branch already used
NOT_EXPORTED before this fix; the pre-T branch is now consistent with it.

**API split (verified against the pinned `androidxCore` 1.18.0 by decompiling
`ContextCompat` from the Gradle cache, not from memory):** on API 33+ the flags
are passed through to `Context.registerReceiver`; on API 26-32 (minSdk 30, so
in practice 30-32) `ContextCompat` emulates NOT_EXPORTED by registering with
the signature-level broadcast permission
`<pkg>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
(`obtainAndCheckReceiverPermission`), which this app holds -- the merged
manifest already contains exactly that `uses-permission` line (task-014
evidence documents it as the single uses-permission).

**Flagged for the security pass:**

1. The pre-T emulation (Android 11-12) is a permission-based approximation,
   not a platform export flag. Confirm on device that the multi-process binder
   broadcast still delivers after this change (the spike's device runs
   exercised the provider-call path; the `requestBinderForNonProviderProcess`
   broadcast path was not separately device-verified). No phone was touched in
   this fix run, per the card.
2. If any future change sends `ACTION_BINDER_RECEIVED` from the helper server
   process itself (a different uid), NOT_EXPORTED will silently drop it -- the
   flag must then become EXPORTED, ideally tightened with a signature-level
   permission on both send and receive.
3. `onReceive` accepts any non-null `BinderContainer.binder` and passes it to
   `Shizuku.onBinderReceived`; binder authenticity rests entirely on the export
   flag/permission above. Worth an explicit trust check in the security pass.

## Helper-server lint findings (TASK-007 fix card: full-gate follow-up)

The first full-gate run reached `:helper-server:lintDebug` (the lead's earlier
fresh-clone run had stopped at `:helper-client`) and found 10 errors in forked
code, fixed here:

- 9x `[ObsoleteSdkInt]` -- SDK branches below `minSdk 30` that were dead code
  in `BinderSender` (pre-N `@RequiresApi`, the `>= 26` / `>= O_MR1` guards
  around `registerUidObserver`), `IContentProviderCompat` and
  `IContentProviderUtils` (pre-30 `IContentProvider.call` overloads),
  `ShizukuConfigManager` (pre-Q `scheduleWriteLocked` branch) and `UserService`
  (pre-N `UserHandle(int)` constructor). Removed/simplified; behavior is
  identical on API 30+.
- 1x `[DiscouragedPrivateApi]` -- `UserService.create()` reflects
  `ActivityThread.mInitialApplication`. This is deliberate hidden-API access
  (driving hidden framework APIs is the fork's entire purpose; upstream got
  the same calls through `dev.rikka.tools.refine` stubs, the fork reflects).
  There is no public or `dev.rikka.hidden:compat` equivalent (checked the
  4.4.0 artifact: ActivityManagerApis/AppOpsApis/... but no ActivityThread
  API), so the fix is a ONE-SITE `@SuppressLint("DiscouragedPrivateApi")` on
  that method with the reasoning in the code comment. No lint baseline, no
  rule-level suppression anywhere. **Flagged for the security pass:** this
  suppression grants that one method blanket cover for reflective access;
  if the security pass wants it narrower, split the reflection into a helper
  method and annotate only that.

## Gates re-run at handoff

Fix 2 re-ran the complete gate after the clean r29 starter build, one Gradle
invocation at a time. The first invocation outlasted the terminal tool wait,
continued running (wrapper PID 40136), and completed successfully; no concurrent
Gradle was launched. The subsequent incremental confirmation returned exit 0.
Earlier fix-card lint/detekt findings remain described above; no additional
suppressions or baselines were added in fix 2.

From `android/`:

`./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m`

First full run, real tail:

```
> Task :platform-a11y:lintAnalyzeDebug
> Task :app:detekt
> Task :platform-a11y:lintReportDebug
> Task :core-agent:detekt
> Task :platform-a11y:lintDebug
> Task :core-llm:detekt
> Task :core-security:detekt
> Task :helper-client:detekt
> Task :helper-server:detekt
> Task :platform-a11y:detekt

[Incubating] Problems report is available at: file:///C:/Users/<user>/Claude/worktrees/task-007/android/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.7.0/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 1h 25m 12s
674 actionable tasks: 579 executed, 95 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.7.0/userguide/configuration_cache_enabling.html
```

Same full command, incremental confirmation, real tail (exit 0 captured):

```
> Task :core-agent:detekt UP-TO-DATE
> Task :core-llm:detekt UP-TO-DATE
> Task :core-security:detekt UP-TO-DATE
> Task :helper-client:detekt UP-TO-DATE
> Task :helper-server:detekt UP-TO-DATE
> Task :platform-a11y:detekt UP-TO-DATE

[Incubating] Problems report is available at: file:///C:/Users/<user>/Claude/worktrees/task-007/android/build/reports/problems/problems-report.html

Deprecated Gradle features were used in this build, making it incompatible with Gradle 10.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/9.7.0/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD SUCCESSFUL in 43s
674 actionable tasks: 24 executed, 650 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.7.0/userguide/configuration_cache_enabling.html

GATE_EXIT=0
```

JUnit XML totals across 56 suites: **377 tests, 0 failures, 0 errors, 1 skipped**
(the existing skipped test remains). `HelperRenameLockstepTest`: **3 tests, 0
failures, 0 errors, 0 skipped**. The three test cases are
`mainSourcesAndManifestsHaveNoOldManagerPermissions`,
`serverClientAndManifestRenameStringsAgree`, and `allowlistAcceptsOnlyExactEqoAppId`.
No Kotlin file was added; the new test is Java, so no new provenance row is needed.

The scripts live at the repository root, not under `android/`; both were run
from the repository root using these exact commands (equivalently, from
`android/` use `bash ../scripts/check-branding.sh` and `bash ../scripts/check.sh`):

`bash scripts/check-branding.sh` -> exit 0, real tail:

```
== Branding gate: provenance map covers exactly the tracked Kotlin files ==
kt files: 208; provenance rows: 208
OK: provenance map covers all Kotlin files and every row path exists in git ls-files
BRANDING GATE PASSED
```

`bash scripts/check.sh` -> exit 0, real tail:

```
== Branding gate: provenance map covers exactly the tracked Kotlin files ==
kt files: 208; provenance rows: 208
OK: provenance map covers all Kotlin files and every row path exists in git ls-files
BRANDING GATE PASSED
OK
```

## Honest limits

- `HelperRenameLockstepTest` adds three pure JVM tests: scan main sources/manifests
  for old manager permission strings (intentionally exclude `androidTest`'s negative
  fixture), compare renamed server/client constants and manifest declarations, and
  test exact manager app-id allowlisting including null/empty/near-miss ids. The test
  is Java, so the Kotlin provenance map remains unchanged (208 files / 208 rows).
- The r29 starter is host-built and compiler-verified only; the prior device evidence
  used r28 and was not repeated on the phone in this fix.
- The rish remote shell and adb pairing are not forked (documented cuts above); user services are
  forked but only compiled, not device-exercised.
- Only one device (Android 11, Realme RMX2193) was exercised; per spec, do not generalize.
- All enable/install actions were announced in the task thread; accessibility settings and the
  owner's apps were not touched.

## Device re-run on NDK 29

2026-10-03, task `t_31e92482`; code HEAD `836927db1d308db443f32e7cb48e2bd19ba6a6cf`.
Same Realme Android 11 device, serial `<DEVICE_SERIAL>`. This run supersedes
only the r29 device-coverage limitation above; historical r28 records are retained.
No code changes. All five instrumentation runs passed on the first attempt.

Clean build from `android/` (removed the entire native cache first, including
inactive r28 caches):

```bash
python -c 'import shutil; from pathlib import Path; p=Path("helper-server/.cxx"); print("Removing native cache",p.resolve()); shutil.rmtree(p,ignore_errors=True)'
./gradlew clean :app:assembleDebug :app:assembleDebugAndroidTest :helper-client:assembleDebugAndroidTest --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m
```

```text
BUILD SUCCESSFUL in 4m 48s
154 actionable tasks: 154 executed
```

Extracted `lib/arm64-v8a/libeqo-starter.so` from the new `app-debug.apk` with
Python `zipfile`; inspected with the real command:

```text
C:/Users/<user>/Android/Sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe -p .comment C:/Users/<user>/AppData/Local/hermes/kanban/boards/eqo-android/workspaces/t_31e92482/r29-starter.so
String dump of section '.comment':
[     0] Android (13989888, +pgo, +bolt, +lto, +mlgo, based on r563880c) clang version 21.0.0 (https://android.googlesource.com/toolchain/llvm-project 5e96669f06077099aa41290cdb4c5e6fa0f59349)
[    b8] Android (13989888, based on r563880c) clang version 21.0.0 (https://android.googlesource.com/toolchain/llvm-project 5e96669f06077099aa41290cdb4c5e6fa0f59349)
[   157] Linker: LLD 21.0.0 (/mnt/disks/build-disk/src/googleplex-android/llvm-r563880-release/out/llvm-project/llvm 5e96669f06077099aa41290cdb4c5e6fa0f59349)
```

Read all four newly generated `helper-server/.cxx/Debug/1p0392v6/<ABI>/CMakeCache.txt`
files: each reported the following, including arm64-v8a:

```text
ANDROID_NDK:UNINITIALIZED=C:\Users\<user>\Android\Sdk\ndk\29.0.14206865
CMAKE_ANDROID_NDK:UNINITIALIZED=C:\Users\<user>\Android\Sdk\ndk\29.0.14206865
```

Every `adb` below denotes exactly
`C:/Users/<user>/Android/Sdk/platform-tools/adb.exe -s <DEVICE_SERIAL>`.
All three installs were announced in the task thread before execution:

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb install -r helper-client/build/outputs/apk/androidTest/debug/helper-client-debug-androidTest.apk
```

Each returned `Performing Streamed Install` / `Success`. Read-back via
`pm path ai.eqo.app` and `pm list instrumentation` confirmed the installed app
and both expected runners. Installed native binary check:

```text
adb shell sha256sum /data/app/~~AiPCe1jZGq-ppMhu5Vtmrg==/ai.eqo.app-aUUalJQeSwngaM6L7Pz1hw==/lib/arm64/libeqo-starter.so
448fbdfe760cbc8213d1c8e6a4778ebe56e4606e42407e743e8a54653612df3e  /data/app/~~AiPCe1jZGq-ppMhu5Vtmrg==/ai.eqo.app-aUUalJQeSwngaM6L7Pz1hw==/lib/arm64/libeqo-starter.so
APK arm64 starter SHA-256 (Python hashlib):
448fbdfe760cbc8213d1c8e6a4778ebe56e4606e42407e743e8a54653612df3e
adb shell 'pm list packages | grep -i shizuku'
(empty; exit 1)
adb shell getprop ro.build.version.release
11
```

Exact activation command, used twice (initial activation after SPIKE A start,
then again after SPIKE C logged NEEDS_REACTIVATION):

```text
adb shell /data/app/~~AiPCe1jZGq-ppMhu5Vtmrg==/ai.eqo.app-aUUalJQeSwngaM6L7Pz1hw==/lib/arm64/libeqo-starter.so --apk=/data/app/~~AiPCe1jZGq-ppMhu5Vtmrg==/ai.eqo.app-aUUalJQeSwngaM6L7Pz1hw==/base.apk
info: starter begin
info: killing old process...
info: use apk path from argv
info: apk path is /data/app/~~AiPCe1jZGq-ppMhu5Vtmrg==/ai.eqo.app-aUUalJQeSwngaM6L7Pz1hw==/base.apk
info: starting server...
info: eqo_helper_server pid is 23719
info: eqo_starter exit with 0
```

Both activation commands exited 0; second activation reported PID `24049`.
Test commands and actual result excerpts, in execution order:

```text
adb shell am instrument -w -e class ai.eqo.helper.HelperSpikeDeviceTest#activationAndShellUid ai.eqo.app.test/androidx.test.runner.AndroidJUnitRunner
Time: 1.526
OK (1 test)
I/EqoHelperSpike(23686): SPIKE A binder received; getUid() returned 2000 (expected 2000)

adb shell am instrument -w -e class ai.eqo.helper.HelperSpikeDeviceTest#survivesAppRestart ai.eqo.app.test/androidx.test.runner.AndroidJUnitRunner
Time: 0.022
OK (1 test)
I/EqoHelperSpike(23881): SPIKE B binder re-delivered to fresh app process; getUid()=2000

adb shell am instrument -w -e class ai.eqo.helper.client.MismatchedPermissionTest ai.eqo.helper.client.test/androidx.test.runner.AndroidJUnitRunner
Time: 15.052
OK (1 test)
I/EqoHelperSpikeNeg(23929): SPIKE NEG ok: no binder delivered; privileged call failed loudly: java.lang.IllegalStateException: binder haven't been received

adb shell am instrument -w -e class ai.eqo.helper.HelperSpikeDeviceTest#binderDeathAndReactivation ai.eqo.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell kill 23719
(exit 0; executed 3s after SPIKE C start, PID obtained via ps -A | grep eqo_helper)
I/EqoHelperSpike(24011): SPIKE C binder death -> clean state NEEDS_REACTIVATION, prompt: The EQO helper connection was lost. Re-activate the helper to continue.
(host re-activated with the command above)
I/EqoHelperSpike(24011): SPIKE C re-activation restored ACTIVE; transitions=[ACTIVE, NEEDS_REACTIVATION, ACTIVE]
Time: 5.691
OK (1 test)

adb shell am instrument -w -e class ai.eqo.helper.HelperSpikeDeviceTest#revocationExitsCleanly ai.eqo.app.test/androidx.test.runner.AndroidJUnitRunner
I/EqoHelperSpike(24110): SPIKE D exit() call ended with android.os.DeadObjectException (expected if the server exited first)
I/EqoHelperSpike(24110): SPIKE D revocation: server exited and binder is dead (clean state)
Time: 0.079
OK (1 test)
```

All instrumentation adb commands exited 0. Log excerpts were captured using
`adb logcat -T 1 -v brief -s EqoHelperSpike:I EqoHelperSpikeNeg:I '*:S'`.
Separate instrumentation processes `23686` and `23881` prove the fresh app
process; `adb shell 'ps -A | grep eqo_helper'` returned identical server output
before and after restart:

```text
shell         23719      1 4542956 118580 SyS_epoll_wait      0 S eqo_helper_server
```

Final cleanup inspection, repeated twice after revocation:

```text
adb shell 'ps -A | grep eqo_helper'
(empty; exit 1)
Final helper PIDs=[]
```

No leftover helper required an additional kill. No adb-server kill, secure
settings write, accessibility setting change, or action on the owner's other
apps was performed. One initial host proof-command invocation returned
`stdin is not a tty` before producing proof; the same inspection was retried
successfully before any install (no device-test failures). **PHONE RELEASED**.
