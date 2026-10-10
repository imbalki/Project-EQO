# Phase One phone test log

## Runtime screenshot resolver and picker — t_62291cf6 — NOT TESTED ON PHONE

- Local-only branch `fix/find-screenshots`, worktree `w-findshots`; no push/PR/gh,
  install, phone, real contact/file content, live provider/model or credentials accessed.
- Owner-supplied DCIM/Screenshots evidence is not a new phone test. Regression fakes
  reproduce 26 OEM-style JPEG names, absent Pictures/Screenshots and an unrelated
  depth-limited folder; scoped screenshot roots, capture-date matching and newest
  selection are exercised separately from whole-storage search failure.
- One-document ACTION_OPEN_DOCUMENT fallback for zero matches/search failure and
  Choose a different file; stage selected content, release original read grant before
  confirmation, show name/date/size and Send / Cancel. Existing 60-second confirmation,
  recipient approval, capture, takeover and resume guards remain unchanged.
- Fixed allowlisted attachment reasons map to readable run detail and EqoRun kinds;
  tests prohibit generic diagnoses and private names/paths/exception payloads.
- Executable planner few-shots distinguish existing screenshot, yesterday, explicit
  capture, bill email (known own address only), and latest camera photo.
- Checklist and limitations: `docs/agents/FIND-SCREENSHOTS.md`.
- First shared-host gate attempt: three sibling Gradle wrappers remained active after
  one 3-minute wait. Own invocation uses 2 workers, in-process Kotlin and 1536 MB heap.
  All touched modules' ktlintFormat/ktlintCheck/detekt, :app:lintDebug and all three
  testDebugUnitTest tasks requested with --continue; progress is real, not a pass.
  It found new line-length/complexity/import violations; corrections are in progress.
  This first invocation is not a frozen-source verification (regressions were added
  while dependency compilation ran).
- Second invocation: BUILD FAILED in 29m 30s, 410 tasks (93 executed). Core-agent
  119 tests pass; actions 190 tests (one macro timeout, three Windows symlink skips);
  app 336 tests (one new neutral-button test lacked Android looper dispatch, corrected).
  :app:lintDebug and all three ktlintCheck passed. Additional detekt findings corrected.
- Third invocation: BUILD FAILED in 20m 31s, 410 tasks (76 executed). Core-agent
  119 and app 336 tests pass; actions 190 tests have only the historical untouched
  AutomationExecutorsTest macro delete/list UncompletedCoroutinesError and three
  Windows symlink capability skips. All attachment/resolver/picker/recipient/status
  tests and StudyLoopWiringTest pass. Core/app detekt and app lint pass. Actions
  ktlintCheck ran before its formatter had completed the new email handoff; the file
  was corrected by ktlintFormat. Two remaining actions detekt findings (cleanup throws
  count and a formatter-collapsed media fake line) corrected.
- Read-only review caught outgoing-cancellation cleanup, neutral control touch and
  oversized-picker reason issues, now covered by passing fakes. Owner-requested
  persistable one-document read grant remains: immediate release after copy plus
  next-process stale read-only/non-tree grant recovery, preserving workspace trees.
- Final separated invocation (frozen Kotlin source): formatting BUILD SUCCESSFUL in
  1m 39s (27 tasks, four executed); checks BUILD FAILED in 14m 35s (386 tasks,
  29 executed). PASS all :core-agent/:actions-android/:app ktlintCheck and detekt;
  PASS :app:lintDebug (zero XML issues); PASS :core-agent:testDebugUnitTest (119)
  and :app:testDebugUnitTest (336). :actions-android:testDebugUnitTest runs 190 tests,
  with one failure in untouched AutomationExecutorsTest `delete and list macros keep
  system macros and sort names` (10-second UncompletedCoroutinesError), three Windows
  symlink capability skips, zero errors. Across the three modules: 645 tests, one
  failure, zero errors, three skips. FULL GATE IS NOT GREEN. This historical macro
  failure is recorded in earlier handoffs, but was not baseline-reproduced this run;
  no unrelated macro implementation/test was changed to hide it.
- Final focused results from the full suites: AttachmentFileSearchTest 26/26,
  SharedStorageCatalogTest 9/9, ContactRecipientsTest 21/21, RegistryPlannerTest 18/18,
  AttachmentSpecTest 14/14, TaskAttachmentSelectionTest 9/9, RunStatusMappingTest 12/12,
  TaskControlPresentationTest 3/3, StudyLoopWiringTest 11/11; zero failures/errors/skips.
- Exact final tasks: first :core-agent:ktlintFormat :actions-android:ktlintFormat
  :app:ktlintFormat; then :core-agent:ktlintCheck :actions-android:ktlintCheck
  :app:ktlintCheck :core-agent:detekt :actions-android:detekt :app:detekt :app:lintDebug
  :core-agent:testDebugUnitTest :actions-android:testDebugUnitTest :app:testDebugUnitTest
  --continue. Both invocations use --no-daemon --max-workers=2
  -Pkotlin.compiler.execution.strategy=in-process --console=plain and
  -Dorg.gradle.jvmargs='-Xmx1536m -XX:MaxMetaspaceSize=1024m -XX:ActiveProcessorCount=2
  -Dfile.encoding=UTF-8'. Formatter/check invocations are serialized, not concurrent.
- Logs retained only under ignored android/app/build/reports/findshots/; no raw logs
  committed. Repository secret/branding/provenance gate PASS (496 Kotlin files/rows),
  added Kotlin lines <=120 and git whitespace checks PASS. Same-card review required.
- NOT RUN: root/all-module suites, release/APK build/install, instrumentation, live
  model/provider, phone or GitHub CI. Lead owns independent review, CI and phone checklist.
## Keep real speed-dial flow — `t_821c6c33` — NOT TESTED ON PHONE

- Local-only `fix/keep2`, based on `edf1612`; no push/PR/gh, device, private note
  text, account or provider request. The card's owner-supplied widget IDs are
  technical evidence for the flow, not verification of this build.
- Keep hints consumed by the registry planner now specify OPEN_APP + WAIT 3000,
  Create a note content-description, id:new_note_button, id:editable_title,
  optional supplied body into id:edit_note_text, PRESS_BACK autosave. Toolbar is
  Search Keep, never the note field. No invented body or Take a note bar.
- Explicit id: targets match exact resource IDs/slash-delimited suffixes only;
  no label, sole-input or focused-field fallback. Active-root/own-window,
  password, takeover and approval gates remain. Safe note navigation allowlist
  includes only the text-note speed-dial ID, not list/photo/drawing or arbitrary IDs.
- Missing Create a note fails on its first probe (no wait/retry), feeds the
  existing control_not_found -> plain Needs you presentation and stops the loop.
- Synthetic fake-tree tests mirror home, four unlabelled speed-dial controls and
  title/body/search fields, missing-ID and EQO-window refusal. Fake planner tests
  cover prompt consumption, supplied body and add/write/create title-only proposals;
  they do not establish live-model output quality or on-device autosave.
- No new Kotlin files; provenance still covers 494 tracked files/rows.
- Phone checklist and exact action parameters: [KEEP-NOTE-FLOW.md](KEEP-NOTE-FLOW.md).
- PASS: final Gradle exit 0, BUILD SUCCESSFUL in 8m 40s (370 tasks: 13 executed,
  357 up-to-date). From android/, exact tasks: `:core-agent:ktlintFormat
  :platform-a11y:ktlintFormat :core-agent:ktlintCheck :core-agent:detekt
  :platform-a11y:ktlintCheck :platform-a11y:detekt :app:lintDebug
  :core-agent:testDebugUnitTest :platform-a11y:testDebugUnitTest`, with
  `--continue --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
  --console=plain`. No test filters. XML: core-agent 119 + platform-a11y 123 =
  242 tests, zero failures/errors/skips; app debug lint XML has zero issues.
- Initial gate BUILD FAILED in 48m 10s solely on one core-agent detekt long test
  line; corrected before final rerun. Initial unit suites and app lint passed;
  final core tests reran, platform tests remained up-to-date on unchanged source.
  Raw local logs are ignored under `android/app/build/reports/keep2/`, not committed.
- PASS: `bash scripts/check.sh` (494 Kotlin/provenance rows), `git diff --check`,
  and added Kotlin lines <=120. No method signature changed; pinned study wiring untouched.
- NOT RUN: app/other-module unit suites, other-module static checks, root/all-module
  gate, APK/release, instrumentation, phone, live model/provider or GitHub CI.
  Local commit only; independent same-card review and owner phone checklist required.


## Location fix — `t_9fd2d126` — NOT TESTED ON PHONE

