# EQO Android toolchain (TASK-001, issue #6)

Status: **provisional**. Installed and verified on the owner's Windows PC on 2026-10-01. Versions marked
"provisional" must be checked against the pinned OpenDroid build files in TASK-002 before they are treated as final.
Raw command output: `android/Phase-One/evidence/toolchain.txt`.

| Component | Version | Source | Pin status |
|---|---|---|---|
| JDK | Temurin 21.0.12.1+1 | `winget install EclipseAdoptium.Temurin.21.JDK` | per TASK-001 scope (JDK 21) |
| Android command-line tools | 23.0.0 | dl.google.com `commandlinetools-win-16111833_latest.zip`, SHA-1 `57d04f2d75eb8e8fffc5000a987e5de4b5a63e9d` verified | latest at install time |
| platform-tools (adb) | 37.0.1 (adb 1.0.41) | sdkmanager | latest at install time |
| Platform | android-36 (2.0.0) | sdkmanager | per TASK-001 scope |
| Build-tools | 36.0.0 | sdkmanager | **provisional**, check against upstream `build.gradle` |
| NDK | 29.0.14206865 | sdkmanager (stable NDK 29) | per TASK-001 scope, check exact version upstream |
| CMake | 3.31.6 | sdkmanager | **provisional**, scope says 3.31+ |
| Gradle | project wrapper | n/a | not yet verified, needs the OpenDroid checkout (TASK-002) |

## Environment variables (user level)

- `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`
- `ANDROID_HOME=C:\Users\<user>\Android\Sdk`
- `adb` and `sdkmanager` are not on `PATH`; call them by full path or add `%ANDROID_HOME%\platform-tools` and
  `%ANDROID_HOME%\cmdline-tools\latest\bin`.

## Install notes

- The current command-line tools use slash-style package IDs (`platforms/android-36`, `ndk/29.0.14206865`), not
  the older semicolon style. `sdkmanager` prints a deprecation notice pointing at the newer `android` CLI.
- `sdkmanager --licenses` is a no-op with this version ("no longer needed"). Licenses are prompted during package
  install. Pipe `y` answers to accept them in a non-interactive shell.
- **License acceptance** was done on the owner's explicit instruction in chat on 2026-10-01; the resulting
  `licenses/android-sdk-license` file exists in the SDK root.
- winget's MSI install needs the Windows admin (UAC) prompt approved; a dismissed prompt shows as exit code 1602.

## Reproduce

```
winget install --id EclipseAdoptium.Temurin.21.JDK -e
# download and verify the command-line tools zip, extract to %ANDROID_HOME%\cmdline-tools\latest
sdkmanager --sdk_root=%ANDROID_HOME% platform-tools platforms/android-36 build-tools/36.0.0 ndk/29.0.14206865 cmake/3.31.6
```

## Not yet done (TASK-001 acceptance criteria still open)

- A throwaway Gradle build of the pinned OpenDroid commit reaching the compile step. No build result is claimed.
- Reconciling the provisional pins with the upstream build files.
