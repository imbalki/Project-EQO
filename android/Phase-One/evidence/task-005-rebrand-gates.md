# TASK-005 evidence: rebrand gate re-run on rebased branch (`agent/android/10-rebrand`)

 Invocation env: Android Studio available (terminal Gradle, JDK21). From `C:\Users\<user>\Claude\worktrees\task-005\android`.

 ## Rebase
 - Merged main first: 17 commits ahead of `a567321` (`feat(android): extract OpenDroid base into EQO modules (TASK-004) (#41)` at origin/main), clean rebase, no conflicts.
 - Working tree clean after rebase: `git status` → nothing to commit, working tree clean.
 - Note: after rebase, `git status -sb` still reported branch tracking `origin/agent/android/36-extraction` (stale upstream tracking info, did not affect build).

 ## Full Gradle run present BEFORE the handoff
 - Command: `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt`
 - Result (from the pre-handoff worktree at `C:\Users\<user>\Claude\worktrees\task-005\android\build\gate_build.log`):

 ```
 BUILD SUCCESSFUL in 30s
 459 actionable tasks: 6 executed, 453 up-to-date
 GATE_EXIT=0
 ```

 Only the `lintDebug` tasks actually needed execution; the 453 up-to-date Gradle output-checked tasks matched freshly re-run source content (against Gradle's incremental build database), including `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `ktlintCheck`, `detekt`.

 Combined log: `C:\Users\<user>\Claude\worktrees\task-005\android\build\gate_build.log` (658 task lines, >21 000 lines).

 To make the test XML re-confirmable non-incrementally, a simulated "test-clean + test-only" rerun was also done:

 - `cleanTestDebugUnitTest cleanTest` + 5 module `testDebugUnitTest` targets → `TEST_EXIT=0`, 10 executed / 125 up-to-date.
 - Test result XMLs under `android/<module>/build/test-results/testDebugUnitTest/TEST-*.xml` were regenerated 2026-10-02T23:29-23:30 local.

 ## Test counts (rolled from the test XMLs by script)
 occupy 32 files, rolled with `xml.etree` per module:

 | module | tests | failures | errors | skipped |
 | --- | ---: | ---: | ---: | ---: |
 | app | 4 | 0 | 0 | 0 |
 | core-agent | 17 | 0 | 0 | 0 |
 | core-llm | 229 | 0 | 0 | 1 |
 | core-security | 37 | 0 | 0 | 1 |
 | platform-a11y | 3 | 0 | 0 | 0 |
 | total | 290 | 0 | 0 | 2 |

 ```
 FILES=32 TOTAL_TESTS=290 FAILURES=0 ERRORS=0 SKIPPED=2
 ```
 The 2 skipped tests are the pre-existing, intentional Assume-gated tests (`WhisperModelRegistry ArrivalTest` class `Assume` guards) and are unchanged from the TASK-004 acceptance evidence. 288 tests ran on all 5 modules with 0 failures.

 No profiler with `gradlew --profile`, but no T_jVM was silently spawned beyond the 3 daemons already accounted for.

 ## Branding gate (`scripts/check-branding.sh`)

 ```
 0 hits for: branding final check at C:\Users\<user>\Claude\worktrees\task-005\bash .\scripts\check-branding.sh
 == Branding gate: user-visible code in android/app + core modules ==
 OK: user-visible code carries no upstream product names
 == Branding gate: multi-line prompt bodies ==
 OK: prompt/notification bodies clean
 == Branding gate: resources, manifest, gradle, docs the user can see ==
 OK: resources/manifests clean
 == Branding gate: excluded Leap SDK (ADR-0003/D-007) ==
 OK: no Leap SDK references
 == Branding gate: provenance map covers every Kotlin file ==
 kt files: 159; provenance rows: 159
 OK: provenance map covers all Kotlin files
 BRANDING GATE PASSED
 BRANDING_EXIT=0
 ```

 Re-run full stdout also kept at `C:\Users\<user>\AppData\Local\hermes\kanban\boards\eqo-android\workspaces\t_d2666af4\branding_out.txt`.

 ## Upstream-name grep (direct grep, independent of the branding gate)

 ```
 LEAP_HITS_SRC=0   # grep -rIn -i "liquid|leap-sdk|ai\.liquid" over android/{app,core-agent,core-llm,core-security,platform-a11y}/src + android/scripts
 ```

 Excluding only `build/`, `.kotlin/`, `.git/`. No `.gradle.kts` or src hits.

 ## What is NOT verified
 - No emulator/instrumented tests were run (no device attached). The api-level compatibility of the `ai.eqo` applicationId on API 30+ is enforced by lint, not by a device install.
 - No screenshot of the launcher/ "Open-source notices" was taken. This is a UI surface instruction, not a script phase; it has no acceptance gate to command against.
 - Byte-level identity between rebased commits and pre-rebase commits was not independently re-verified when the rebase replayed (the pre-handoff run already checked the 102 TASK-004 moved files, this rebase was a pure replay of the 5 task-005 commits, same tree identity).
 - Manifest AARs and applicationId slices were vetted by branding search, not by manually reading all 700+ lines of repo manifests.

 ## Review round 1 (fixes for review findings R1-R4 + MINOR)

 Invocation env: same worktree `C:\Users\<user>\Claude\worktrees\task-005`, JDK21 terminal Gradle,
 `--max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m` (one invocation at a time).

 ### Full Gradle gate

 - Command: `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m --console=plain`
 - First attempt failed on ktlint (`:core-security:ktlintTestSourceSetCheck`, 4 style errors in the
   new test: ktlint forces single-parameter function signatures onto one line); fixed with a
   nested-type import, re-run:
 - Result: `BUILD SUCCESSFUL in 1m 23s`, `459 actionable tasks: 25 executed, 434 up-to-date`,
   `FULL GATE EXIT=0`. All five modules ran `lintDebug`, `ktlintCheck`, `detekt`,
   `testDebugUnitTest`, `assembleDebug`, `assembleRelease` (executed or up-to-date against
   current sources; `:core-security:testDebugUnitTest` re-executed for the new test).

 ### Test counts (rolled from the test-results XMLs by script, `xml.etree` per module)

 XMLs regenerated by this round's runs 2026-10-03 01:21-01:36 local (app 01:21, core-agent 01:27,
 core-llm 01:29, platform-a11y 01:30, core-security 01:36), 33 files:

 | module | tests | failures | errors | skipped |
 | --- | ---: | ---: | ---: | ---: |
 | app | 4 | 0 | 0 | 0 |
 | core-agent | 17 | 0 | 0 | 0 |
 | core-llm | 229 | 0 | 0 | 1 |
 | core-security | 38 | 0 | 0 | 1 |
 | platform-a11y | 3 | 0 | 0 | 0 |
 | total | 291 | 0 | 0 | 2 |

 ```
 FILES=33 TOTAL_TESTS=291 FAILURES=0 ERRORS=0 SKIPPED=2
 ```
 `core-security` grew 37 -> 38: the new `LegacyPlaintextPreferencesSourceTest`
 (1 test, R1). `app` stays 4: `legalDeepLinkIntentTargetsEqoApplicationId` was kept as a real
 assertion (MINOR), not dropped. The 2 skipped are the pre-existing Assume-gated tests.

 ### Branding gate probes (scratch clone, never the worktree)

 Scratch clone: `git clone` of the worktree into
 `C:\Users\<user>\AppData\Local\hermes\profiles\eqo-core-dev\cache\scratch\t005-gate-test`,
 review-round-1 diff applied with `git apply --index` (so `git ls-files` sees the new test file).

 ```
 clean tree:                      bash scripts/check-branding.sh   -> exit 0  BRANDING GATE PASSED
 P1 lower-case Kotlin string:     printf 'val probeBrandString = "powered by opendroid"\n' >> \
    android/core-llm/src/main/java/ai/eqo/core/util/UrlUtils.kt
                                  bash scripts/check-branding.sh   -> exit 1  (code-strings section)
 P2 OPENDROID in prompt body:     printf 'private val probePrompt = """\n    OPENDROID\n"""\n' >> \
    android/core-llm/src/main/java/ai/eqo/core/llm/prompts/SystemPrompts.kt
                                  bash scripts/check-branding.sh   -> exit 1  (code-strings + prompt-body sections)
 P2b comment-looking prompt line: printf 'private val probePrompt2 = """\n * powered by OPENDROID\n"""\n' >> \
    android/core-llm/src/main/java/ai/eqo/core/llm/prompts/SystemPrompts.kt
                                  bash scripts/check-branding.sh   -> exit 1  (prompt-body section alone: the fixed multi-line check is alive)
 P3 OpenDroid in strings.xml:     sed -i 's|</resources>|    <string name="probe_label">OpenDroid</string>\n</resources>|' \
    android/app/src/main/res/values/strings.xml
                                  bash scripts/check-branding.sh   -> exit 1  (resources section)
 P4 stale provenance-map path:    sed -i 's|ai/eqo/core/service/NotificationListenerBridge.kt|ai/eqo/core/core/service/NotificationListenerBridge.kt|' \
    android/Phase-One/evidence/task-005-provenance-map.md
                                  bash scripts/check-branding.sh   -> exit 1  (set-identity diff names the stale row)
 each probe reverted with git checkout -- <file>; clean tree re-verified -> exit 0
 ```

 These cover the reviewer's planted probes (lower-case `opendroid`, `OPENDROID` in a prompt body,
 `OpenDroid` in strings.xml; canonical-case variants were already caught before) plus P2b/P4 for
 the fixed multi-line check and the new provenance path check.

 ### Repo checks

 ```
 bash scripts/check-branding.sh -> BRANDING GATE PASSED, exit 0 (kt files: 160; provenance rows: 160)
 bash scripts/check.sh          -> exit 0
 no-Leap grep: grep -rniE 'liquid|leap-sdk|ai\.liquid' android/ --include='*.gradle' --include='*.kts' --include='*.kt' --include='*.toml'
             -> no matches (grep exit 1 = clean)
 ```

 ### What is still NOT verified after review round 1

 - Screenshots of the app label and the in-app notices screen need the phone (unchanged; static
   evidence remains `strings.xml` `eqo_label`/`notices_body` and the res scan).
 - On-device verification of the `eqo://legal` deep link and the file provider (device needed).
 - The legacy import's on-device behavior is covered by a JVM unit test that asserts which
   preference file name is opened (`opendroid_prefs`); an instrumented migration test on a device
   with upstream-era data has not been run.
 - ClosePaw donor code is still not extracted (later Phase One tasks); its NOTICE text is now
   included verbatim in NOTICE (R4), sourced read-only from `imoonkey/closepaw`.
