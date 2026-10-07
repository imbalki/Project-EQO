# Current handoff (any agent can take over from this page)

Last updated: 2026-10-07. Update this file in the same PR as every merge to `main`.

## Goal
EQO Phase One: Android assistant app (OpenDroid base, OpenRouter bring-your-own-key, accessibility automation, wireless-ADB helper, Chrome control, guided setup, approvals, Pause/Stop/takeover). Owner is non-technical: plain language, real command output, say what was not tested.

## Owner rules (do not break)
- Never commit to `main`; branch + PR; merge only when CI is green and review + security passed; show repo, branch, exact commit, base `main`, versions before merging (`gh pr merge --match-head-commit`).
- Never type, read or ask for API keys. The owner pastes his OpenRouter key into the app himself. Do not read app shared_prefs.
- Never write secure settings on the phone; never `force-stop` the app on the test phone (it switches accessibility off); never touch `com.pocketpalai`; only the SDK `adb`, never kill the adb server.
- No new features beyond porting OpenDroid executors the owner approved; free/open-source tools only.

## State of main and open PRs (check with `gh pr list`)
| PR | What | State |
|---|---|---|
| #74 | Typed requests, foreground runs, typing fixes | MERGED 65a884f |
| #77 | Wireless pairing, enrolled connect-plane key pinning | MERGED 29bad15; hub row stays "Needs attention" until the helper authorization prompt lands (card t_91b2a02e) |
| #78 | Chrome control, socket-owner verification | PARKED: Android 11 refuses an ordinary app's connection to Chrome's DevTools socket; needs the adb-forward route through the helper |
| #81 | Batch 2: notifications, macros, routines + Room | Security PASS; main (#74, #77, #82) merged in, registry total 94, CI green on each merge. Evidence `android/Phase-One/evidence/task-078-port-actions-batch-2.md`, ADR-0005. Needs phone test |
| #83 | UX styling pass | In progress on a Hermes worker (eqo-trial) |
| #79 | Throwaway combined test build | Do not merge |

Debug builds only: broadcast `ai.eqo.debug.PLAN` with `--es plan_b64 <base64 plan json>` hands the task screen a finished plan (still validated and approved on screen) so executors can be tested without a model key. Scripts used: `runplan.sh`, `runreq.sh` in the worktrees folder.

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
