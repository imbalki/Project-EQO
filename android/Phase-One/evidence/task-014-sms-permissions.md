# TASK-014 evidence: SMS compose-only and permission narrowing (issue #19)

Captured: 2026-10-02, host <host> (Windows 11, bash/MSYS); re-base evidence appended 2026-10-03.
Worktree: C:\Users\<user>\Claude\worktrees\task-014
Branch: agent/android/19-sms-permissions (re-based onto TASK-005, see section 6)
Toolchain: JDK 21.0.12.1 (Eclipse Temurin), Gradle wrapper 9.7.0, AGP per pins (TASK-001/003).

## 1. Merged-manifest dump (acceptance: no READ_SMS / RECEIVE_SMS)

Manifest merge from the post-rebase gate run (:app:processDebugMainManifest, 2026-10-03):

    app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml

    $ grep -c "uses-permission" <merged manifest>
    1
    $ grep -icE "READ_SMS|RECEIVE_SMS|termux|RUN_COMMAND" <merged manifest>
    0   (exit 1 = no matches, gate satisfied)

The single uses-permission line is `ai.eqo.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
(androidx-generated receiver-visibility permission, added by TASK-005's androidx.core
receiver wiring) — not a dangerous permission. No SEND_SMS (not needed — ACTION_SENDTO
delegates into the messaging app), no READ_SMS, no RECEIVE_SMS, no
com.termux.permission.RUN_COMMAND. Module pre-compiled manifests
(core-llm: network state/location/dataSync; platform-a11y: READ_PHONE_STATE)
match TASK-004 and contain no SMS or Termux entries.

Robolectric guard against the *runtime-merged* manifest (manifest merger output
seen through PackageManager):
    app/src/test/kotlin/ai/eqo/SmsPermissionsManifestTest.kt

## 2. Test output (acceptance: no bridge/Termux code path, termux_shell hidden)

Post-rebase gate run (single Gradle invocation, section 4):

    $ ./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt \
        --console=plain --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m \
        -Pkotlin.daemon.jvmargs=-Xmx1024m
    ... BUILD SUCCESSFUL in 16m 7s
    459 actionable tasks: 285 executed, 174 up-to-date
    GRADLE_EXIT=0

JUnit summary rolled from */build/test-results/testDebugUnitTest/TEST-*.xml by
script (roll-test-counts.py, xml.etree suite attributes):

    TOTAL: tests=302 failures=0 errors=0 skipped=2

(291 tests + 2 skipped = TASK-005 base at 97fc72b; + 11 TASK-014 guard tests = 302.)

TASK-014 test classes (all green, post-rebase names):
- ai.eqo.SmsPermissionsManifestTest                              tests=2 fail=0 err=0 skip=0
  (merged manifest requests no SMS permissions beyond SEND_SMS; no
   READ_SMS/RECEIVE_SMS/RUN_COMMAND)
- ai.eqo.core.agent.SmsComposePolicyTest                         tests=3 fail=0 err=0 skip=0
  (DIRECT_SEND_ALLOWED=false; compose requires recipient+content; SEND_SMS
   still valid + auto-approvable but described as a user-sent draft)
- ai.eqo.core.agent.ReplyDispatcherSmsComposeTest                tests=2 fail=0 err=0 skip=0
  (replyViaSms opens ACTION_SENDTO smsto: with sms_body; refuses blank
   recipient/content without launching anything)
- ai.eqo.core.agent.SmsTermuxToolHiddenTest                      tests=2 fail=0 err=0 skip=0
  (no TERMUX/*_SHELL action in the schema; prompts never mention Termux)
- ai.eqo.core.llm.prompts.SmsComposePromptContractTest           tests=2 fail=0 err=0 skip=0
  (planning/main prompts state the compose-only contract)

## 3. Code-path greps (acceptance: no Python bridge launch, no Termux call)

    $ grep -rniE "termux|closepaw|bridge_py|RUN_COMMAND" android/ \
        --include="*.kt" --include="*.kts" --include="*.xml" --include="*.toml"
    -> three files, none source-side:
       - app/src/main/res/values/strings.xml (notices_body attribution block —
         the documented NOTICE context that must name upstream projects)
       - app/src/test/kotlin/ai/eqo/SmsPermissionsManifestTest.kt
         (asserts the banned permissions are NOT requested)
       - core-agent/src/test/java/ai/eqo/core/agent/SmsTermuxToolHiddenTest.kt
         (asserts no termux/shell action is exposed)

    Source-side (android/*/src/main) hits: 0. No code path launches the ClosePaw
    Python bridge (the asset itself is not packaged by EQO's android/ build at
    all — the copyClosePawBridge-style packaging stayed donor-only; TASK-004) and
    no code calls Termux.

    `termux_shell` is not exposed: ActionSchema.getAllActionNames() contains
    no TERMUX/SHELL action (asserted by SmsTermuxToolHiddenTest), and no
    module registers a shell tool (actions package was quarantined in
    TASK-004; the adapter interfaces declare no implementations).

## 4. Static + build gates (post-rebase run, 2026-10-03)

    $ ./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt \
        --console=plain --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m \
        -Pkotlin.daemon.jvmargs=-Xmx1024m
    ... BUILD SUCCESSFUL in 16m 7s / GRADLE_EXIT=0
    459 actionable tasks: 285 executed, 174 up-to-date

    $ bash scripts/check-branding.sh
    ... BRANDING GATE PASSED / BRANDING_EXIT=0
    (kt files: 166; provenance rows: 166)

    $ bash scripts/check.sh
    ... OK / CHECK_EXIT=0   (secret scan + shell syntax + branding gate)

    $ grep -rniE "liquid|leap-sdk|ai\.liquid" android/ \
        --include='*.gradle' --include='*.kts' --include='*.kt' --include='*.toml'
    -> no output, exit 1 (no matches; gate satisfied)

    $ git grep -InE '(sk-or-v1-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{30,})' \
        -- . ':!scripts/check.sh'
    -> no output, exit 1 (no matches; gate satisfied)

## 5. Security-review note (acceptance D-005 line)

agents/android/checklists/SECURITY.md — TASK-014 recorded deviation section:
the ClosePaw donor APK still ships the unreviewed `closepaw_bridge_py`
Python script; it is excluded from EQO's build, termux_shell is not exposed,
RUN_COMMAND is not requested; removal deferred to a later phase.

## 6. Re-base onto TASK-005 (2026-10-03)

TASK-014 passed independent review at 0398033 (10 commits on old main a567321).
TASK-005 merged as 97fc72b (com.opendroid.ai -> ai.eqo rename, OpenDroid* -> EQO*).
Re-base: `git rebase --onto origin/main a567321 agent/android/19-sms-permissions`,
safety branch `backup/019-pre-rebase` at 0398033 (local only, not pushed).

Pre-rebase head:  0398033 (10 commits)
Base:             a567321 -> 97fc72b (origin/main)
Post-rebase SHAs (git log --oneline origin/main..HEAD at gate time):

    341d0ed docs(android): record TASK-014 guard files in the provenance map (Refs #19)
    033b48e docs(android): record TASK-014 evidence — manifest, tests, gates (Refs #19)
    011c523 style(core-agent): wrap detekt MaxLineLength findings (Refs #19)
    f8b9a1a style(core-agent): fix ktlint findings in SmsComposePolicy (Refs #19)
    076ddcd test(core-agent): make SMS guard tests compile against real APIs (Refs #19)
    2f48d46 docs(security): record D-005 unreviewed-script deviation note (Refs #19)
    8ef1381 test(core-agent): keep termux_shell hidden and prompts compose-only (Refs #19)
    7eed124 test(android): compose-only and manifest-narrowing guards (Refs #19)
    c618dfd feat(core-llm): describe SEND_SMS as a user-sent draft (Refs #19)
    6bff0c2 feat(core-agent): compose-first SMS, never a direct send (Refs #19)
    f325ce2 chore(build): ignore the Kotlin session directory (Refs #19)

(plus the present evidence update commit; each replayed commit's content is
unchanged apart from the package/path renames listed below.)

git diff --stat 97fc72b HEAD (before this evidence update):

    .gitignore                                         |  3 +
    agents/android/checklists/SECURITY.md              | 10 +++
    .../Phase-One/evidence/task-014-sms-permissions.md | 89 ++++++++++++++++++
    android/app/build.gradle.kts                       |  4 +
    .../kotlin/ai/eqo/SmsPermissionsManifestTest.kt    | 56 ++++++++++
    .../main/java/ai/eqo/core/agent/ReplyDispatcher.kt | 37 +++++----
    .../java/ai/eqo/core/agent/SmsComposePolicy.kt     | 33 ++++++
    .../core/agent/ReplyDispatcherSmsComposeTest.kt    | 52 +++++++++
    .../java/ai/eqo/core/agent/SmsComposePolicyTest.kt | 44 +++++++++
    .../ai/eqo/core/agent/SmsTermuxToolHiddenTest.kt   | 42 +++++++++
    .../main/java/ai/eqo/core/agent/ActionSchema.kt    |  4 +-
    .../ai/eqo/core/llm/prompts/PlanningPrompts.kt     |  2 +-
    .../java/ai/eqo/core/llm/prompts/SystemPrompts.kt  |  4 +-
    .../llm/prompts/SmsComposePromptContractTest.kt    | 39 +++++++++
    14 files changed, 400 insertions(+), 19 deletions(-)

Identical shape to the pre-rebase diff (14 files, +400/-19).

Conflicts resolved during the replay (all mechanical):
- cbc0d13: SmsComposePolicy.kt file-location (git directory-rename suggestion
  accepted: com/opendroid/ai -> ai/eqo path, `package ai.eqo.core.agent`);
  ReplyDispatcher.kt auto-merged, one residue fixed — the merge kept the renamed
  `import ai.eqo.core.util.DeviceCapabilities` that this commit deletes (its only
  use, canSendSms, goes away), so the import was removed to keep TASK-014's intent.
- 1f1d7ce: ReplyDispatcherSmsComposeTest.kt + SmsComposePolicyTest.kt
  file-location (moved to ai/eqo paths, packages fixed).
- d3d66d4: SmsTermuxToolHiddenTest.kt file-location (moved, package + prompt
  imports fixed); SmsComposePromptContractTest.kt moved to
  core-llm/src/test/java/ai/eqo/core/llm/prompts/ (package + ActionSchema import).
- Commits 943b4ac..0398033 replayed clean (git followed the moved files).

Mechanical-proof commands:

    $ git diff a567321 0398033 > pre.diff
    $ git diff 97fc72b HEAD    > post.diff
    $ sed -e 's|com\.opendroid\.ai|ai.eqo|g' -e 's|com/opendroid/ai|ai/eqo|g' \
          -e 's|OpenDroid|EQO|g' -e 's|OPENDROID|EQO|g' -e 's|opendroid|eqo|g'
    $ diff pre.norm post.norm            # residual listing
    $ diff pre.plusminus post.plusminus  # +/- content lines only

Results: 9 of 14 files byte-identical after normalization (all five new code
files, .gitignore, SECURITY.md, SmsPermissionsManifestTest.kt,
SmsComposePromptContractTest.kt). Residuals, each explained:
1. `index <sha>..<sha>` lines differ — base blob hashes, not content.
2. app/build.gradle.kts: hunk moved (38,4 -> 41,6) and gained context lines that
   are TASK-005's own additions (androidx.core.ktx block, testOptions block).
   TASK-014's 4 added dependency lines are identical in both diffs.
3. ReplyDispatcher.kt: pre-rebase base (a567321) had SmsManager and
   DeviceCapabilities imports 3 lines apart (one hunk `@@ -7,10 +7,8 @@`); the
   TASK-005 base sorts DeviceCapabilities first, so the same two deletions land
   in one hunk `@@ -1,14 +1,12 @@` with the Origin: header as context. The only
   content-order residual in the normalized +/- comparison is the swapped order
   of those two `-import` deletion lines.
4. The four modified MOVE files (ReplyDispatcher, ActionSchema, PlanningPrompts,
   SystemPrompts) differ in line 1 only: the `Origin:` header keeps the ORIGINAL
   upstream path (`yashab-cyber/opendroid`, `com/opendroid/ai/...`) and was
   deliberately NOT rewritten (provenance truth), while the normalization
   under comparison rewrites it.

Step-5 name fixes: the four guard tests now declare/import the ai.eqo packages
(assertions unchanged); evidence test-class names updated above. Branding gate
required the 6 new Kotlin files to appear in task-005-provenance-map.md — added
as EQO-NEW rows (commit 341d0ed), gate script untouched (kt files: 166; rows: 166).