CI review rework (2026-10-10; lead-reported ContactRecipientsTest failure):
- Source inspection identifies stale `ACTIONS.size` (five) lookup assertions: per-plan
  preparation deliberately reuses Alice's phone for four actions, with a separate email
  resolution, totaling two. Both assertions now require exactly two. Removing that cache
  would reintroduce potential text/location recipient drift during preparation.
- Safety assertions are retained/strengthened: every approved literal and display label
  is checked, the WhatsApp launch still must target the original approved phone, and no
  execution lookup occurs after changing the fake contact and denying Contacts permission.
  Production code and approval/takeover guards are unchanged. No new Kotlin file.
- PASS: `bash scripts/check.sh` (489 Kotlin files/provenance rows), `git diff --check`,
  added Kotlin lines <=120. No raw logs committed.
- NOT RUN: `:actions-android:testDebugUnitTest --tests '*ContactRecipientsTest*'`, full
  `:actions-android:testDebugUnitTest`, app/actions `ktlintFormat`, `ktlintCheck`, `detekt`
  and `lintDebug`. First host probe found two sibling wrappers; after three minutes and
  on the final probe, three were active. Skipped Gradle under the lead's busy-host policy;
  no sibling processes touched. Failure was not locally reproduced; changed test not run.
- NOT RUN: app tests, root/all-module gates, APK/release, instrumentation, phone or CI.
  CI/free-host execution remains required before acceptance; same-card review requested.

Lead approved check-only mid-run refusal, not a permission-dialog takeover exemption.
Android verification remains unverified; lead permits review with the busy-host caveat.
No phone was contacted; no real location or recipient
was saved. Existing Google Maps message generation and redacted position handling are unchanged.

Host attempt (2026-10-10): app/actions ktlintFormat began, but the tool transport timed
out at 420 seconds. A corrected process probe found several sibling Gradle wrappers
(`-jar gradle-wrapper.jar`, not `GradleWrapperMain`). Only this worktree's wrapper was
cancelled to obey one Gradle at a time. Two subsequent five-minute bounded waits still
found sibling wrappers; no second build was launched. No Android lint/static/unit gate
has passed. No local commit existed at that attempt; no push, PR, APK or device test.
`scripts/check.sh` passed with 489 tracked Kotlin files and 489 provenance rows.

