# TASK-063: setup hub UI regressions (Refs #20)

## Base and scope

Branch: `agent/android/63-setup-hub-ui`, worktree `C:/Users/<user>/Claude/worktrees/task-063`.
PR #61 was verified merged before branching. Base: `origin/main` at
`4817126ef6b490f919ddd10b8cc59eaa535f98f2`.
No phone was used; no PR was opened. No capability probes, transport gates,
permission checks, helper lifecycle, or task/approval safety wiring changed.

## Changes

- Resolve `SetupHubActivity.stateLabel(state)` through `getString` before appending
  it to each row; annotate the resource-id-returning helper with `@StringRes`.
- After an explicit re-check finishes rendering all probes, show a short Toast:
  "Re-check complete. Results updated below." Row results remain visible.
  This confirms completion, not readiness of all capabilities.
- Enable Android resources for app JVM tests. Add two Robolectric regressions
  under the production application on SDK 34:
  - Reject bare ten-digit resource-id-looking numbers in every row, and verify
    all six states across all five rows with preserved details and repair guidance.
  - Click re-check twice, confirm feedback each time, change model-key probe state
    to verify refreshed output, and verify unrelated rows remain intact.
- Add the test to the exact-set provenance map required by the branding gate.

## Resource rendering audit

Searched all `app/src/main/kotlin` for append calls, label helpers, string resource
references, and text setters; inspected the onboarding activities and TaskActivity.
The hub's stateLabel append was the only unresolved resource-id append found.
TaskActivity resolves every step state with getString; WirelessAdbSetupActivity
resolves its integer label before interpolation; ModelKeySetupActivity uses the
resource-aware TextView.setText(Int) overload; accessibility and Chrome consent
screens also resolve resources or use resource-aware setters. No other fixes needed.

## Executed verification

Both new regressions failed on the original activity: a raw numeric row state and
missing Toast feedback. After the fix both pass. The first full gate found two
Detekt findings in the new test (nesting and line length); extracted the state
assertion helper and wrapped the line, then reran the complete gate successfully.

From `android/`, one Gradle invocation at a time:

```sh
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m
```

Final result: BUILD SUCCESSFUL in 2m 51s; 901 actionable tasks,
40 executed and 861 up-to-date. Both APK variants assembled.
JUnit XML aggregation: 612 tests, zero failures, zero errors, one skipped.
The skipped case is the existing core-llm ModelDownloadSchedulingTest case
"foreground info omits service type before Android Q".

From the repository root:

```sh
scripts/check-branding.sh
scripts/check.sh
```

Both exit 0. `git diff --check` passes.

Toolchain: Temurin JDK 21.0.12.1, Gradle 9.7.0, AGP 9.3.1,
Kotlin 2.4.0 (version catalog and Gradle runtime verified).

## Integration hotspot

`android/Phase-One/evidence/task-005-provenance-map.md` is shared by other Android
cards. The change is a single additive TASK-063 table row; preserve sibling rows
when integrating.
