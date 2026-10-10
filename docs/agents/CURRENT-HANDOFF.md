# Current handoff (any agent can take over from this page)

## Screenshot/gallery repair — t_f123214e (local-only `fix-shots`)

- Existing gallery requests use `find:latest,type=screenshot`, dated screenshots use
  `find:type=screenshot,date=YYYY-MM-DD`, and latest photo/picture/PDF/file use the corresponding
  run-time search. eBay bill uses `find:ebay bill`; no invented path or FIND_FILES output binding.
- An explicit capture uses TAKE_SCREENSHOT then `last_screenshot`. The preview says to open the
  intended app first, not EQO. Own-window, protected-screen, takeover and approval gates are unchanged.
- `last_screenshot` prefers an EQO capture no older than one hour (file modification time); missing,
  deleted, stale or future-dated captures fall back to the newest shared-gallery screenshot. Equal
  newest timestamps require choice. Fallback requires the existing foreground exact-name disclosure
  and Continue before staging/compose. Cancel, missing UI and post-wait deletion refuse to send.
- Natural `find:latest` searches still require choice among multiple matches; only the explicitly
  previewed compatibility fallback selects a uniquely newest screenshot. All files access is required
  for gallery lookup, not for a fresh private EQO capture. No new permission or runtime dependency.
- File-only sends can have empty message/body/subject without inventing a caption. Planner and
  registry share the narrow exception; a valid attachment and nonblank recipient remain required.
- Diagnostics no longer equate every screenshot error with missing screenshot permission. Specific
  reasons: no_eqo_screenshot_yet, no_matching_file, needs_all_files_access, eqo_in_foreground,
  protected_screen; unknown capture/copy failures stay unknown. Logs contain only allowlisted kinds.
- Final scoped host gate exits 0: three-module ktlintFormat/check, detekt, debug lint and unit tests
  (actions attachment/storage filters; full core-agent/app suites), max two workers/in-process compiler.
  BUILD SUCCESSFUL in 36m 5s; XML 491 tests, no failures/errors, three Windows real-link skips;
  all three lint XML reports have zero issues. Repo gate passes (487 files/provenance rows).
- NOT TESTED ON PHONE. Fake-provider tests verify supplied model proposals and the prompt contract,
  not real model interpretation. Fake registry tests exercise attachment resolution/disclosure/staging
  and outgoing intents, not actual delivery or real Send buttons. See the test-log checklist.

Last updated: 2026-10-10 (round-1 accepted permission preflight implemented; awaiting review). Update this file in the same PR as every merge to `main`.

Last updated: 2026-10-09 (voice v2). Update this file in the same PR as every merge to `main`.

## Voice v2 (t_e9801e01, local-only branch `feat/voice-v2`)
- Phone remains the default: 4/3/5-second speech-intent pause hints, live partials, Stop listening control,
  preserved early-end text and append-on-next-tap. Providers may ignore pause hints; no automatic restarts.
- Setup: Voice engine (Phone / AI model via OpenRouter), Voice language (device default / en-IN / hi-IN /
  additional tags reported by the phone speech service). AI requires first-use consent before permission/capture.
- AI records a cache WAV (16 kHz mono, 60-second cap), then uses the existing BYOK OpenRouter provider and shared
  planning client. Public model metadata must advertise audio before upload. A text-only model is refused with
  plain guidance and a Phone voice option. Success/error/cancel deletes audio; no words/audio logging or auto-submit.
- Verified local code head `381b083`: app/core-llm ktlintFormat, ktlintCheck, detekt and debug unit tests;
  final saved Gradle run reports BUILD SUCCESSFUL in 9m 48s. XML reports: 486 tests, zero failures/errors,
  one existing core-llm skip; all 34 voice/provider tests pass. Both debug lint reports have zero issues.
  Repo/branding/provenance checks pass (451 Kotlin files, 451 rows); changed Kotlin lines are <=120 chars.
  Resumed-worker confirmation of both modules' static checks, tests and debug lint exited 0 in 28m 3s.
  CI is still the full gate. Design: `docs/adr/0011-voice-input-engines.md`.
- NOT TESTED ON PHONE: long English/Hindi pauses, installed language packs, AudioRecord, provider audio models,
  permission dialogs, airplane mode, 60-second cap and cancellation. See the phone checklist in the test log.
- Local commits only; lead owns push/PR. This card goes to same-card review, not self-completion.
## Explain/handle UX polish (t_e84b3eaa, feat/explain-handle-polish): NOT TESTED ON PHONE

- Compact Explain panel defaults to 25% of screen height; drag/tap the resize bar for a one-line
  collapsed header, medium or large (35% maximum). The body scrolls, with 22sp answer text,
  85%-opaque background, Read aloud, consent and follow-ups retained. Outside touches pass through.
  See screen hides the keyboard and makes the entire panel 5%-opaque and non-touchable for five
  seconds, then restores it. FLAG_SECURE is unchanged; no screen context is logged or persisted.
