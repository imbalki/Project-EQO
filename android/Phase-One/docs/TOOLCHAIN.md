# EQO Android toolchain (TASK-001, issue #6)

Status: **verified against the pinned OpenDroid build files** on 2026-10-01. The upstream pins below were read
(grep with line numbers) from the OpenDroid repo at commit
`6ff5a061755b597b0558fed1f565587837ed4d51` ("feat: AI Social Media Management System, ... v1.0.7"), confirmed
with `git rev-parse HEAD`. Raw toolchain command output: `android/Phase-One/evidence/toolchain.txt`.

## Upstream pins, verified in the OpenDroid build files (commit 6ff5a061755b...)

| Pin | Exact value | Verified source in OpenDroid (project root) |
|---|---|---|
| Gradle (wrapper) | **9.7.0** | `gradle/wrapper/gradle-wrapper.properties:4` — `distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.0-bin.zip`; line 3 pins the distribution SHA-256 `84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae` |
| AGP | **9.3.1** | `build.gradle:13` — `classpath 'com.android.tools.build:gradle:9.3.1'` (comment at lines 8-9: requires Gradle >= 9.5.0; wrapper pinned to 9.7.0 with checksum) |
| Kotlin | **2.4.0** | `build.gradle:18` — `classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0'` (comment lines 15-17: AGP 9 built-in Kotlin compiles with whatever KGP is on the classpath; AGP 9.3.1 pulls 2.2.10 transitively, this pin keeps 2.4.0). Also `kotlin-serialization:2.4.0` (:23) and `compose-compiler-gradle-plugin:2.4.0` (:24) |
| KSP | **2.3.10** | `build.gradle:25` — `classpath 'com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.10'` |
| Dagger / Hilt | **2.60.1** | `build.gradle:22` — `classpath 'com.google.dagger:hilt-android-gradle-plugin:2.60.1'` (comment lines 19-21: Hilt 2.58's plugin fails under AGP 9; 2.60.1 uses the current APIs) |
| JDK | **21, Temurin in CI** | `app/build.gradle:99-100` `sourceCompatibility/targetCompatibility JavaVersion.VERSION_21`; `app/build.gradle:179` `jvmToolchain(21)`; `app/build.gradle:181` `jvmTarget = ...JvmTarget.JVM_21`; `gradle/gradle-daemon-jvm.properties:13` `toolchainVersion=21` (the Gradle daemon is pinned to Java 21 — a JDK 21 must be installed, Gradle will not download one; README.md:197-199 "JDK 21 — not newer"); `.github/workflows/android-ci.yml:39-40,72-73` `distribution: temurin` / `java-version: '21'` |
| compileSdk | **36** | `app/build.gradle:12` — `compileSdk 36` (minSdk 26 at line 16, targetSdk 36 at line 17) |
| buildToolsVersion | **not pinned upstream** | no `buildToolsVersion`, `ndkVersion`, `externalNativeBuild`, or `cmake` in any `.gradle` file (`grep -rnicE "ndkVersion\|externalNativeBuild\|cmake\|buildTools" app/build.gradle build.gradle settings.gradle` → `0` for all three); no version catalog (zero `.toml` files in the repo) |
| ndkVersion / cmake | **not pinned upstream** | same grep result — the upstream project has no native JNI code at commit 6ff5a061 |

Consequences for the local machine:

- The wrapper never runs Gradle older or newer than **9.7.0** (distributionUrl, checksum-enforced on first
  `gradlew` run), and its daemon is pinned to **Java 21** by `gradle/gradle-daemon-jvm.properties` — so
  `JAVA_HOME` must point at a JDK 21, which it does.
- Build-tools, NDK and CMake are **not declared** by the upstream build. AGP 9.3.1 supplies its own defaults
  for build-tools and only needs the NDK/CMake packages if a module declares native builds; OpenDroid's
  modules do not. The installed versions (Build-tools 36.0.0, CMake 3.31.6, NDK 29.0.14206865) are therefore
  compatible defaults kept for this machine's toolchain completeness, not values the upstream build could
  disagree with.

## Comparison against the provisional pins of 2026-10-01

| Component | Provisional (this doc, earlier state) | Verified upstream | Verdict |
|---|---|---|---|
| JDK | Temurin 21.0.12.1+1 | JDK 21, Temurin in CI, daemon-jvm pinned to 21 | **Right.** The installed Temurin 21 update satisfies `toolchainVersion=21`; per README.md:197 the exact update number does not matter as long as it is JDK 21. |
| Platform | android-36 (2.0.0) | compileSdk 36 | **Right.** |
| Build-tools | 36.0.0 (provisional) | no pin in upstream | **Nothing to reconcile** — upstream pins no buildToolsVersion; AGP 9.3.1 decides. Kept as installed fact. |
| NDK | 29.0.14206865 | no pin in upstream | **Nothing to reconcile** — kept per TASK-001 scope; OpenDroid has no native builds at this commit. |
| CMake | 3.31.6 (provisional) | no pin in upstream | **Nothing to reconcile** — 3.31.6 satisfies the TASK-001 scope (3.31+). |
| Gradle | "project wrapper, not yet verified" | 9.7.0 via wrapper (SHA-256-pinned distribution) | **Now verified**, see table above. |
| AGP / Kotlin / KSP / Hilt | (not listed before) | AGP 9.3.1, Kotlin 2.4.0, KSP 2.3.10, Hilt 2.60.1 | **Added** — these were verified from `build.gradle` lines 13-25. |

No upstream pin differs from what is installed or documented. Nothing was changed on the machine during this
verification; the upstream tree was cloned read-only (`git clone --no-checkout` + `git checkout 6ff5a061`) into
`C:\Users\<user>\Claude\worktrees\_upstream\opendroid` (outside the project repo) and only read from.

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
- First Gradle invocation on OpenDroid downloads `gradle-9.7.0-bin.zip`; the wrapper verifies the SHA-256 pinned
  at `gradle-wrapper.properties:3` before unpacking. The Gradle pin is the wrapper's own `distributionUrl`/checksum,
  not a machine-level Gradle install.

## Reproduce

```
winget install --id EclipseAdoptium.Temurin.21.JDK -e
# download and verify the command-line tools zip, extract to %ANDROID_HOME%\cmdline-tools\latest
sdkmanager --sdk_root=%ANDROID_HOME% platform-tools platforms/android-36 build-tools/36.0.0 ndk/29.0.14206865 cmake/3.31.6
# OpenDroid checkout at 6ff5a061755b597b0558fed1f565587837ed4d51 — the project wrapper then supplies Gradle 9.7.0
```

## Not yet done (TASK-001 acceptance criteria still open)

- A throwaway Gradle build of the pinned OpenDroid commit (6ff5a061) reaching the compile step. No build has
  been run and no build result is claimed; this criterion stays OPEN until the owner approves running the build.

The provisional-versus-upstream pin reconciliation is done — see the two verification tables above.
