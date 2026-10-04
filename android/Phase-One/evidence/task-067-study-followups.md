# TASK-067 study-build follow-ups (Refs #20)

Base: `origin/main` at `643b017`. Branch: `agent/android/67-followups`.
No phone, emulator, installation or instrumentation execution was used.

## Parked model-download worker (D-009)

The donor worker was not a `@HiltWorker`: its plain constructor eagerly called
`EntryPointAccessors.fromApplication` for DAO, HTTP, credentials and notification
bindings. The registered study `EqoApplication` is deliberately plain. A new
Robolectric test using the default WorkManager reflection factory reproduced the
constructor failure with `android.app.Application` before the fix:
`Given component holder class android.app.Application does not implement ... GeneratedComponent`.

The worker class name and standard `(Context, WorkerParameters)` constructor are
retained for any persisted legacy requests. All Hilt entry points and download
side effects have been removed; `doWork()` unconditionally returns failure. This
is intentionally inert, not an alternate implementation of local-model support.
There are no study-app worker, request-builder or WorkManager call sites.
AndroidX Startup's default WorkManager initializer remains enabled: disabling
unrelated library initialization is unnecessary when this worker is harmless.
The legacy internal request builder keeps its tested request shape, but does not
enqueue anything. Restoring downloads requires a separate decision and explicit
runtime wiring; the donor implementation remains recoverable from Git history.

`StudyWorkerWiringTest` scans all modules' production worker sources for
`@HiltWorker`, `@AssistedInject`, application entry points and a Hilt worker
factory while asserting the study Application is plain. A second guard rejects
model-download scheduling references in the study app. The Robolectric test
constructs the worker through WorkManager's default factory under a plain
Application and checks its failure result.

`ModelDownloadSchedulingTest` retains its one honest pre-Q skip: that behavior
is unreachable under minSdk 30. The outdated foreground-service assumption was
removed; missing data-sync service metadata or permission now fails the test
instead of being silently skipped. These are legacy request/notification tests,
not evidence of an enabled model-download feature.

## Accessibility androidTest namespace

The baseline command `:platform-a11y:compileDebugAndroidTestKotlin` failed with
eight unresolved `R` references in `EqoTestTargetActivity`. Its Kotlin package
remains `ai.eqo.test`; the library namespace is `ai.eqo.platform`, so test-only
`res/values/ids.xml` belongs to generated `ai.eqo.platform.test.R`. Added that
explicit import; no production R alias, duplicated IDs or namespace rollback.
The same compile command passed after the fix.

## Verification

Targeted worker, scheduling and study-wiring tests passed; the new worker test
was red before the implementation change and green afterward.

Toolchain: Temurin JDK 21.0.12.1; Gradle 9.7.0; AGP 9.3.1; Kotlin 2.4.0.

Final full gate from `android/` (one own Gradle invocation at a time):

```sh
./gradlew assembleDebug assembleRelease assembleDebugAndroidTest testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m
../scripts/check-branding.sh
../scripts/check.sh
```

All passed. Gradle reported `BUILD SUCCESSFUL in 9m 34s` on the final retry,
with 1,187 actionable tasks (40 executed, 1,147 up-to-date). The first full run
had completed assemblies/tests/lint before failing on four new-test detekt
`MaxLineLength` findings; those were fixed by wrapping lines, with no suppression
or baseline additions. The identical full gate then passed incrementally.

JUnit XML aggregated programmatically across 102 suites: 622 tests, one skipped,
zero failures and zero errors. The only skip is
`ModelDownloadSchedulingTest.foreground info omits service type before Android Q`.
The three new guard/regression tests have no skips.

Required instrumentation APKs were assembled and verified to exist:

- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- `platform-a11y/build/outputs/apk/androidTest/debug/platform-a11y-debug-androidTest.apk`
- `helper-client/build/outputs/apk/androidTest/debug/helper-client-debug-androidTest.apk`

Branding passed with 319 tracked Kotlin files and exactly 319 provenance rows;
`check.sh` also passed its secret scan and shell syntax checks. Only existing
compiler/deprecation warnings remain. No device test results are claimed.