- Explain screen is the first default edge shortcut on a fresh registry (saved user ordering is
  preserved on upgrade). Accessibility connect/window-state callbacks repost the optional notification
  directly, without trying to start a background foreground service; enabled handles refresh too.
  This repairs a lost notification even if the OEM killed ExplainNotificationService. This is recovery
  on the next observed window change, not a claim that EQO or its service can never be killed.
- Accessibility button declared and routed through the transient Explain entry. API 31+ declares
  isAccessibilityTool. Android 11 QS guidance has three plain steps and an editor hint; API 33+ has
  the platform Add tile confirmation. There is no public direct Android 11 QS-editor intent.
- Setup hub exposes the default-OFF Edge handle switch, accessibility-off wording, shortcut settings
  and a background-running battery-settings row with Realme/ColorOS, Xiaomi and Samsung guidance.
  First successful handle attachment shows “Drag me up or down. Tap to open.” once.
- Owner follow-up: Ask already targets the real request planner with focus and optional voice button;
  its shared layout incorrectly said “Practice run”. That heading now says “Your task”. No sample run
  is started by Ask. Hidden apps settings now show the count and per-package Unhide / Unhide all;
  EQO itself cannot be hidden, including legacy saved own-package choices.
- Local verification: both final sequential scoped commands exit 0 (platform 1m 11s; app 6m 10s):
  `:platform-a11y:ktlintFormat :platform-a11y:ktlintCheck :platform-a11y:detekt :platform-a11y:testDebugUnitTest :platform-a11y:lintDebug`;
  then `:app:ktlintFormat :app:ktlintCheck :app:detekt :app:testDebugUnitTest :app:lintDebug`.
  Both use `--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process --console=plain`.
  JUnit XML: platform 111 + app 251 = 362 tests, zero failures/errors/skips; 22 new API-30/33
  polish cases. Repo/branding/provenance/secret checks, changed Kotlin <=120-character lines,
  changed XML parsing and whitespace checks pass. No new permission or runtime dependency.
  Native hub Switch has a narrowly documented XML lint exception to match platform Activity/StudyTheme.
- Verification corrections: the peek deadline uses a close-cancelled main Handler (distinct name
  avoids Button.handler receiver shadowing). Removed premature attachment-state reconciliation that
  cancelled deferred handle panels; existing paced/in-flight Pause/Stop regressions now pass too.
- NOT RUN: all-module/root Gradle gate, release/assemble APK, device/emulator instrumentation,
  installation, real provider/speech/OEM behavior or GitHub CI. CI remains the full gate; commits local only.
- Phone checklist (record actual results and build commit):
  1. Realme Android 11: switch handle on in Setup, verify immediate drawing, first-use hint, drag and
     Explain shortcut; switch off and confirm disappearance. With accessibility off, verify plain hint.
  2. Explain a real other app twice: open, Close, open; exercise Read aloud and typed follow-up.
     Check default height, all resize states, portrait/landscape, large font, keyboard and body scroll.
  3. Tap See screen: verify background app receives taps inside and outside the faded panel and
     full panel returns after five seconds. Check outside touches pass through normally too.
  4. Enable notification, stop only ExplainNotificationService, change foreground window and verify
     notification returns without starting that service; tap it twice. Deny notification permission
     and switch preference off: it must not reappear. Check accessibility reconnect restores entries.
  5. Add QS tile with the three manual steps; test Android accessibility button and configured
     volume-key shortcut. On Android 13+, confirm Add tile request succeeds or cancels cleanly.
  6. Open background-running row; inspect OEM battery/auto-start choices manually. No new permission.
  7. Ask opens focused real request box with mic if available, never a practice/sample execution.
     Hide another app, unhide it individually, then Unhide all; check count and immediate drawing.
     Verify EQO settings always retain the handle even with a legacy own-package hidden preference.

## Pairing discovery fix (t_9a512691, local-only fix/pairing-discovery)

- Implemented: NSD still finds service instances. One bounded framework resolve attempt is preferred;
  failure or no callback within 1.5 seconds invokes an independent Wi-Fi-interface-bound multicast
  SRV query, then A/AAAA queries for its target. The socket uses mDNS multicast membership and a
  WifiManager multicast lock; retries stop after four seconds. Network changes/close cancel sockets,
  invalidate callbacks and release the lock. Parser limits packets, record counts, names and pointer
  traversal; malformed/truncated/compression-loop replies yield no records. Only this phone's Wi-Fi
  addresses are eligible, including IPv6 scope preservation. Logs contain only resolve method/status.
- Notification Reply is available even with no discovered ports. Both reply and the in-app password
  field accept CODE, CODE PAIRPORT, or CODE PAIRPORT CONNECTPORT, with spaces/commas, six ASCII code
  digits and ports 1024–65535. Explicit ports override discovery; equal ports are refused. Three fields
  require no discovery. Two fields use a known connection port, or wait in memory for up to one minute
  for it; the owner can replace the pending reply with three fields. Waiting never starts PAIR, guesses
  a connection port or reports enrollment. Codes are not saved in view state, logs or durable storage.