Resumed host verification (2026-10-10, lead's revised busy-host policy):
- Existing local WIP checkpoint `ee5a66a` contains the saved code/tests/docs, uses
  `imbalki <imbalki@users.noreply.github.com>` and includes no raw logs. Worktree was clean.
- Four sibling Gradle wrappers were active on both probes separated by three minutes.
  No new Gradle command was started; no sibling process was changed.
- Fresh `bash scripts/check.sh` PASS (489 files/rows), checkpoint whitespace check PASS.
- NOT RUN in this resumed attempt: app/actions ktlintFormat, ktlintCheck, detekt,
  lintDebug and testDebugUnitTest (full app and focused/full actions suites).
  The earlier cancelled formatter is NOT a successful formatting or build gate.
- NOT RUN: root/all-module Gradle, APK/release tasks, instrumentation, phone tests or CI.
- Code/tests remain unverified WIP. Same-card review is requested under the lead's policy;
  lead owns full CI and local lint/ktlint/detekt follow-up. No push/PR/gh by this worker.

Required next Android verification from `android/`: app/actions ktlintFormat, then both
modules' lintDebug, ktlintCheck, detekt and testDebugUnitTest with `--max-workers=2
-Pkotlin.compiler.execution.strategy=in-process --console=plain`. Focused actions
ContactRecipientsTest and ShareActionsTest should run as well; do not claim them passed.

Phone checklist (lead/owner; record build commit and pass/fail, no real identifiers):
- [ ] Named WhatsApp text + location: Contacts and Location shown/requested BEFORE
      approval/run; hold the answer longer than five seconds. Text and Maps link go to one chat.
- [ ] Typed number: only Location requested, no Contacts. Previously frozen recipient:
      revoke Contacts after preparation; execution must use the approved literal destination.
- [ ] Deny Location or Contacts: nothing sends. Stop during preflight, then late Allow:
      no run, no automatic approval/resume. Explicit retry obtains access before approval.
- [ ] Revoke Location after text completes (controlled lab): no mid-run permission dialog;
      location step says Needs you. Retry remaining steps obtains access, previews only
      location and requires approval, then sends only the Maps link. Text is not repeated.
- [ ] Retry denial twice; reject reapproval; rotate/reopen while stopped: completed text
      stays excluded within the live process. Unchanged Start uses remaining-step retry.
- [ ] Approval preference OFF still requires fresh approval of remaining steps.
- [ ] Android 12+: precise/approximate requested together. Approximate-only answer cannot
      authorize precise sharing. Email literal/name and SMS route use the frozen destination.
- [ ] Real takeover, own-window/protected-window refusal, Pause/Stop and unknown effects
      retain existing guards. Unknown effects/manual draft handoffs do not offer blind retry.
- [ ] Process death does not start/repeat any step; no persistent delivery/dedup guarantee.
      Check logs for counts/status only, never positions, Maps link, names or numbers.
## Keep and app-specific taps — t_2f7fbd22 — NOT TESTED ON PHONE

- Local-only `fix-keep`, based on `9716dbd`. No phone, private notes, provider or credentials accessed.
- Static package-keyed `AppControlHints` feeds the registry planner: Keep's Take a note / New text note entry,
  Title and Note fields, Back autosave; Gmail, Messages, WhatsApp search, Chrome, Calendar, Contacts,
  Calculator and Clock controls. These are public guidance, not proof of every app version/language.
- CLICK_TEXT accepts ordered comma-separated alternatives. Exact text/content-description/ID suffix matches
  precede partial labels; existing non-input preference and clickable-ancestor handling remain. Accepted or
  rejected mutations are never followed by a second alternative. Password, own-window and takeover guards remain.
- CLICK_TEXT/CLICK_ID use four missing-node probes with 700 ms intervals (2.1 seconds waiting, not 17 retries).
  Exhaustion reports `control_not_found`; run presentation says Needs you with manual/reapproval guidance.
  Legacy tap_text reports the same fixed reason immediately rather than retrying an irreversible tap.
- Added the missing registry PRESS_BACK schema/executor through the existing gated Android Back facade;
  advanced-control risk, registry execution/approval scope and no automatic fallback are retained (99 actions).
- Named-note plans use open + wait + entry alternatives + title/body typing + Back. Title-only requests must
  omit body rather than invent it. Note-edit draft validation checks EVERY alternative and allows Back autosave;
  Send/Share/Publish, arbitrary IDs and communication draft submit routes remain rejected before approval.
- This base has no enabled vision-locate fallback/settings; no fictitious vision button or automatic upload was
  added. Explain screen is separate from locate/tap. A vision offer needs integration with a real enabled fallback.
- Broad five-module host gate ran with `--continue --no-daemon --max-workers=2`, in-process Kotlin,
  `-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8`.
  Tasks per core-llm/platform-a11y/core-agent/actions-android/app: ktlintFormat, lintDebug, ktlintCheck,
  detekt, testDebugUnitTest. Actual output: BUILD FAILED in 1h 32m 33s (477 tasks, 362 executed).
  XML: core-llm 277 (one existing skip), platform 120, core-agent 115, actions 169 (one macro failure,
  three Windows symlink capability skips), app 309; 990 tests, one failure, zero errors, four skips.
  All new tests passed; the sole test failure is historical macro delete/list UncompletedCoroutinesError,
  not baseline-reproduced here. All five lintDebug tasks completed; static checks passed except app progress
  complexity 16 (limit 15), now fixed by extracting existing target selection without a suppression.
- Final corrected-source confirmation: platform-a11y/app ktlintFormat, lintDebug, ktlintCheck, detekt,
  testDebugUnitTest with the same memory/worker/compiler flags exited 0: BUILD SUCCESSFUL in 22m 28s
  (389 tasks: 52 executed, 337 up-to-date). Fresh XML: platform 120 + app 309, zero failures/errors/skips.
  All five module lint XMLs contain zero issues. Earlier attempt crashed from native memory allocation;
  retries caught/fixed a long line, app complexity and facade method count, without global suppressions.
- Repo secret/branding/provenance gate passes (488 Kotlin files/rows); added Kotlin lines <=120 and
  git diff --check pass. The broad actions macro failure remains; full gate is NOT green. Release/APK,
  instrumentation, other-module suites and live provider/device/CI were not run. No push/PR/gh/install.
  Lead owns CI and phone checks; same-card independent review must approve before publication.

Phone checklist (lead/owner, synthetic content only; record build and result):
- [ ] Keep: “add a note called Test in Keep”; preview has correct entry alternatives, Title and Back, no
      invented body. With an explicit synthetic body, verify both fields and autosave after Back.
- [ ] Keep-like variants: text-bar and content-description FAB; missing labels stop promptly with Needs you,
      no repeated mutation and no next typing step. Explicit new plan/reapproval only after manual correction.
- [ ] Gmail: Compose and To/Subject/Compose email fields; verify draft vs approved Send behavior.
- [ ] Calendar: Create/Event entry and title; verify Save only when the approved plan requests it.
- [ ] Different language/version, delayed launch, Stop/Pause/takeover and own EQO approval window refusal.
- [ ] When an actual vision-locate fallback lands/enables: offer only after failure, require existing consent,
      capability and fresh approval; disabled state must never capture/upload or automatically tap.

Task-supplied Round-3 evidence: OPEN_APP Keep and WAIT succeeded; CLICK_TEXT NodeNotFound repeated x17
at 09:24 before failure. This is historical owner evidence, not a phone result for this branch. The checked-in
starting log has historical round-1/round-2 material, not a separate Round-3 table; no missing results invented.
## Screenshot/gallery repair — t_f123214e — NOT TESTED ON PHONE

Local branch `fix-shots`; no phone contacted, no model request, no push/PR/gh action.
Task-supplied Round-3 evidence: All files access ON, latest-screenshot WhatsApp requests failed
with misleading `no_screenshot_permission` or ASK_USER; capture correctly refused EQO foreground.
The supplied evidence is motivation, not a new phone result. This checkout has no separately
labelled Round-2/Round-3 phone tables; the existing history and card evidence were read.

Phone checklist (lead/owner; synthetic files and contacts, record actual result and build commit):
- [ ] Send my latest/last screenshot to a test contact on WhatsApp: gallery search, correct chosen
  file named before Continue, correct chat/file; no new capture of EQO.
- [ ] Dated screenshot, latest photo/picture/PDF/file, and the eBay bill: correct `find:` filters;
  several matches show a chooser (including latest), zero matches show no_matching_file.
- [ ] Refer to the bill and say WhatsApp it to a test contact / email it to me; unresolved it or
  unknown own email asks rather than inventing a file/address. Repeat SMS attachment route.
- [ ] Explicit take a screenshot and send it: preview instructs opening the app first; EQO
  foreground hands off with eqo_in_foreground, protected window remains refused, no automatic resume.
- [ ] No recent EQO capture: last_screenshot uses newest gallery screenshot, displays exact name
  before Continue; tied newest timestamps require choice. Cancel/background/Stop sends nothing.
- [ ] Fresh private EQO capture works without All files access; stale/deleted capture falls back;
  gallery lookup without access says needs_all_files_access. No match says no_eqo_screenshot_yet.
- [ ] Delete/revoke access after disclosure; no stale file or private/excluded/link path is shared.
- [ ] Verify EqoRun logs never include search terms, file/contact names or paths; capture/copy
  failures without a proven protected-window diagnosis do not claim missing permission/protection.

Final frozen-source Gradle exits 0: BUILD SUCCESSFUL in 36m 5s, 416 tasks (224 executed,
192 up-to-date). One serial invocation from `android/`: all three modules' ktlintFormat,
ktlintCheck, detekt, testDebugUnitTest and lintDebug, with `--max-workers=2 --no-daemon
-Dorg.gradle.jvmargs='-Xmx1536m -XX:MaxMetaspaceSize=1024m'
-Pkotlin.compiler.execution.strategy=in-process --console=plain`. All three debug lint XML
reports have zero issues. Total 491 tests, zero failures/errors, three disclosed capability skips.
Repo/branding/provenance gate passes (487 Kotlin files/rows); no new Kotlin files; diff check and
changed Kotlin lines <=120 pass. Logs: ignored `android/app/build/reports/screenshot-fix/`.
NOT RUN: full actions suite (untouched macro cases), other-module unit suites, root/all-module
Gradle gate, APK/release build, install, device instrumentation, live model/provider or GitHub CI.
Lead owns publication and phone verification; worker requests same-card review, not completion.

Host coverage (synthetic fixtures): final XML reports actions 66 tests (zero failures/errors,
three Windows real-link capability skips), core-agent 117 and app 308 tests (zero failures/errors/skips).
Actions filter: `--tests 'ai.eqo.actions.impl.*File*Test' --tests 'ai.eqo.actions.impl.SharedStorage*Test'`;
core-agent/app suites unfiltered. Fake registry covers all nine combinations of three send routes and
search/gallery fallback references, empty-caption sends, URI/read grants and disclosure before staging.
Fake planner supplies proposals for latest/last/date screenshot, photo/picture/PDF/file, eBay bill,
WhatsApp it, email it to me and explicit capture. This is not live-model or actual delivery proof.
The shared AttachmentSpec blank-text exception permits only message/body/subject with a valid
attachment; recipients are never waived. The real registry uses the same exception as the planner.

Host validation caveats: initial static findings were corrected; the two-worker host exhausted
Windows commit/paging-file memory, then a reduced-heap compile exhausted Metaspace. Retried with
one Gradle at a time and bounded memory; these failed attempts are not passing verification.
## Voice v3 — t_112c2517 — NOT TESTED ON PHONE

Local-only `feat/voice-v3`. The checked-in log has no Round 3 heading; this card's supplied
Realme Android 11 early-stop result motivated the change, but is not verification of this build.
No real microphone, provider upload, APK install or device interaction was performed.
Verified code checkpoint `51c1125` (external in-progress checkpoint). Final serial command from
`android/`: `./gradlew :app:ktlintFormat :core-llm:ktlintFormat :app:ktlintCheck :app:detekt
:core-llm:ktlintCheck :core-llm:detekt :app:testDebugUnitTest :core-llm:testDebugUnitTest
:app:lintDebug :core-llm:lintDebug --continue --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process --console=plain`.
Saved ignored `app/build/reports/voice-v3/final-gates-2.log`: `BUILD SUCCESSFUL in 25m 45s`,
394 tasks (26 executed, 368 up-to-date). Fresh XML: app 322 and core-llm 279 tests, zero
failures/errors, one existing core skip. All 51 voice/provider tests pass; both module lint
XML reports have zero issues. Repo/provenance 491/491, diff and changed Kotlin line lengths pass.
Earlier failed runs found static findings, fake scheduling after key-check suspension, a
safe-error assertion and timer idle-endpoint mismatch; corrected, not baselined or concealed.
NOT RUN: all-module/root checks, other module unit suites, APK/release build, instrumentation,
real provider/mic, remote CI and all phone items below. Host tests do not establish phone quality.

Phone checklist (lead/owner must record build commit and outcomes; synthetic requests only):
- [ ] New/unset engine preference defaults to AI; a previously explicit Phone choice stays Phone.
      First-use consent declines without capture/upload; accept is remembered. Grant/deny/cancel
      microphone permission at first tap, never startup; typing remains possible after denial.
- [ ] English 20-word fixture: "Please open my notes tomorrow morning and remind me to buy milk
      bread fruit vegetables and rice after finishing work". Pause 3–4 seconds after "notes" and
      "milk": Recording continues, timer and level update; second tap stops and starts Transcribing.
- [ ] Hindi 20-word fixture: "कृपया कल सुबह नोट्स खोलकर मुझे दूध रोटी फल सब्जियां और चावल खरीदने की याद
      दिलाना जब काम खत्म हो". Pause 3–4 seconds after "नोट्स" and "चावल"; repeat with Hinglish,
      proper nouns and spoken numbers. Confirm original language, no translation or task execution.
- [ ] Review/edit the returned text; no auto-submit. Second session appends to the current draft.
- [ ] Six seconds of initial/post-speech silence stops capture; shorter pauses continue. Repeat
      quiet/noisy rooms and soft/loud voices; report threshold errors, not assumed reliability.
- [ ] Continuous speech reaches 90-second cap, never longer; the timer stops, no stuck microphone.
- [ ] Choose an audio model in setup: only catalog audio INPUT models, remembered separate from
      planner model; advertised Gemini default when available. Unsupported/text-only model gives
      a plain message without uploading; missing/rejected key gives its plain setup message.
- [ ] Airplane mode: plain cannot-reach-provider message, request draft unchanged, no auto-retry;
      choose Use Phone voice, then verify its recognizer/language pack or plain offline failure.
- [ ] Cancel during Transcribing, edit draft, leave/rotate while recording/uploading, retry and
      fail capture/provider: microphone released, voice cache file deleted, no late draft overwrite.
      Cancellation does not claim to recall provider audio already sent. No transcript/audio logs.

## Fixes for round 1 — t_7a91b291 — NOT TESTED ON PHONE

- 2026-10-10 accepted ADR-0008 implementation: preview lists runtime prerequisites; Allow now requests missing grants BEFORE approval/run. Existing Android grants are reused. The service is check-only; revoked permission does not launch a dialog and requires Stop plus explicit restart/reapproval. Removed the permission-controller touch bypass entirely; ordinary takeover, own-window and no-auto-resume protections remain.
- Fresh host verification, sequential modules with `--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`: actions ktlintFormat/check/detekt and 72 focused tests (5 suites), `BUILD SUCCESSFUL in 1m 28s`; platform-a11y format/check/detekt and all 102 tests (18 suites), `BUILD SUCCESSFUL in 50s`; app format/check/detekt and all 199 tests (34 suites), `BUILD SUCCESSFUL in 2m 38s`. Fresh XML has zero failures/errors/skips. `scripts/check.sh` passes (445 Kotlin/provenance rows); diff check passes; no added Kotlin line exceeds 120. Earlier failed iterations found formatting/static-analysis issues, an offline-weather test expectation changed by centralized prerequisite checking, and a JUnit test return type; final reruns above passed.
- Actual TaskActivity Robolectric regression: preview before Location request, typed-number Contacts omission, no pending run/controller before approval, Stop cancels preparation, late grant cannot start it, explicit retry with grant remembered reaches approval without executing. Fake registry tests prove deduplicated inventory and no read/send on preflight; requester tests retain cancellation/stale callback/120-second coverage and verify active-run missing-grant refusal. Platform source regression rejects any permission-touch bypass. No device/input-attribution success is claimed.
- Current phone checklist: grant/deny Location and named Contacts BEFORE run, wait over five seconds before answering, Stop/cancel then late callback, explicit retry after denial, remembered grant without another prompt, revoke between preview/approval or during run and verify no permission UI/automatic resume, delayed OEM input/lifecycle ordering, All files grant/return, screenshot guards, Keep note, arithmetic words, draft-only WhatsApp and multiple email recipients. NOT TESTED ON PHONE. No instrumentation, APK/release build, full Gradle gate or remote CI. Core-agent/core-llm and broad actions were not rerun in this rework; earlier evidence/failures remain below. Lead owns publication.

### Historical iterations (permission blocker superseded by the accepted pre-run flow)

- 2026-10-10 review rework: card remains BLOCKED on runtime-permission architecture, not ready for final approval. Re-fetched Android 11 InputDispatcher source and confirmed cross-UID outside-touch coordinate redaction (`FLAG_ZERO_COORDS`, lines 1846–1859 and 2523–2529). The existing Boolean permission helper test is not service-level attribution proof, and the bounds exception cannot identify a real Allow tap from the redacted event. Production permission/takeover code was not weakened or changed in this rework. See proposed ADR-0008 for the owner decision: recommended pre-run runtime prerequisites, with explicit restart after mid-run revocation; strict same-step mid-run continuation needs a different trustworthy input surface. No claim of a fixed real-phone Allow flow.
- Fixed the other review finding: named Keep/Notes writing can use known local note-navigation taps and typing before immutable plan approval; communication drafts still reject arbitrary taps/IDs, submit and sending actions. Added three fake-provider tests covering accepted note writing/type/draft routes, rejected Send/Share/Publish/unknown note taps and communication actions, and strict explicit don't-send/communication requests.
- Rework verification: sequential `:core-agent:ktlintFormat :core-agent:ktlintCheck :core-agent:detekt :core-agent:testDebugUnitTest --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`, exit 0, `BUILD SUCCESSFUL in 2m 27s`, 60 tasks (6 executed, 54 up-to-date). Fresh JUnit XML: 110 tests, 0 failures, 0 errors, 0 skipped in 19 suites. Earlier attempts caught and corrected a return-count violation, a long test line and a test fixture's wrong TYPE_TEXT parameter (`content`, not `text`); those failed runs are not passing evidence. `scripts/check.sh` passed (445 Kotlin files/445 provenance rows); `git diff --check` passed; no added Kotlin line exceeds 120 characters. No other module, phone, instrumentation, APK/release/full Gradle gate or remote CI was rerun in this rework. Prior test evidence follows, not a new device result.
- Branch `fix/round1-phone-tests`, based on `92802e7`. The checked-in log did not contain a "Phone round 1" section; this card's supplied phone findings are the inputs, not new device test results.
- Added fake/unit regressions for the permission apply-budget exclusion and 120-second wait, Stop/stale callbacks, narrowly scoped permission-dialog touch attribution, typed-number location permissions, All files Settings grant/recheck, title-only notes, calculator words/no-web-on-invalid-arithmetic, draft-only WhatsApp/no-send planning, explicit channel clarification, multiple email recipients and plain permission/failure copy.
- Screenshot investigation: `canTakeScreenshot=true` is already declared. All files access is not needed for EQO's private screenshot fallback. Own-window refusal is intentional; the failure now tells the owner to open the app to capture, without weakening that guard.
- All files Settings is normally completed before a run starts, after showing the preview warning. Returning with access granted continues the same plan; the owner still approves its actions. Settings is not a takeover exception. A real takeover or mid-run revocation never auto-resumes.
- Initial `:actions-android:testDebugUnitTest --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`: 145 tests, five failures (untouched `AutomationExecutorsTest` macro timeout and four `SharedStorageTest` Windows link/path cases). This is not a passing full gate. Initial compile also found a hidden Android permission-controller API; implementation now uses allowlisted system packages plus verified dialog/control bounds.
- Host checks passed sequentially with `--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`: actions `ktlintFormat`, `ktlintCheck`, `detekt` and 71 focused tests (`ContactRecipientsTest`, `ProductivityInformationTest`, `FileFeaturesRegistryTest`, `ShareActionsTest`, `SystemActionsTest`); full core-agent checks and 107 tests; full core-llm checks and 272 discovered tests (one skipped); full platform-a11y checks and 102 tests; final app checks and 197 tests, zero failures/errors/skips (`BUILD SUCCESSFUL in 8m 28s`). Recovery reran app format/check/static analysis/test tasks successfully in 1m 46s (tests up-to-date). `scripts/check.sh` passed again (445 Kotlin files, 445 provenance rows), `git diff --check` passed, and no added Kotlin line exceeds 120 characters. Full repository Gradle gate, APK build and device instrumentation NOT RUN. No device was contacted or changed. CI and lead-owned phone tests remain required.
- Recovery broad-actions retry: completed JUnit XML reports 146 tests, five failures, zero errors/skips. The same untouched cases fail: macro delete/list exceeds the coroutine test timeout; two shared-storage symlink fixtures lack Windows privilege; two shared-storage path assertions fail on Windows. The terminal transport timed out at 420 seconds while Gradle continued; the wrapper was subsequently observed exiting and XML results were parsed. These failures were not reproduced on an untouched baseline and are not claimed as a passing full actions gate. No out-of-scope macro/storage code was changed.
- Phone checks remaining: hold Allow for more than five seconds, grant/deny Location and Contacts, Stop while permission is pending, touch outside a permission dialog and verify takeover, missing All files access then grant/return, protected/own-app screenshot refusal, real WhatsApp draft preserving text with no Send, multiple Gmail recipients, Keep title creation, and arithmetic answers.


## Files v2 — t_ecfe91de — NOT TESTED ON PHONE

- Local branch `feat/files-v2`; tested code `add2fab`, no push/PR or phone contact. Synthetic files and fake selection callbacks only. Host results below do not verify a real attachment or Send button.
- Phone round-1 failure motivating this card (task-supplied evidence): All files access ON, FIND_FILES succeeded, then SEND_WHATSAPP/SEND_EMAIL with an attachment failed in the 12:28–12:31 test window. The pre-run planner could not know a file path; use `find:` directly on the send action rather than a made-up FIND_FILES output path.
- [ ] "send my eBay bill to <test contact> on WhatsApp": approved search in preview, exact chosen name in run status before Continue, correct attachment/chat, Send pressed only after disclosure.
- [ ] "email the screenshot from 7 October to me": planner emits `find:type=screenshot,date=YYYY-10-07` using the intended year; gallery screenshot from Pictures/Screenshots or DCIM/Screenshots, correct attachment, disclosure before Send.
- [ ] Several matches -> chooser: at most eight names with date/size, newest first; tapping a non-first match attaches only that file. `latest` still requires choice.
- [ ] Zero matches: plain searched-query message, no compose/send. No foreground UI: multiple-match name list and refusal.
- [ ] Cancel, background, rotation, timeout, Stop/takeover: no unintended attachment or send; ordinary task controls/takeover still work.
- [ ] Exact date/local midnight and inclusive range; words in different order/case; folder/type restrictions; All files access denied; hidden/other-app/staging/private/link paths excluded.
- [ ] TAKE_SCREENSHOT + last_screenshot remains working; staged copies swept on age/failure; logs contain only counts/kinds/codes, not names/paths/searches.
- [ ] Realme/Samsung/Xiaomi (where available): MediaStore screenshot/camera/gallery buckets, vendor-specific directories and Download/Downloads synonyms; modern and legacy WhatsApp/Business, Telegram, Instagram, Bluetooth and Documents.
- [ ] Discovery is reused across runs/restart; `rescan=true` refreshes on demand, zero matches refresh automatically; no filename or search saved in the folder-map preferences. MediaStore denied/incompatible/stale -> safe filesystem fallback, without guessing among matches or visiting Android/data/obb.

### Files v2 host verification (resumed run)

- Serial module-scoped Gradle, from `android/`, with `--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process --console=plain`. For each of `core-agent`, `core-llm`, `actions-android`, `app`: `:MODULE:ktlintFormat :MODULE:ktlintCheck :MODULE:detekt :MODULE:testDebugUnitTest`; app also `:app:assembleDebug`.
- Core-agent: exit 0, `BUILD SUCCESSFUL in 1m 38s`, 60 tasks up-to-date. JUnit XML: 108 tests, zero failures/errors/skips; cached passing execution from the first attempt, not a new test run.
- Core-llm: exit 0, `BUILD SUCCESSFUL in 5m 53s`, 68 tasks (16 executed, 52 up-to-date). JUnit XML: 272 tests, zero failures/errors, one existing pre-Android-Q/minSdk skip.
- App: exit 0, `BUILD SUCCESSFUL in 10m 15s`, 240 tasks (56 executed, 184 up-to-date). JUnit XML: 195 tests, zero failures/errors/skips, including four chooser/disclosure/lifecycle tests and the task-control takeover checks. Debug APK built at `android/app/build/outputs/apk/debug/app-debug.apk`; not installed.
- Actions: ktlintFormat/ktlintCheck/detekt pass, but full-suite exit 1, `BUILD FAILED in 7m 19s`, 120 tasks (10 executed, 110 up-to-date). JUnit XML: 155 tests, one failure, zero errors, three skips. Sole failure: unchanged `AutomationExecutorsTest` / `delete and list macros keep system macros and sort names`, `UncompletedCoroutinesError` after its explicit 10-second timeout, reproduced on retry. No unrelated macro production/test changes were made. This is NOT a passing full gate; lead/CI must investigate or confirm on an unloaded host.
- File-specific suites within that full run: AttachmentFileSearchTest 13, SharedStorageCatalogTest 7, SharedStorageTest 21, FileFeaturesRegistryTest 19 (60 total), zero failures/errors. Three actual-link tests skip only when host link creation is unsupported (Windows privileges); deterministic injected-link/path-ancestor exclusion tests do run. CI must exercise actual symlinks. Staging sanitization uses a portable source filename and separately asserts invalid-name sanitization.
- Verification fixes: split overlong resolver expression; search all coexisting folder aliases; avoid generic `Media` leaf learning across WhatsApp/Business; cap directory discovery globally; update stale reflective task-dialog test to pass the added chooser flag. No existing takeover guard, approval or send safety was relaxed.
- Focused actions rerun: `:actions-android:testDebugUnitTest --tests 'ai.eqo.actions.impl.AttachmentFileSearchTest' --tests 'ai.eqo.actions.impl.SharedStorageCatalogTest' --tests 'ai.eqo.actions.impl.SharedStorageTest' --tests 'ai.eqo.actions.impl.FileFeaturesRegistryTest'` with the same worker/compiler flags. Exit 0, `BUILD SUCCESSFUL in 1m 21s`, 106 tasks (one executed, 105 up-to-date). Fresh XML confirms 60 tests, zero failures/errors, three capability skips. This focused execution replaces the local XML files from the failing full run; the full-run failure remains recorded above, not concealed by the focused pass.
- `bash scripts/check.sh`: exit 0, `455` Kotlin files/provenance rows, `BRANDING GATE PASSED`, `OK`. `git diff --check`: exit 0. Added/modified Kotlin lines are at most 120 characters; pre-existing schema/registry long lines are unchanged.
- NOT RUN: phone, instrumentation, publication/CI or a repository-wide Gradle gate. Required phone checklist above remains unchecked.
## Voice v2 — 2026-10-09 — NOT TESTED ON PHONE

Branch `feat/voice-v2`, card t_e9801e01. No phone was contacted or modified. No real provider audio was uploaded.
Initial `:app:ktlintFormat :core-llm:ktlintFormat :app:compileDebugUnitTestKotlin --max-workers=2
-Pkotlin.compiler.execution.strategy=in-process` succeeded in 8m 27s (Gradle daemon log verified after tool timeout).
Final frozen-source checks for local code head `381b083` completed after the earlier worker timed out:
`:app:ktlintFormat :core-llm:ktlintFormat :app:ktlintCheck :core-llm:ktlintCheck :app:detekt
:core-llm:detekt :app:testDebugUnitTest :core-llm:testDebugUnitTest --max-workers=2`.
Saved `voice-v2-final-gates.log` reports `BUILD SUCCESSFUL in 9m 48s` (220 actionable tasks).
Verified XML: app 209 tests, core-llm 277 tests; zero failures/errors, one existing core-llm skip.
All 34 voice/provider tests pass (6 Android adapter, 10 presenter, 13 v2 recording/UI, 5 provider).
Both module debug lint XML reports contain zero fatal/error/warning issues. Repo/branding/provenance checks
pass (451 Kotlin files and 451 provenance rows); no changed Kotlin line exceeds 120 characters.
Resumed-worker confirmation on the unchanged Kotlin source: app/core-llm `ktlintCheck`, `detekt`,
`testDebugUnitTest` and `lintDebug`, with `--max-workers=2 --console=plain
-Pkotlin.compiler.execution.strategy=in-process`, exited 0: `BUILD SUCCESSFUL in 28m 3s`
(370 actionable tasks: 42 executed, 328 up-to-date). Unit tests were up-to-date from the passing frozen-source run.
CI remains the full merge gate; no APK install or real audio upload was performed.

Phone checklist (owner must record build commit and actual outcome):
- [ ] Phone engine: long English sentence with multiple 3–4 second pauses; partials appear live, stop control works.
- [ ] Phone engine: hi-IN long Hindi sentence with pauses; en-IN, device default and provider-reported languages.
- [ ] If the service still ends early, already-heard words remain; next mic tap adds rather than wipes.
- [ ] Grant, deny, cancel and permanently deny microphone permission; no request at startup, typing still works.
- [ ] Edit recognized words; nothing submits until the normal task button is tapped, preview/approval unchanged.
- [ ] AI engine: first-use consent accept/decline; only accepted consent allows capture and provider upload.
- [ ] Audio-capable configured model: English and Hindi verbatim transcripts; stop tap, 60-second cap, repeat append.
- [ ] Text-only model: plain audio-unsupported explanation and Use Phone voice fallback; no audio upload.
- [ ] Airplane mode on both engines: understandable failure, words kept, no automatic network retry.
- [ ] Leave screen while recording/transcribing, or tap mic during transcription: no late draft overwrite,
      microphone released and EQO voice cache file deleted. Test failures/cancellation as well as success.
- [ ] Compare phone vs AI accuracy and latency; verify offline language packs separately in the phone provider.

## Explain/handle UX polish (t_e84b3eaa): NOT TESTED ON PHONE

This worker has not installed or exercised this change on a phone or emulator. Owner-reported
earlier-phone behavior is the motivation, not verification of this revision. Full CI is the gate.
Fake-clock tests cover all panel sizes/background alpha and the five-second peek deadline;
Robolectric tests cover overlay close/reopen, touch flags, source intents, notification repost,
accessibility refresh events, hub opt-in/background intent and hidden-app recovery. Existing Ask
tests now assert a real focused request screen, voice control and no misleading Practice run title.
Final local checks: sequential scoped platform/app ktlintFormat, ktlintCheck, detekt,
testDebugUnitTest and lintDebug commands from CURRENT-HANDOFF.md exit 0 (1m 11s and 6m 10s).
Both use max-workers=2 and the in-process Kotlin compiler. JUnit XML confirms platform 111,
app 251, total 362 tests; no failures/errors/skips. The 22 new API-30/33 cases all pass.
Initial failures exposed premature deferred-panel cancellation, timer scheduling and Button.handler
receiver shadowing; corrected before the final full scoped runs. Removed an obsolete string and
documented the single native Switch XML lint exception (platform Activity/StudyTheme, no new dependency).
Repo/branding/provenance/secret, added/changed Kotlin line length, XML parse and whitespace checks pass.
NOT RUN: full all-module/root Gradle gate, APK assembly/install, release tasks, device/emulator
instrumentation, real provider/speech/OEM behavior or GitHub CI.

Phone checklist: follow the seven steps in CURRENT-HANDOFF.md's UX polish section. Record
build commit, OS/OEM, panel/large-font/rotation/keyboard behavior, peek touch-through and timing,
open-close-open from each entry, notification recovery after service death and preference-off,
QS editor/manual install (API 30) and platform add request (API 33+), accessibility/volume shortcut,
hub handle immediate rendering/off hint, battery settings, real Ask focus/mic and per-app/all unhide.

## Pairing discovery fix (t_9a512691, fix/pairing-discovery): NOT TESTED ON PHONE

Local-only implementation; no device was contacted or changed. No model/provider request was made.
Verified code commit: `c8d6d2ad98ee912f57e3ed2bd3fc19e814a7b40b` (later documentation-only commit does
not change tested code). Each command below ran sequentially from `android/`, with
`--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`, and exited 0:

| Gradle tasks | Actual final result |
|---|---|
| `:app:ktlintFormat` | BUILD SUCCESSFUL in 1m 2s |
| `:app:ktlintCheck :app:detekt :app:testDebugUnitTest` | BUILD SUCCESSFUL in 6m; 255 app tests |
| `:app:lintDebug :app:assembleDebug :app:processReleaseMainManifest` | BUILD SUCCESSFUL in 13m 3s |

- Final JUnit XML: 255 tests, zero failures/errors/skips. Pairing regressions: 50 executions (DNS parser 8,
  reply/pending state 6, fake resolve attempt 3, debug receiver 3, existing discovery state 10, notification/
  Android discovery 20 on API 30 and 33). Includes real framework failure/timeout callbacks with an injected
  mDNS lookup, late NSD rejection, code-envelope consumption, strict input, network-bound pending expiry,
  and unchanged fake PAIR+CONNECT-only/no-helper path. Test data is synthetic; no pairing sockets were
  opened against a device. Real multicast socket/lock behavior remains a phone checklist item.
- Source-set regression verifies the lab receiver is only under `src/debug`, guarded by BuildConfig.DEBUG
  and protected by DUMP. Read-back of actual merged debug/release manifests independently confirms the
  exact debug receiver/action/permission and absence of the receiver/action in release. The built debug
  APK's DEX contains the receiver. A release APK/DEX was not built or inspected.
- Actual APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 59,776,547 bytes; SHA-256
  `c7d10024f9b3154fcdb049cc6163b844b55d3e193cf5d8a208443fcc5aded1f8`.
  ZIP inspection confirms helper starters for arm64-v8a, armeabi-v7a, x86 and x86_64. No install attempted.
- `bash scripts/check.sh` passes: BRANDING GATE PASSED, 469 tracked Kotlin files/469 provenance rows.
  `git diff --check` and changed Kotlin lines <=120 checks pass. Raw host logs are kept under ignored
  `android/app/build/reports/pairing-fix/`, not committed. No push, PR or gh action by this worker.
- NOT RUN: full root/all-module Gradle checks, untouched modules' unit-test tasks, release lint/unit tests/
  APK build, connected/device instrumentation or GitHub CI. App tasks' dependency compilation/lint
  analysis is not those modules' unit-test execution. NOT TESTED ON PHONE; every phone item below remains.

Initial lint/compile findings were fixed without a baseline: bounded parser wire-number annotation,
named transport constants, split framework listener method, short imports/lines and explicit test generic.

Phone checklist (lead/owner; record build commit and pass/fail, never real identifiers or credentials):
- On the Android 11/ColorOS phone, open EQO setup, enable its notification, then open Wireless debugging.
  Open Pair device with pairing code. Confirm EQO finds both ports despite the framework mapping failure;
  app logs may contain only `resolve: nsd fail` / `resolve: mdns ok` (or generic fail), no values/names.
- While the pairing dialog stays open, reply with its six-digit code from the notification. Verify PAIR
  and CONNECT pass, no helper authorization occurs in the background, and returning/tapping Connect again
  displays the protected EQO helper prompt. Deny/Back must fail; Allow must be a real human tap.
- Force discovery to be unavailable in a lab build or environment: Reply remains visible. Supply code,
  pairing port and connection port in one string, with spaces then commas, without split screen. Verify
  the normal pinned path succeeds, and the code is cleared/consumed. Check the in-app box accepts both.
- Supply code and pairing port only: use a discovered connection port if present; otherwise verify the
  plain waiting message, no premature pairing or secure-enrollment report, successful late discovery,
  replacement by a three-field reply and discard after one minute. No connection port is guessed.
  Forget or a new foreground pair/reconnect must cancel the pending code before any new transport work.
- Reject malformed codes, Unicode digits, signs, out-of-range/equal ports, extra fields and bad separators
  before transport. Show a plain error; do not echo the entry or exception. Reopen expired pairing dialog.
- Test notification denial/channel blocking and the in-app fallback; keyboard allows spaces/commas and
  does not save/autofill the code. Check Android 13+ notification permission and background restrictions.
- Toggle Wi-Fi, change network/address, enable VPN (API 30–32), stop discovery and close during lookup:
  no stale endpoints/replies restore state; multicast socket/lock are released. Another LAN device's
  advertised service is never selected. Repeat toggle/re-pair and rejected server-key/pinning scenarios.
- In an installed DEBUG build only, use an explicit DUMP-authorized lab broadcast with the three extras
  documented in CURRENT-HANDOFF.md. Verify shared PAIR+CONNECT, no helper auto-consent. A normal sender
  without DUMP is denied; a release build has no receiver/action/class and cannot accept the broadcast.
- Verify actual local multicast/unicast delivery, OEM multicast filtering and service-name handling;
  host fixtures do not prove any of these real-network behaviors. CI remains the complete merge gate.

## Edge handle (t_a3fa16d0, feat/edge-handle): NOT TESTED ON PHONE

Local code commit `205739d`: sequential platform-a11y and app ktlintFormat, ktlintCheck, detekt,
testDebugUnitTest and lintDebug commands exit 0 (`--max-workers=2`, Kotlin compiler in-process).
JUnit XML totals: platform 111, app 186; 297 tests, no failures/errors/skips. Six app edge-handle
tests exercise real preference/widgets/overlay objects with fake run execution, including existing
controller Pause/Stop, focus, drag/edge switch, hide-app and settings choice/order/reset. Ten platform
edge-handle tests use fake registry storage/nodes/operations to check defaults/upgrades/availability,
own-overlay refusal and takeover exclusion. Exact commands and checks not run are in CURRENT-HANDOFF.md.
This feature has not been installed or exercised on a phone by this worker. Full CI remains the gate.

Round-1 review correction (local-only): panel opening now requests the existing user Pause once and
waits for no in-flight action before showing the guarded window. Closing never resumes or clears
takeover. Final sequential scoped commands in CURRENT-HANDOFF.md both exit 0: platform 2m 19s,
app 5m 34s. JUnit XML totals: platform 111, app 189; 300 tests, zero failures/errors/skips, including
19 edge-handle tests. Added real-overlay/controller regressions cover a due step during paced work
followed by panel Pause/Stop, in-flight settle with one Pause request and no resume on dismissal,
and cancellation of a deferred panel after foreground change/disable. Underlying/panel automation
remains blocked. Repo/branding/provenance, whitespace and changed Kotlin line-length checks pass.
Initial detekt method-size/return-count findings were fixed before the final passing runs.
NOT RUN: all-module/root Gradle checks, untouched-module unit suites, assembleDebug/APK install,
release tasks, device/emulator instrumentation, phone testing or GitHub CI.

Phone checklist (record build commit and actual results before merge):
- Enable EQO accessibility; confirm handle is absent until enabled in Edge handle shortcuts.
- Show handle over Gmail, Chrome and Android Settings; tap/swipe inward, outside dismissal and Back.
- Drag vertically, switch left/right edge, rotate and restart service/app; check saved placement.
- Long press, Hide for this app, switch app, then restore hidden apps in settings.
- Disable/reorder/reset shortcuts; restart app; verify choice/order persist and unavailable controls are absent.
- Ask EQO opens the task request with focus/keyboard and does not plan/run; Open EQO opens home.
- Open the panel during pacing, wait past the next step's due time, select Pause then Stop: no FAILED
  terminal, underlying tap or takeover. Opening requests user Pause; dismissal must not resume.
- Open during an in-flight action: the action settles before the panel appears; Stop stays usable.
- Dragging the collapsed handle must not trigger takeover or pause the run.
- A normal touch outside the collapsed handle during work still triggers takeover; handle never clears it.
- Try an approved agent coordinate tap at the handle and a node/coordinate tap while the panel is open:
  no handle/panel button is activated by the agent, even with another app active underneath.
- Inspect OEM keyboard/full-screen game/video hiding; explanation must match observed limitations.
- Disable handle, revoke/re-enable accessibility and repeat takeover tests; existing touch probe still works.
## Explain screen — t_699c0abc

NOT TESTED ON PHONE. Local-only branch `feat/explain-screen`, base `4a49832`. No provider request or phone action was performed by this worker.

Phone checklist (lead/owner; use synthetic content and do not record real messages or keys):
- Add the Explain screen tile in Quick Settings. From Calculator, explain a formula; ask a formula/image follow-up and check screenshot-vs-text fallback with image-capable and text-only models.
- From Android Settings, tap the tile: shade closes, EQO main UI does not open, the result describes Settings and its main controls. Verify no control is tapped or changed.
- From a Chrome page with ordinary text and a diagram, verify explanation and a typed follow-up refer to the same original screen. Navigate behind the sheet, ask again, and verify no different screen image is sent. Close, reopen and verify the prior session is gone.
- From a synthetic Gmail inbox, explain using both tile and notification. Verify no message is opened or sent, and EQO is not brought to the foreground first.
- Sharing OFF: first-use disclosure appears before any observation/provider request; Close declines. Allow enables sharing explicitly. Switch it off in Setup and verify future requests do not read/upload.
- Check a password field and a very large/deep screen: no password text/image is sent; text-only notice appears when privacy inspection is incomplete.
- Android 13+ notification denied/channel blocked: tile remains usable and no invisible observation occurs. Enable notification, test the action from another app, then disable it and verify it disappears. Test Android 11 and Android 14+ tile entry/background restrictions.
- Read aloud and auto-read: device-language voice, missing voice data, long explanations, Stop on Close; verify keyboard and controls fit at large font size and with TalkBack. Note whether the configured TTS engine uses network synthesis.
- Missing accessibility, missing model/key, offline provider and refused model: plain errors, no raw provider message/key/content in logs. Protected window: permissive policy seam; Android capture refusal falls back to text rather than bypassing FLAG_SECURE.
- Close during an in-flight request, disable accessibility, or open a replacement sheet: no late result reopens it. Rotation/lockscreen/process death must not persist screen context.

Host verification of code commit `96220ce1e6c57f8bad6553231b5e50c2e7906a01` (later documentation-only commit does not change tested code):

Each command ran separately, sequentially from `android/`, with `--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process --console=plain`. Every command exited 0:

| Gradle task | Actual result |
|---|---|
| `:app:ktlintFormat` | BUILD SUCCESSFUL in 2m 38s |
| `:app:ktlintCheck` | BUILD SUCCESSFUL in 55s |
| `:app:detekt` | BUILD SUCCESSFUL in 39s |
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL in 5m 46s; 209 tests, zero failures/errors/skips |
| `:app:lintDebug` | BUILD SUCCESSFUL in 5m 30s |
| `:app:assembleDebug` | BUILD SUCCESSFUL in 4m 52s |

- Explain tests: 15 pure fake-session tests plus 14 Robolectric test executions (seven adapter tests on API 30 and 33), 29 total. Includes text/image choice, sparse/capture fallback, exact model capability, consent OFF, own/System UI roots, passwords/hidden text, privacy bounds, immutable original context, adapter cleanup, late capture/answer rejection, and notification action routing. All model answers/images are explicit synthetic fixtures; no inference request was sent.
- Repo gate: `bash scripts/check.sh`, exit 0, `kt files: 448; provenance rows: 448`, `BRANDING GATE PASSED`, `OK`. `git diff --check` exits 0. Every new Kotlin file is mapped.
- Real APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 59,632,916 bytes; SHA-256 `9c6c1c1c3575087dff5ec292216a71439855f92ad39bed7a450490e50750abbb`. ZIP inspection confirms all four existing helper-starter ABIs are packaged. No install was attempted.
- Ignored local host logs: `android/app/build/reports/explain-screen/`, especially `explain-final-gates.log`. Raw host logs are not committed or uploaded. Initial verification found a nullable image receiver compile error and new-code detekt/lint issues; these were fixed, not baselined. The final complete sequence above passed. The only targeted new lint suppression is the Intent tile-launch overload on OS versions below API 34, where the PendingIntent overload does not exist; API 34+ uses PendingIntent.
- NOT RUN: full-repository Gradle `ktlintCheck detekt test`, other modules' unit-test tasks, release lint/build/unit tests, connected/device instrumentation, live accessibility/screenshot/provider calls, voice integration on the advanced main commit, and every phone checklist item above. Dependency compilation/debug lint analysis performed by app tasks does not count as those modules' unit tests. CI remains the full merge gate; lead/owner owns phone validation and publication.
## Voice input — 2026-10-08 — NOT TESTED ON PHONE

- Branch `feat/voice-input`, card t_e9ef0f95. Mic fills the editable request only; the user must still tap the normal task button. Permission is requested on Mic, not startup. No EQO audio storage or transcript logging.
- Standalone compilation/JUnit of the real presenter: `OK (6 tests)`, exit 0 (denial, absent recognizer, grant/result, empty results, errors/retry, cancelled-session late callbacks).
- Android/Robolectric tests added but NOT RUN. Full Gradle lint/static analysis/unit tests/APK build NOT RUN: other worktrees' Gradle wrappers remained busy after two five-minute waits. Standalone ktlint CLI fallback failed with missing Clikt (exit 1).
- Phone checks remaining: grant/deny/cancel/permanent denial, recognized text review/edit with no task submission, no recognizer explanation, no-match/network/audio errors, device language, leaving the screen stops recognition. No phone was contacted or modified by this task.

Device: Realme RM10, Android 11, serial <DEVICE_SERIAL>. Owner pastes his own OpenRouter key; it is never read or recorded here.
Rule: start `adb logcat -s EqoRun EqoActions` into a file before the owner tests; record each test below with the build commit.

## 2026-10-06

| Time (IST) | Build | Request / action | Result | Evidence | Follow-up |
|---|---|---|---|---|---|
| ~15:43 | PR #74 head 3133f6a (CI APK) | Typed "Open Keep Notes and search for opencode", Plan | "Could not reach the model" twice. Phone network fine (ping openrouter.ai ~57 ms). Cause not found; later plans worked after the owner re-entered the key and re-enabled accessibility. | screenshots, ping | none; likely accessibility/key state after reinstall |
| ~15:48 | 3133f6a | Same request, plan approved | Plan shown correctly (OPEN_APP "Keep Notes", WAIT, TYPE_TEXT "Search your notes", PRESS_ENTER). Step 1 OPEN_APP failed (`execution_failed`) twice, 1.4 s then 0.1 s. Reason not logged in that build. | EqoRun log | added short failure reason to EqoRun (commit c2dd4e4) |
| ~22:11 | PR #79 test build 375590c (#74 + #78) | Same request | Step 1 OPEN_APP succeeded and Keep opened. Step 3 TYPE_TEXT failed: 15 retries "node_found=false", then PartialApply, status FAILED. Real field label is "Search Keep" (EditText, resource id com.google.android.keep:id/toolbar); planner guessed "Search your notes". | EqoRun log | fix: type into the only text input when the hint does not match (commit df7ad13); with several inputs it still refuses to guess |
| ~23:00 | df7ad13 | Owner ran the request; reported typing still failing in search fields | NOT captured (capture started 23:14; phone log rolled over). | none | live capture started |
| 23:28 | df7ad13 | Keep Notes search (captured) | OPEN_APP ok, WAIT ok, TYPE_TEXT found the "Search Keep" EditText and reported action_accepted in 24 ms, then USER_TAKEOVER latched 52 ms later and the run paused. The bar opens a separate search page, so the text never reached the real field. | eqorun-live.log 23:28:00 | fix af0ace9: wait 700 ms, check the focused field, retype into it if empty; 600 ms grace for EQO's own touch signals |
| 23:29 | df7ad13 | SEND_WHATSAPP (captured) | READ_CONTACTS requested; TYPE_TEXT accepted; result UserActionRequired, then takeover latched and status FAILED. | eqorun-live.log 23:29:06-11 | WhatsApp send path needs its own check |
| 23:30 | df7ad13 | Open app + READ_MESSAGES (captured) | Completed. | eqorun-live.log 23:30:03-09 | none |
| 23:31 | df7ad13 | Keep search, second try (captured) | TYPE_TEXT NodeNotFound x15 once (screen state), then on retry accepted again and takeover latched 51-52 ms after, same pattern as 23:28. | eqorun-live.log 23:31, 23:32 | same fix af0ace9 |
| 23:57-23:58 | af0ace9 | Open Google Meet (captured) | `reason=App 'meet' not installed` (correct: Meet is not installed on this phone). | eqorun-live.log | none |
| 01:06-01:25 | af0ace9 | Search boxes in Keep, Gmail, Zepto (captured, owner report) | TYPE_TEXT found an EditText but Android rejected set-text and paste (`ActionRejected`), at 01:06, 01:07, 01:21, 01:25. Typing worked in Flipkart search, a message field and a new-mail compose field. | eqorun-live.log; owner report | commit 3350a47: rejected first attempt now gets the confirm-and-retype pass; log carries widget id and flags |
| 01:09 | af0ace9 | Open a URL | OPEN_URL succeeded, run COMPLETED. | eqorun-live.log | none |
| 01:18-01:20 | af0ace9 | Gmail flow: OPEN_APP, WAIT, READ_EMAILS, then a tap | READ_EMAILS ok; the following tap_text step NodeNotFound x16, run FAILED. | eqorun-live.log | needs the plan text to see the target; planner/step diagnostics |
| 01:22 | af0ace9 | Send email | SEND_EMAIL succeeded (draft opened). | eqorun-live.log | none |
| 01:30 | af0ace9 | "Open calculator and find 2345678 * 8765432" | OPEN_APP, WAIT, CALCULATE all Success. CALCULATE evaluates inside EQO and never types into the Calculator app. Owner reports the Calculator showed "238 * 800", not the requested numbers; the plan text and result line were not captured. | eqorun-live.log | open: find where "238 * 800" came from (plan param vs display); decide whether Calculator should be driven by taps or the answer shown in EQO |
Owner summary 2026-10-07 ~01:30: opening apps, sending SMS/WhatsApp messages and calls work; typing fails in some search boxes (Notes, Gmail, Zepto).
| 02:01-02:03 | af0ace9 (CI) | Keep search via debug plan (no key) | Passed: CLICK_TEXT toolbar, TYPE_TEXT into `search_actionbar_query_text`, PRESS_ENTER, COMPLETED. | eqorun-live.log | debug plan receiver added (debug builds only) |
| 02:03 | af0ace9 | Gmail search via debug plan | First TYPE_TEXT hit `open_search` (ActionRejected), retype landed in `open_search_view_edit_text`; COMPLETED. | eqorun-live.log | none |
| 09:18 | local b360a56 | Zepto search | Search is a Button with a text child, no EditText: tap-then-type fallback worked; COMPLETED. | eqorun-live.log | fix b360a56 + step-budget ordering |
| 09:20 | local | Calculator keys (12 x 3 =) | Passed with real labels (digits, Multiply, Equals, Clear). Earlier "238 x 800" was leftover state in the Calculator, not typed by EQO. | eqorun-live.log; screenshot | planner hints added (fbf4fbb) |
| 11:14-11:17 | 21de750 (#74+#78+#77+#81) | LIST_MACROS, DETECT_ROUTINES, READ_NOTIFICATIONS via debug plan | All three Success (notification content not verified; access not granted). | eqorun-live.log | #81 needs notification-access check |
| 11:52 | 21de750 | BROWSER_NAVIGATE https://example.com, consent allowed, Chrome flag + command-line file set | Chrome launched with the DevTools socket present, but EQO reported "Android did not allow EQO to connect to Chrome's DevTools socket". Consent was first reset by a stray test tap (lead error). | screenshot; /proc/net/unix | #78 not merged as working; needs adb-forward through the helper |
| 12:15 | 21de750 | Wireless pairing (split screen) | PAIR, CONNECT, HELPER_START passed; AUTHORIZE failed (`StepFailed`), BINDER_HEALTH not run. Pairing screen says "Paired and connected"; hub row "Needs attention". Cause: Shizuku.checkSelfPermission never granted, no prompt exists. Pinned-key risk resolved (first connect matches on Realme). | eqorun-live.log 12:31, 12:34 | card t_91b2a02e (helper authorization prompt) |
Merged to main 2026-10-07: #74 (65a884f), #77 (29bad15). Open: #78 parked, #81 awaiting rebase, UX #83 in progress.

## Known failing (as of df7ad13)
- Typing into some apps' search fields (Keep reported by owner after the sole-input fix; cause unknown until captured).
- Contact search inside apps, WhatsApp voice calls, MAKE_CALL permission: not yet fixed.

## Not tested on a phone yet
- Wireless pairing (#77), Chrome control (#78), notifications/macros/routines (#81), voice input.
- Files and attachments (`feat/files-attachments`): All files access row, FIND_FILES / LIST_FILES, `attachment` on email / WhatsApp / SMS, screenshots into `Pictures/EQO`.

## One-step wireless pairing — t_681e8ea6

NOT TESTED ON PHONE. Local-only branch `feat/one-step-pairing`; lead/owner owns device testing and publication.

- Implemented: own-Wi-Fi/own-phone mDNS port discovery; 10-second manual fallback; bounded foreground discovery; EQO RemoteInput pairing-code reply; short numbered guidance and Developer options button.
- Security: existing pinned connect/enrollment path retained. Reply runs only PAIR + CONNECT, never helper start/AUTHORIZE/BINDER_HEALTH or activation completion. Return to EQO and tap Connect again, then a human must tap Allow in EQO's existing protected consent dialog. No accessibility interaction with Settings or reading of its code.
- Verified code commit: `0baad6f5751f849709e20e024ffbf44b7d6ee97a` (base `a647b26f6a79b5e1681ec25088fd2b64ff0d74d8`). Later handoff-only commits do not change the tested code. All changes are committed locally; no push or PR.
- Full host gate from `android/`: `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`, exit **0**. Real output: `BUILD SUCCESSFUL in 12m 36s` and `485 actionable tasks: 189 executed, 296 up-to-date`. JUnit XML totals across nine modules: **961 tests, 0 failures, 0 errors, 1 skipped** (960 passed). The existing skip is `ModelDownloadSchedulingTest`'s pre-Android-Q case, unreachable under minSdk 30. App: 176 tests, no failures/errors/skips. Debug APK exists at `android/app/build/outputs/apk/debug/app-debug.apk` (59,953,858 bytes).
- Focused wireless/source-guard checks: `:app:detekt :app:testDebugUnitTest --tests 'ai.eqo.onboarding.Wireless*Test' --tests 'ai.eqo.study.StudyFlowGateTest' --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`, exit **0**, real output `BUILD SUCCESSFUL in 51s`; 32 tests, no failures/errors/skips. Includes fake NSD discovery/resolve callbacks on API 30/33, actual ten-second Handler timeout and late discovery recovery, wrong-host/wrong-network filtering, lost-service and stale-resolution invalidation, strict code parsing, consumed RemoteInput envelopes, explicit notification routing/stale-token rejection, and a fake runner that throws if helper start/authorization is attempted.
- Repo checks: `bash scripts/check.sh`, exit **0**, real output `kt files: 425; provenance rows: 425`, `BRANDING GATE PASSED`, `OK`. `git diff --check` exits 0. The source guard now permits only the activity and its isolated notification service to construct the pinned runner; it also rejects helper hooks or activation completion in the notification service.
- Verification caveats: the initial build retried a failing Kotlin daemon; using in-process compilation avoided it without changing project configuration. The first full gate hit a timeout in the untouched `AutomationExecutorsTest` macro delete/list case in `actions-android`; one full-gate retry passed all 95 tests in that module without edits. Phone tests and Android device instrumentation were **not run**. Notification permission/channel denial, real mDNS visibility and OEM Settings behavior still need lead/owner confirmation.
- Required phone checks: Android 11 and 13+ local mDNS announcements, reply with leading-zero code while the pairing dialog remains open, success followed by human helper authorization, wrong/expired code, revocation/key mismatch, notification permission denied/channel blocked, wireless debugging off, ten-second discovery timeout with manual ports, service lost/new dialog, Wi-Fi disconnected/switched, screen rotation and five-minute discovery expiry. No real codes, ports or device identifiers belong in this log.
