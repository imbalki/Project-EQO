# Current handoff (any agent can take over from this page)

Last updated: 2026-10-07. Update this file in the same PR as every merge to `main`.

## Goal
EQO Phase One: Android assistant app (OpenDroid base, OpenRouter bring-your-own-key, accessibility automation, wireless-ADB helper, Chrome control, guided setup, approvals, Pause/Stop/takeover). Owner is non-technical: plain language, real command output, say what was not tested.

## Voice input (t_e9ef0f95, local branch `feat/voice-input`)
- Implemented: Mic beside the request box; Android speech recognition in the device language fills only the editable draft. It never submits a task or invokes the planner. The normal task button, preview and approval paths are unchanged. Accessibility, takeover and own-window guards are not changed.
- Microphone permission is requested only after Mic and a plain-language rationale. Denial and missing recognizer leave typing working. Listening/no-match/permission/error states have plain copy. EQO does not save audio or log words; logs contain only start/stop/error class. The rationale discloses that the phone's speech provider may process audio remotely. Recognition is cancelled/destroyed when leaving the screen, and stale callbacks are ignored.
- Verified without Gradle: compiled the actual presenter and its tests with cached Kotlin 2.4.0 and ran JUnit 4.13.2: `OK (6 tests)`, exit 0. Added Android/Robolectric tests for the speech intent, rationale-before-permission, denial, unavailable recognizer, draft-only results and cleanup; these have NOT run yet.
- Verification blocked: other worktrees have running Gradle wrappers. Two five-minute waits both exited 1 (still busy); no concurrent Gradle was started and no other worker process was stopped. Required next check from `android/`: `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug --max-workers=2` (or module-scoped checks if slow). A standalone ktlint CLI attempt also exited 1 because the cached CLI lacks its Clikt dependency; it is NOT a passing lint result.
- NOT TESTED ON PHONE: permission prompts, actual speech accuracy/device language, service absence, provider network failures and lifecycle/background cancellation. Local commits only; lead owns push/PR. Card is not complete until Android checks and review are finished.

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