- Security: no adb-pairing/authentication changes. Fresh CONNECT enrollment stays bound to its real
  endpoint, PAIR and pinned CONNECT share the existing runner, and background replies never start or
  authorize the helper. Return to EQO and tap Connect again; ADR-0006's own-process human Allow tap
  remains required. Pending replies are discarded on timeout, Wi-Fi revision change, service close,
  Forget and a new foreground pairing/reconnect run (synchronous main-thread cancellation).
- Debug lab: `ai.eqo.debug.PAIR`, only in debug source/manifest, additionally checks BuildConfig.DEBUG
  and requires sender permission android.permission.DUMP. Extras: string `code`, integer `pairing_port`
  and integer `connection_port`. An explicit broadcast to ai.eqo.app/ai.eqo.onboarding.DebugPairReceiver
  forwards validated extras to the same non-exported pairing service; it does not approve the helper.
  Never paste real codes, addresses or ports into docs/logs. Tests cover release source-set exclusion.
- Verified code commit `c8d6d2ad98ee912f57e3ed2bd3fc19e814a7b40b`: sequential app ktlintFormat,
  ktlintCheck/detekt/testDebugUnitTest, then lintDebug/assembleDebug/processReleaseMainManifest all exit 0,
  with `--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`. Final JUnit XML: 255 app tests,
  including 50 pairing regressions, zero failures/errors/skips. Repo/branding/provenance and whitespace/
  changed-Kotlin line-length checks pass; all eight new Kotlin files are mapped (469 files/469 rows).
  Actual merged debug manifest has the DUMP-protected receiver/action; actual merged release manifest
  has neither. Debug APK exists, includes the receiver DEX and all four helper starter ABIs. Exact
  commands, checksum and phone checklist are in PHASE-ONE-TEST-LOG.md. NOT TESTED ON PHONE; full root/
  other-module unit suites, release APK/lint/unit tests and device instrumentation/CI were not run.
  Lead owns install/device checklist, push/PR and the full CI merge gate. Worker requests same-card review.

Last updated: 2026-10-09 (edge handle local handoff added). Update this file in the same PR as every merge to `main`.
Last updated: 2026-10-09 (Explain screen section added). Update this file in the same PR as every merge to `main`.

## IN PROGRESS: files and attachments (branch `feat/files-attachments`, draft PR, do not merge)
Plain-language status, updated after each step. Details and design: `docs/adr/0007-shared-files-and-attachments.md`.
- DONE (code + unit tests with fakes): setup row "All files access"; `FIND_FILES` / `LIST_FILES`; `attachment` parameter on SEND_EMAIL / SEND_WHATSAPP / SEND_SMS (file path or `last_screenshot`); the non-exported share provider and share-intent builder; `TAKE_SCREENSHOT` saves into `Pictures/EQO` and records `last_screenshot` (protected windows still refused); plan preview lists attached file names; staged copies are cleaned up (one hour, or at once on failure); planner vocabulary updated.
- LEFT: phone test on the Realme (see list below). PR #6 is a draft; never merge it. CI (`android`, `android-branding`, `repo-checks`) is green on 3608d0a with `main` b7e8af2 already merged in; merge `main` again before reporting green if it moves.
- NOT TESTED WITHOUT THE PHONE: the real Android "All files access" page; reading a real Downloads folder; a real screenshot being saved and shown in Gallery; Gmail, WhatsApp and Messages actually receiving the file and EQO pressing Send (their button names are guesses from known ids); WhatsApp opening the right chat from the number; a protected screen (bank app) refusing a screenshot; the plan-preview wording on screen.
- NOT RUN IN THIS CLOUD SESSION: the full Gradle build (cloud box cannot reach Google's Maven); CI runs it. Lessons from CI: a constructor's trailing-lambda parameter must stay last; Advanced-category actions must stay out of macros (do not mark file actions READ_ONLY); lint wants KTX `SharedPreferences.edit {}`.

## Fixes for round 1 — t_7a91b291 (local branch `fix/round1-phone-tests`)
- CURRENT: lead approved ADR-0008's pre-run runtime-permission model. Preview lists needed access; missing grants require Allow now before Android's prompt, then the existing plan approval. Android remembers grants. Run service checks only and never requests permission/Settings during execution; revoked access stops with explicit restart instructions. Removed the attempted permission-dialog takeover exception entirely. No permission-result latch clearing, Settings exemption or automatic resume.
- Stop cancels pending preflight and invalidates preparation. Fake/Robolectric regression exercises the actual TaskActivity preview, Location-only request to a typed number, Stop/late Allow, and explicit retry reaching approval without execution. Other tests cover granted access, active-run revocation, stale request codes and 120-second waits. Contacts are only needed for names; inventory also covers flashlight Camera, current-weather Location, direct Calendar insertion and supported phone calls.
- Fresh sequential two-worker checks: actions format/check/detekt + 72 focused tests; platform-a11y format/check/detekt + all 102 tests; app format/check/detekt + all 199 tests. Zero failures/errors/skips in these runs. Repo check passed (445 provenance rows), diff check passed, no added Kotlin lines exceed 120. No new Kotlin files. Core-agent/core-llm were not rerun in this permission rework; their earlier results are historical below.
- REMAINING: independent review, full CI and lead-owned phone checklist. NOT TESTED ON PHONE; no instrumentation, APK/release build, full Gradle gate, push/PR/gh or remote CI. Broad actions' earlier five failures remain disclosed, not baseline-reproduced or claimed fixed. Per-contact channel memory remains follow-up as permitted by the card.

### Historical work/evidence (superseded permission flow; not the current blocker)
- BLOCKED, not complete: independent review found that Android 11 redacts the overlay's cross-UID `ACTION_OUTSIDE` coordinates. The permission-controller bounds exception below does NOT reliably exempt a real Allow tap. No wider takeover exception was introduced. Owner decision requested in proposed [ADR-0008](../adr/0008-runtime-permission-touch-attribution.md): obtain runtime prerequisites before approval/run (recommended, with explicit restart if revoked mid-run), or supply a reviewed trustworthy mid-run touch-attribution design. The current service stays fail-closed; Allow may still pause. This supersedes the earlier implication that round-1 permission takeover was fixed.
- Review rework: `write/type/draft a note in Keep/Notes` can now reach approval with OPEN_APP + known local note-creation/navigation taps + TYPE_TEXT. Communication intent and explicit don't-send requests retain the strict no-tap/no-submit guard; notes plans cannot acquire communication actions, arbitrary IDs/taps or Send/Share/Publish taps. Unknown note controls fail closed. Fake-provider regressions exercise the actual planner and immutable approval snapshot. Fresh core-agent format/check/detekt/all unit tests passed: 110 tests, zero failures/errors/skips; `BUILD SUCCESSFUL in 2m 27s`. Repo/provenance and diff checks passed; no added Kotlin lines exceed 120 characters. Other modules were not rerun in this rework; prior evidence below is historical. NOT TESTED ON PHONE.
- NOT TESTED ON PHONE. Local commits only; lead owns push/PR and CI is the full gate.
- Runtime permissions wait up to 120 seconds without spending the normal apply budget. Stop settles the pending request before dispatch; stale callbacks cannot grant a replacement request. The attempted takeover exclusion requires a pending EQO-launched dialog in an allowlisted system package, verified controls and a touch inside visible bounds. Android 11 redacts the required touch coordinates across UIDs, so this is not an operational fix on that path. Other apps, Settings, unknown windows and coordinate-less touches remain conservative. EQO never presses Allow or clears a takeover latch.
- All files access is shown in the preview and offered on EQO's exact package-scoped Android Settings page. The normal typed-plan path obtains this human-operated prerequisite before a run starts, then continues to the same plan's approval. This avoids exempting Settings from takeover. Execution rechecks access; denial is an honest Needs-you handoff. Mid-run revocation still retains takeover/resume protection.
- Screenshot capability `android:canTakeScreenshot="true"` was already present. No missing flag was invented. Screenshots already save privately without All files access; taking a screenshot of EQO's own approval/task window is deliberately refused, now with actionable copy. Protected-window, takeover and accessibility guards remain intact. `EqoRun` adds only allowlisted failure kinds, never recipient names, numbers or file paths.
- Named notes apps use app tap/type planning, not EQO internal ADD_NOTE. Title-only internal notes use the title as content. Arithmetic words, including percent of, evaluate locally; invalid arithmetic no longer launches web search. WhatsApp `draftOnly=true` opens a prefilled chat and never calls Send; preview says "you press Send". A pre-approval guard forces draft mode and rejects submit/tap/send routes for type/write/draft/don't-send requests. A bare message cannot silently pick SMS/WhatsApp; it asks for the channel once.
- Email recipients split on commas, semicolons and " and "; literals bypass lookup/Contacts permission, names resolve individually, failed recipients are identified before any compose launch, and approved destinations remain frozen. Permission waiting now says "Tap Allow for Contacts/Location" or "Turn on All files access".
- Per-contact channel memory is follow-up: the existing productivity preference boundary is unavailable by default, not a simple working per-contact store. No preference database or runtime dependency was added.
- Verified with sequential, two-worker Gradle module checks (`ktlintFormat`, `ktlintCheck`, `detekt`, `testDebugUnitTest`): actions 71 focused tests passed; core-agent 107 passed; core-llm 272 discovered, one skipped, no failures; platform-a11y 102 passed; app 197 passed (`BUILD SUCCESSFUL in 8m 28s`). On recovery, app format/check/static analysis/test tasks passed again (tests up-to-date), and `scripts/check.sh` passed with all 445 Kotlin provenance rows covered. Added Kotlin lines are all at most 120 characters and `git diff --check` passed. The broad actions retry finished with 146 tests and the same five failures in untouched macro/shared-storage tests (macro timeout, Windows symlink privilege and path behavior); baseline was not separately reproduced. The retry's terminal transport timed out while Gradle continued; its completed JUnit XML, not the transport error, establishes those failures. No phone or instrumentation tests, APK/release build or full Gradle gate have run. CI remains required.

## Files v2 (t_ecfe91de, local branch `feat/files-v2`)
- Implemented: run-time `find:` attachment references with name/type/folder/local-date filters; bounded metadata-only shared-storage search; human chooser (up to eight names, dates and sizes); exact chosen-name run status and Continue/Cancel before staging and opening WhatsApp/email/SMS. One match resolves without a chooser; multiple matches never guess, including `latest`. No foreground UI refuses and lists matches. Existing plan approval, storage exclusions, last_screenshot and staged-copy cleanup retained.
- Local tested code: `add2fab` (includes `8ae3fdd` and `e1cf5a2`), no push or PR. All four touched modules passed ktlintFormat/ktlintCheck/detekt. Core-agent: 108 tests (cached passing results), core-llm: 272 tests (one existing minSdk skip), app: 195 tests, no failures/errors; app debug APK built. Actions full suite: 155 tests, one unchanged macro delete/list 10-second coroutine timeout, three Windows real-symlink capability skips; all 60 file/resolver/OEM/registry tests have no failures/errors. Full actions gate is NOT green. See PHASE-ONE-TEST-LOG.md for exact commands/caveats. Every added Kotlin file has a provenance row; repo/branding gate passes (455 files/rows).
- Operator addition implemented: MediaStore Images/Video/Audio/Downloads/Files metadata; one alias/MIME/extension asset table (modern/legacy messaging media included); first-search per-phone folder/bucket map in local preferences, zero-match refresh and `rescan=true` on demand; camera/gallery/download(s) types. Filesystem fallback also verifies uniqueness. Added synthetic Realme/Samsung/Xiaomi and unknown-OEM-bucket tests. Android/data/obb remain excluded, not bypassed.
- Bare folder aliases search all coexisting locations (Download/Downloads, modern/legacy WhatsApp), retaining ambiguity rather than choosing a folder. Discovery has a global 500-entry/two-second cap. Business/ordinary media folders no longer cross-classify through a generic `Media` leaf. Fake-link exclusion tests run on Windows; actual link tests require host link privileges and must run on CI. Existing task-dialog takeover test updated for the new file-chooser flag; no guard relaxed.
- NOT TESTED ON PHONE: "send my eBay bill to <test contact> on WhatsApp", "email the screenshot from 7 October to me", several matches -> chooser, Cancel/background/timeout, All files access off and excluded/link paths. Lead owns publication, CI and phone tests. The sibling round-1 fixes own missing-All-files-access wording and permission waiting; this card does not duplicate those changes.
## Edge handle (t_a3fa16d0, local-only branch feat/edge-handle)
- Implemented: opt-in accessibility overlay, dynamic feature registry, persisted switches and Up/Down order,
  reset/defaults and per-app hiding/restoration. Built-ins Ask EQO, Pause, Stop and Open EQO; future adapters
  documented only in ADR-0010, not imported from other branches. Ask focuses the existing typed request;
  that screen has no microphone. Pause/Stop use the existing StudyTaskController public controls.
- Safety: own-package guard plus coordinate hitboxes and a panel-open automation refusal; overlay touch
  exclusions compose with the existing task controls and never release takeover. No new resume path,
  SYSTEM_ALERT_WINDOW permission, runtime dependency, screen-content capture or transmission.
- Round-1 review correction: opening the panel requests the existing user Pause and waits for the
  current action to settle before showing the guarded window. This prevents a due action from failing
  the run before Pause/Stop selection. Closing/Back never resumes; settings explains explicit Resume.
- Local verification of code commit `205739d` (sequential module-scoped commands, each exits 0):
  `:platform-a11y:ktlintFormat :platform-a11y:ktlintCheck :platform-a11y:detekt :platform-a11y:testDebugUnitTest :platform-a11y:lintDebug`;
  then `:app:ktlintFormat :app:ktlintCheck :app:detekt :app:testDebugUnitTest :app:lintDebug`.
  Both used `--max-workers=2 -Pkotlin.compiler.execution.strategy=in-process`. Platform: 111 tests; app: 186;
  297 total, zero failures/errors/skips, including 16 edge-handle regressions. Repo/branding/provenance and
  `git diff --check` pass; all new/changed Kotlin lines are at most 120 characters.
- Review-correction verification: repeated both exact module commands above against the final code,
  sequentially with the same worker/compiler flags; platform BUILD SUCCESSFUL in 2m 19s,
  app BUILD SUCCESSFUL in 5m 34s. JUnit XML: platform 111, app 189; 300 tests, zero failures/errors/skips,
  including 19 edge-handle tests. Three new app regressions cover real panel opening during paced work,
  due-step/Pause/Stop without automation, in-flight settle/one-shot Pause/dismissal without resume,
  and pending-open cancellation on foreground change/disable. Repo/branding/provenance, diff whitespace
  and changed Kotlin file line-length checks pass (450 tracked Kotlin files, 450 provenance rows).
  Initial method-size/return-count detekt findings were corrected; only the single integrated
  scheduling scenario has a documented method-scoped LongMethod annotation. No new Kotlin files.
- NOT RUN: root/all-module Gradle `ktlintCheck detekt test`, unit tests of untouched modules,
  `assembleDebug`/APK install, release tasks, device/emulator instrumentation execution or GitHub CI.
  Initial detekt findings were fixed. Android lint's physical LEFT/RIGHT docking warnings are intentionally
  annotated only on two methods (ADR-0010); no lint baseline or global suppression was added.
- NOT TESTED ON PHONE. Checklist in PHASE-ONE-TEST-LOG.md covers Gmail/Chrome/Settings, drag/edge switch,
  Back/outside, hidden apps, Pause during a run, agent-tap refusal and OEM keyboard/full-screen behavior.
  Lead pushes/opens the PR; this worker commits locally only. CI is the full gate.
## Explain screen (t_699c0abc, local branch `feat/explain-screen`)

- Implemented separately from automation: Quick Settings tile and optional ongoing-notification action; transient entry finishes before screen reading; bounded active-app text extraction with password/own-window exclusion; sparse/visual-question screenshot fallback through the existing in-memory accessibility JPEG primitive; exact-model image-capability check from the cached public catalog; large translucent accessibility overlay; read-aloud/auto-read; typed follow-ups that retain only the session's original screen context.
- Screen-sharing consent is OFF by default, with first-use disclosure and an optional settings screen in Setup. No shared vision-locate consent exists on inspected origin/main `4a49832`. Before integration, if vision-locate has landed, replace the separate consent with its shared setting. ADR-0009 documents the permissive `ScreenProtectionPolicy` seam (next phase: protected-screen setting). No action/approval/takeover/own-window guard logic changed.
- Remote main was rechecked and fetched during verification: `92802e7` adds draft-only voice input, not vision-locate consent. This branch retains its original base `4a49832`; the lead owns integration with the voice commit. A voice-drafted screen question, once submitted through the same task input, receives the shortcut guidance; the explanation sheet itself does not depend on voice input. Its controls scroll when space is limited, and follow-ups can also submit from the keyboard Send action. The input requests no personalized IME learning using the platform flag; the device keyboard must respect it.
- Verified code commit `96220ce`: app-scoped `ktlintFormat`, `ktlintCheck`, `detekt`, `testDebugUnitTest`, `lintDebug` and `assembleDebug` all exit 0 (run one at a time, max two workers); 209 app tests, including 29 Explain executions, zero failures/errors/skips. Repo branding/provenance gate passes (448 Kotlin paths/rows); real debug APK built. Exact commands, output and APK checksum are in PHASE-ONE-TEST-LOG.md.
- NOT TESTED ON PHONE: tile/shade entry on Android 11/13/14+, notification permission/channel behavior, live screen capture/model answers, overlay/keyboard layout, TTS and device language. Full-repository Gradle checks, other modules' unit tests, release checks and device instrumentation were not run. See the dedicated phone checklist in PHASE-ONE-TEST-LOG.md. The lead publishes; no push/PR by this worker. CI remains the full merge gate.

## Goal
EQO Phase One: Android assistant app (OpenDroid base, OpenRouter bring-your-own-key, accessibility automation, wireless-ADB helper, Chrome control, guided setup, approvals, Pause/Stop/takeover). Owner is non-technical: plain language, real command output, say what was not tested.

## Voice input (t_e9ef0f95, local branch `feat/voice-input`)
- Implemented: Mic beside the request box; Android speech recognition in the device language fills only the editable draft. It never submits a task or invokes the planner. The normal task button, preview and approval paths are unchanged. Accessibility, takeover and own-window guards are not changed.
- Microphone permission is requested only after Mic and a plain-language rationale. Denial and missing recognizer leave typing working. Listening/no-match/permission/error states have plain copy. EQO does not save audio or log words; logs contain only start/stop/error class. The rationale discloses that the phone's speech provider may process audio remotely. Recognition is cancelled/destroyed when leaving the screen, and stale callbacks are ignored.
- Verified without Gradle: compiled the actual presenter and its tests with cached Kotlin 2.4.0 and ran JUnit 4.13.2: `OK (6 tests)`, exit 0. Added Android/Robolectric tests for the speech intent, rationale-before-permission, denial, unavailable recognizer, draft-only results and cleanup; these have NOT run yet.
- Verification blocked: other worktrees have running Gradle wrappers. Two five-minute waits both exited 1 (still busy); no concurrent Gradle was started and no other worker process was stopped. Required next check from `android/`: `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug --max-workers=2` (or module-scoped checks if slow). A standalone ktlint CLI attempt also exited 1 because the cached CLI lacks its Clikt dependency; it is NOT a passing lint result.
- NOT TESTED ON PHONE: permission prompts, actual speech accuracy/device language, service absence, provider network failures and lifecycle/background cancellation. Local commits only; lead owns push/PR. Card is not complete until Android checks and review are finished.
## One-step wireless pairing (t_681e8ea6, local branch `feat/one-step-pairing`)
- Opening the wireless setup screen starts bounded (five-minute) mDNS discovery of `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp`. API 33+ scopes discovery to the Wi-Fi Network; API 30–32 starts discovery only when Wi-Fi is the default route (not cellular or VPN). All versions accept only resolved addresses belonging to this phone's Wi-Fi interface, never another device on the subnet. Network/address changes and lost services invalidate discovered ports. Manual pairing/connection port boxes appear after 10 seconds when discovery is incomplete; discovery keeps listening for late results.
- Tap Find ports to enable notifications on Android 13+. Open Developer options, turn on Wireless debugging and open Pair device with pairing code. Reply to the EQO notification with the six-digit code while that dialog stays open. If notifications are blocked, use the in-app code field in split screen. Codes are neither saved in view state nor forwarded to an activity; the reply envelope is consumed.
- The reply service performs only PAIR and CONNECT through the existing pinned/enrollment-owning runner. It never starts the helper, authorizes it, or marks activation complete. Return to EQO and tap Connect again to run the remaining checks; AUTHORIZE is still the existing EQO-owned, own-process AlertDialog with a real human Allow tap (ADR-0006). There is no Settings accessibility automation, screen reading, code/key/port logging, new runtime dependency, or automatic consent.
- Verified code commit `0baad6f5751f849709e20e024ffbf44b7d6ee97a`: full `ktlintCheck detekt testDebugUnitTest assembleDebug --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process` exits 0; 961 tests (960 passed, one existing minSdk skip), zero failures/errors. Repo/branding/provenance checks also exit 0. See `PHASE-ONE-TEST-LOG.md` for exact output and the transient macro-test timeout that passed on retry. NOT TESTED ON PHONE. Lead/owner must verify local-phone NSD on Android 11 and 13+, notification reply while Settings is foreground, notification denial fallback, Wi-Fi loss, discovery timeout, re-pair, pinned-key rejection and the human helper prompt before merging. No push/PR by this worker.

## Owner rules (do not break)
- Never commit to `main`; branch + PR; merge only when CI is green and review + security passed; show repo, branch, exact commit, base `main`, versions before merging (`gh pr merge --match-head-commit`).
- Never type, read or ask for API keys. The owner pastes his OpenRouter key into the app himself. Do not read app shared_prefs.
- Never write secure settings on the phone; never `force-stop` the app on the test phone (it switches accessibility off); never touch `com.pocketpalai`; only the SDK `adb`, never kill the adb server.
- No new features beyond porting OpenDroid executors the owner approved; free/open-source tools only.

## State of main and open PRs (check with `gh pr list`)
Merged to main on 2026-10-07: #74 typed requests, #77 wireless pairing, #81 batch 2 (macros/routines/notifications), #84/#85 tap fixes, #82 docs.

| PR | What | State |
|---|---|---|
| #86 | UX visual pass (styling only, supersedes #83) | CI running; installed on the test phone; merge when green |
| #87 | SMS send button id, longer model-call timeouts (OkHttp 10 s default was timing out), planner error class in log | CI running; verified on phone; merge when green |
| #88 | Helper authorization prompt (fixes AUTHORIZE step, ADR 0006) | Draft; security review card t_1b5c2aa0 running; needs phone test on the pairing screen |
| #78 | Chrome control | PARKED: Android 11 refuses an ordinary app's connection to Chrome's DevTools socket; needs the adb-forward route through the helper after #88 |
| #79 | Throwaway combined test build | Do not merge |

## Share contact / share location (branch `feat/share-contact-location`, draft PR)
- Done: two new actions, `SHARE_CONTACT(contact, to, via)` and `SHARE_LOCATION(to, via)`, `via` = whatsapp, sms or email. They reuse the existing WhatsApp, SMS and email routes and the contact resolver. Contacts permission and precise-location permission are asked only when the step runs. The plan screen says plainly what is sent ("the saved phone number of X", "your current location"). Several matching contacts: the step fails and lists the names, it never guesses. Location is never stored or logged.
- Added: `ShareActions.kt` (+ test), schema entries, planner wording, preview wording.
- Not done / not testable here: this cloud session could not reach the Android build servers, so ktlint, detekt, the unit tests and the app build were NOT run. Run `./gradlew ktlintCheck detekt test` for `core-llm`, `core-agent`, `actions-android` first.
- Not testable without the phone: the real permission prompts, a real GPS fix, WhatsApp "Send" press, SMS and email drafts opening.
- Expect merge conflicts with `feat/files-attachments` in `ActionSchema`, `AndroidActionRegistry` and the planner vocabulary; the registry test counts enabled actions (now 96).

## Hermes workers (GitHub login is unreliable for dispatcher-started workers)
Workers started by the Hermes gateway failed twice with "no GitHub login" (12:44, 15:36) although a worker started from a normal shell is logged in as imbalki. Do not copy tokens around. Pattern that works: create a git worktree under C:\Users\<user>\Claude\worktrees\ for each card from origin/main, tell the worker to COMMIT LOCALLY ONLY, then the lead pushes the branch and opens the PR. Security reviewers read a saved diff file plus the worktree. Fallback chain for every profile: DeepSeek v4.1 Flash then GLM 5.3 Flash. Dev profile eqo-trial = GPT 6.1 Sol.
In flight (cards): contacts by name (t_212ea16c), WHATSAPP_CALL action (t_d3c58c41), plain run status (t_4fa4cbd6), exit review doc (t_e8434d96), security review of #88 (t_1b5c2aa0).

## Phone testing without a model key
Debug builds only: `adb shell am broadcast -a ai.eqo.debug.PLAN --es plan_b64 <base64 plan json>` hands the task screen a finished plan (validated, approved on screen). Scripts in C:\Users\<user>\Claude\worktrees: `runplan.sh` (plan JSON), `runreq2.sh` (typed request through the real model). Always start `adb logcat -s EqoRun EqoActions >> eqorun-live.log` first (the capture dies when the USB link resets; restart it) and record results in docs/agents/PHASE-ONE-TEST-LOG.md. Never type or read the owner's API key; the owner pastes it. Locally built debug APKs (laptop key) update in place and keep app data; CI-built APKs need uninstall (different key).
Known behaviours: the Plan step could time out at 10 s (fixed in #87); WhatsApp reopens in the last chat so search-based plans for it are unreliable (card t_d3c58c41); a run is shown as FAILED when an action hands control back on purpose (card t_4fa4cbd6); contact names need Contacts permission and the screen copy saying EQO cannot read contacts is stale (card t_212ea16c).

## Verification model
GitHub CI is the merge gate: jobs `android`, `android-branding`, `repo-checks`. CI uploads the debug APK as artifact `eqo-debug-apk` (`gh run download <run> -n eqo-debug-apk`). Local full builds are slow; use `./gradlew :app:assembleDebug` only for phone installs. CI-built and locally built debug APKs are signed with different keys: switching between them requires uninstall (owner must re-enter his key and re-enable accessibility).

## Test phone
Realme RM10, Android 11, serial `<DEVICE_SERIAL>`, over USB. Wake it with `KEYCODE_WAKEUP`. Use `adb exec-out screencap -p`, not `uiautomator dump`, during runs. Logs: `adb logcat -s EqoRun`. Typed-request screen: MainActivity, "Try the sample task".

## Work left (priority order)
1. Typed requests working across apps on the phone (fix `OPEN_APP` refusal; contact search inside apps; WhatsApp voice calls and call permission).
2. Merge #77 and #78 after phone tests.
3. Batch 2 ports: notifications, macros, routines with the Room database — PR open on `feat/batch2-notifications-macros-routines` (evidence `android/Phase-One/evidence/task-078-port-actions-batch-2.md`, ADR-0005); needs owner review and a phone test. Batch 3 (transport, shopping, social) is skipped by the owner.
4. UX/visual pass: styling only (theme, spacing, buttons, home/setup/plan-approval screens), no behaviour changes. Start after #74 merges to avoid conflicts.
5. Android 12/13 emulator checks (AVDs `eqo-api31`, `eqo-api33`), exit review (TASK-016), voice input and web-search research, release items (signing key, weather privacy note).

## Taking over
- Hermes board: `hermes kanban --board eqo-android` (always `dispatch --dry-run` first; dispatch is blanket; check each card's `model:` line).
- Read `AGENTS.md`, `android/Phase-One/DECISIONS.md`, `docs/agents/PHASE-ONE-STATUS.md`.
- Every Kotlin file needs a row in `android/Phase-One/evidence/task-005-provenance-map.md` (branding gate).
- Run `./gradlew ktlintCheck detekt test` for touched modules before pushing; ktlint and detekt both gate CI.

## Self-contained prompts for cloud/Codex sessions
Start a session on `imbalki/Project-EQO`, paste the block, nothing else needed.

### Batch 2: notifications, macros, routines
Read `docs/agents/CURRENT-HANDOFF.md` and `AGENTS.md`. Branch from `main` as `feat/batch2-notifications-macros-routines`. Port the OpenDroid notification, macro and routine executors into `actions-android` following how batch 1 was ported (see `AndroidActionRegistry`, `ActionSchema`, provenance rows, `RegistryPlanVocabulary`). Bring back the Room database they need. No new features. Add unit tests, keep ktlint/detekt/lint green, open a PR, never merge it. Report what could not be tested without the phone.

### UX pass (styling only)
Read `docs/agents/CURRENT-HANDOFF.md`, `docs/agents/UX-REVIEW-STUDY-APP.md` and `AGENTS.md`. Start only after PR #74 is merged. Branch `feat/ux-visual-pass`. Restyle the home, setup hub, model/key setup and task/plan-approval screens (theme, spacing, button and card styles, readable text). Platform widgets only (the app has no AppCompat). No behaviour or string-meaning changes; keep accessibility labels and 48dp touch targets. Keep CI green, open a PR, never merge it.
