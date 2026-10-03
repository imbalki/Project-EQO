# Phase One status and handoff (runbook for ANY agent)

Last updated: 2026-10-03 about 23:40 local. TASK-001 to 007, 009, 012 and 014 are merged. Next: TASK-008 (wireless ADB pairing) and TASK-010 (Chrome CDP) are the remaining device spikes, then TASK-015 (study APK) and TASK-016 (exit review). Update this page in the PR that finishes each task. It is written so that a person or **any** AI agent (Claude Code, Codex, Hermes, anything that can run a shell, `git`, `gh` and `hermes`) can continue the work from the repo alone, with no knowledge held only by a previous chat.

## 0. Read this first

- **Project:** EQO is a privacy-first personal AI assistant for Android (Kotlin). **Phase One** is a sideloaded study APK: OpenDroid base + OpenRouter bring-your-own-key + accessibility automation + wireless ADB + an EQO-branded privileged helper + Chrome CDP + guided onboarding + approvals, Pause/Stop/takeover. Scope and decisions: `android/Phase-One/DECISIONS.md`, task specs: `agents/android/tasks/` (`INDEX.md`).
- **Hard rules** (`AGENTS.md`, do not break them): never commit to `main`; one task = one issue = one branch = one PR; Conventional Commits (`type(scope): summary (Refs #N)`); no secrets in commits; only free and open-source tools; announce before irreversible or external actions; CI must be green.
- **The owner is non-technical.** Explain in plain language, quote real command output and exit codes, say plainly what was NOT tested, never invent numbers or results. Make engineering decisions yourself and report what you did and why. **Ask the owner only for:** merges that are not pre-approved, anything that uses their phone, spend or model-plan changes, and real product or legal decisions. Never silently change a model, option or scope the owner chose.
- **Never use the owner's personal Telegram Hermes profiles** (`boltzy99`, `hulk369`, `sweety99`). Use the `eqo-*` profiles only.
- **Nothing in this repo or its chats is a secret store.** Credentials live in the tools' own stores (see section 2). Never print or commit keys.

## 1. How to resume in a new chat (paste this)

Open the agent in the `project-eqo` folder (so `AGENTS.md` loads) and paste:

> Continue EQO Phase One. Read `docs/agents/PHASE-ONE-STATUS.md` fully, then run `hermes kanban --board eqo-android stats` and `list`, `gh pr list --repo imbalki/project-eqo` and `gh issue list --repo imbalki/project-eqo`. Give me a plain-language status: done, running, waiting on me, with a progress percentage and the expected time to the first usable build. Be proactive: decide engineering questions yourself and tell me what you did and why; ask me only for merges that are not pre-approved, anything that uses my phone, spend or model-plan changes, and real product or legal decisions. Verify every worker's claims yourself before a review starts. When something is ready to merge, run the merge command so I can approve it, and remind me when you are waiting on me.

## 2. Tools and accounts (what must work before you start)

| Need | Where / how to check |
|---|---|
| Repo | `C:\Users\<user>\Claude\project-eqo` (main checkout), remote `https://github.com/imbalki/Project-EQO` (use `--repo imbalki/project-eqo` with `gh`). `gh auth status` must show the owner's account |
| Worktrees | one per task under `C:\Users\<user>\Claude\worktrees\` (`git worktree add -b <branch> ../worktrees/<name> origin/main`) |
| Shells | Git Bash for scripts; PowerShell **7** (`C:\Program Files\PowerShell\7\pwsh.exe`), not Windows PowerShell 5 (it blocks scripts) |
| JDK / Android | `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`, `ANDROID_HOME=C:\Users\<user>\Android\Sdk` (platform 36, build-tools 36.0.0, NDK 29.0.14206865, CMake 3.31.6), Gradle wrapper in `android/` (Gradle 9.7.0, AGP 9.3.1, Kotlin 2.4.0) |
| adb | only the SDK one: `C:\Users\<user>\Android\Sdk\platform-tools\adb.exe` (a second `adb.exe` on PATH makes the phone show offline). Never `adb kill-server` |
| Hermes (agent workers) | `hermes` CLI; board `eqo-android`; dashboard `hermes dashboard --no-open` then http://127.0.0.1:9119/kanban. Logins: `hermes auth list` (`openai-codex` OAuth, `xiaomi` MiMo API, `opencode-go`, `opencode-zen`, `openrouter`, `copilot`, `huggingface`) |
| Upstream study clone | `C:\Users\<user>\Claude\worktrees\_upstream\opendroid` at `6ff5a061` (read-only reference) |
| Status line (optional) | `pwsh -NoProfile -File C:\Users\<user>\Claude\worktrees\status-line.ps1` prints board counts, running cards, PR CI and phone state |

If Hermes is unavailable, do each task yourself by following its spec in `agents/android/tasks/` and the verification routine in section 6: the acceptance criteria are in the spec, the process is the same.

## 3. Where everything lives

| What | Where |
|---|---|
| Android code | `android/` (`:app`, `:core-security`, `:core-llm`, `:core-agent`, `:platform-a11y`; package `ai.eqo`; minSdk 30) |
| Documents and evidence | `android/Phase-One/` (`DECISIONS.md`, `docs/`, `evidence/`, `design/`) |
| Task specs and index | `agents/android/tasks/` (`INDEX.md` is the plan, this page is the live truth) |
| Decisions | `android/Phase-One/DECISIONS.md` (D-001 to D-010) and `docs/adr/` (0001 to 0004) |
| Model routing | `docs/agents/MODEL-ROUTING.md` (updated 2026-10-03 to match section 4) |
| Branding, provenance, licences | `scripts/check-branding.sh` (CI), `NOTICE`, `LICENSES/`, `android/Phase-One/evidence/task-005-provenance-map.md` |
| Per-task evidence | `android/Phase-One/evidence/task-0NN-*.md` (every claim in them must come from a command that was run) |

## 4. Decisions in force

- **Android 11 floor (minSdk 30)** (D-007, ADR-0003). **No LiquidAI Leap SDK** anywhere (CI grep).
- OpenRouter bring-your-own-key is the primary model path (D-001). App connectors are Phase 2 (D-002). Bundled Python bridge stays inert (D-005); bridge and Termux code are not in the EQO tree yet.
- Module layout A (ADR-0004): `:core-security` <- `:core-llm` <- `:core-agent` <- `:platform-a11y`, boundaries provisional.
- Visual identity: Warm (clay); animations Phase 2.
- **D-008 (owner, 2026-10-03): the virtual display (TASK-011, gate 6) is postponed to the next stage.** Helper (007) and wireless pairing (008) stay. TASK-015 and TASK-016 do not wait for it.
- **D-009 (owner, 2026-10-03): local Gemma (TASK-013) is parked;** the owner tests with OpenRouter. PocketPal AI on the phone cannot be bridged (no API, private storage).
- **D-010 (owner, 2026-10-03): no hard-block list for banking, authenticator and crypto-wallet apps for now.** TASK-012 stays structured so one can be added.
- **Model routing (owner, 2026-10-03, after the N1 A/B trial):** new substantial authoring runs on **GPT 6.1 Sol** through the owner's OpenAI login (Hermes profile `eqo-trial`, provider `openai-codex`; model id `gpt-6.1-sol`, the names `gpt-6.1` and `6.1-sol` are rejected). Fallbacks on the same profile: MiMo 2.6 Pro (`xiaomi`) then MiMo 2.6 Pro (`opencode-go`); the chain was verified by breaking each link and reading the usage file. The `eqo-core-dev` profile (MiMo 2.6 Pro, `xiaomi`) still runs cards created under the earlier routing. Security passes run on MiMo 2.6 Pro (`eqo-security`). The first independent review is by the other model family (author Sol/OpenAI -> reviewer GLM 5.3 Flash on `opencode-go`, profile `eqo-reviewer-glm`). Docs, summaries and copy run on DeepSeek V4.1 Flash (`eqo-docs`). **Trial result:** the same N1 task (shape-based redaction, issue #44) given to both workers — GPT 6.1 Sol finished in 49 min (card `t_3c04decf`, started 10:19, completed 11:08 local; the worker reported start 10:20:30 / finish 11:07:09) and reported 269 `:core-llm` tests green; the MiMo 2.6 Pro baseline (card `t_5fde84fb`) was stopped by the owner at 109 min unfinished (reclaimed 12:08). No PR was opened from either branch. Full detail: `docs/agents/MODEL-ROUTING.md`.
- **Merge policy (owner, 2026-10-03):** the owner approves merges. **Standing approval:** a PR the owner asked for may be merged by the lead once the independent review says PASS, the security pass says PASS where the task requires one, the lead's own fresh-clone build passes, and CI is green on the final head — with no open owner question. Everything else still needs the owner's explicit approval. The earlier one-off pre-approval (TASK-005, 006, 014, 009) is used up.
- Open owner decisions: the Shizuku trademark/naming question before any public distribution (`NOTICE` flags it and claims nothing).

## 5. Task progress (16 tasks)

| Task | Issue | State | Evidence |
|---|---|---|---|
| 001 toolchain | #33 | **Done** (PR #22) | `evidence/toolchain*.txt` |
| 002 upstream build and lint | #34 | **Done** (PR #30) | `evidence/task-002-*` |
| 003 skeleton and CI | #35 | **Done** (PR #32) | `evidence/task-003-skeleton-ci.md` |
| 004 module extraction | #36 | **Done** (PR #41, `a567321`) | `evidence/task-004-extraction-map.md`, ADR-0004 |
| 005 identity, branding, NOTICE | #10 | **Done** (PR #43, `97fc72b`) | `evidence/task-005-*` |
| 006 OpenRouter key, redaction | #11 | **Done with a known gap** (PR #46, `edf9403`): acceptance criterion 5 (cost disclosure before first use) is NOT MET; redactor and crash hook have no production callers yet. Follow-ups: issue #44 | `evidence/task-006-byok-security.md` |
| 007 helper spike (Shizuku-derived) | #12 | **Done** (PR #51, `87c8223`): helper runs under EQO's app id, survives binder death and revocation, no separate helper app. Security pass PASS with SF-1 to fix before release/S2 (section 11) | `evidence/task-007-helper-spike.md` |
| 008 wireless ADB pairing | #13 | **Next** (TASK-007 is done; needs the phone) | |
| 009 one accessibility service, takeover | #14 | **Done** (PR #49, `70b26e0`): criteria 1 to 4 pass on the physical phone, criterion 5 not tested. Security follow-ups: issue #50 | `evidence/task-009-accessibility.md` |
| 010 Chrome CDP spike | #15 | Waiting on 008 | |
| 011 virtual display | #16 | **Deferred (D-008)** | |
| 012 action loop, Pause/Stop/takeover | #17 | **Done** (PR #52, `f84e079`): the loop, states and race tests are implemented; carries issue #50 (SF-1 to SF-4 and N-3 landed here). **Nothing constructs `ActionLoop` in `main`/`:app` yet** (TASK-015 wiring) | `evidence/task-012-action-loop.md` |
| 013 local Gemma | #18 | **Parked (D-009)** | |
| 014 SMS compose-only, permissions | #19 | **Done** (PR #45, `9120e1d`) | `evidence/task-014-sms-permissions.md` |
| 015 study APK and onboarding | #20 | Waiting (needs 008 and 010 done; 007, 012, 014 done). Also carries issues #44 and #50; wires `ActionLoop` into the app | |
| 016 device matrix and exit review | #21 | Waiting on 015; gate 6 is recorded as deferred | |

Issue numbers: TASK-001 to 004 were #6 to #9, moved by an interrupted command and moved back (#33 to #36). Commit messages from before say `Refs #6/#7/#8`: read as #33/#34/#35.

Pull requests: merged #2 to #5, #22, #26, #27, #29 to #32, #38, #40, #41, #43, #45, #46, #48, #49, #51, #52. None open. Open issues to know: #25 (UX), #44 (TASK-015 follow-ups from the TASK-006 security pass), #47 (this documentation refresh), #50 (TASK-012 follow-ups from the TASK-009 security pass; SF-1 to SF-4 and N-3 landed in PR #52). #42 is closed (superseded by D-010 and the TASK-012 brief).

## 6. The operating loop (do exactly this for every task)

The lead (you) drives; workers are Hermes cards. A task is a chain of cards: **author -> independent review (the other model family) -> security pass (tasks 006, 007, 008, 009, 012) -> docs**; phone tasks also have a blocked "OWNER: connect physical phone and confirm" card that you complete only when the task's real predecessors are done.

1. **Release a task:** complete its phone card (`hermes kanban --board eqo-android complete <id> --summary "..."`) when predecessors are done and the owner is present/phone connected (`adb devices` shows `device`).
2. **Always dry-run before dispatching:** `hermes kanban --board eqo-android dispatch --dry-run`, check the spawn list is exactly what you intend, then `... dispatch`. Dispatch is blanket: it starts every ready card and also restarts author cards sitting in `review`. Hold cards with `block <id> "reason"`; cards in `review` cannot be blocked (complete them after verifying).
3. **Brief every worker** with a comment (`comment <id> "..."` or `create ... --body-file`) that contains: the task spec path, the phone rule, the memory flags (below), branch from current `origin/main`, evidence rules, "every number, file name, SHA and test name must come from a command you ran", push before the 2-hour limit, heartbeat every 10 minutes.
4. **Watch heartbeats, not just status** (`hermes kanban --board eqo-android show <id>`). A worker silent for 30+ minutes is stalled: `reclaim <id>`, re-brief ("read your worktree first, do not start over"), dispatch again. If a worker is about to hit the 2-hour limit with unpushed local commits, push its branch yourself (normal fast-forward push).
5. **VERIFY before the review starts, with your own commands** (never trust the worker's text): (a) pushed head is based on current `origin/main`; (b) fresh clone of the pushed branch and run the full gate from `android/`: `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m` (about 15 to 30 minutes), quote the BUILD line, exit code and test counts rolled from `*/build/test-results/testDebugUnitTest/*.xml` by script; (c) `bash scripts/check-branding.sh` and `bash scripts/check.sh` exit 0; (d) no-Leap grep clean; (e) spot-check the claims in the evidence doc against git and the XML results.
6. **Review and security cards:** brief them with a focus list, tell them to work in a scratch clone and run no Gradle if possible (the lead's build is the gate), and to end with an explicit `VERDICT: PASS` or `VERDICT: CHANGES REQUESTED`. A reviewer that requests changes completes its card with the findings in an attachment; **you** create the fix card with the task's author profile as assignee (`eqo-trial` for new work), dispatch it, and **verify the fix yourself** — there is no second re-review (see the process rules below).
7. **Branch and PR:** after a re-base onto a renamed or updated main, create a backup branch, `git rebase --onto origin/main <old-base> <branch>`, prove it mechanical by comparing the changed lines before and after (normalise renames), then `git push --force-with-lease` your OWN branch only, after announcing it. Open the PR with a body that states what changed, the real verification outputs, and what is NOT verified. Mark draft until phone records are complete.
8. **Merge:** only when the independent review and the security pass (where required) say PASS, your own fresh-clone gate passed, CI is green on the final head, and the merge is approved (section 4 — standing approval, or the owner's explicit approval). Squash merge: `gh pr merge <n> --repo imbalki/project-eqo --squash --subject "<type>(android): <summary> (TASK-0NN) (#n)" --body "<summary>"`. Then release the task's docs card and update this page in a docs PR.
9. **Record follow-ups** as GitHub issues, never only in chat.

**Process rules from the owner (2026-10-03):**

- The lead verifies every worker's claims **in a fresh clone** — never the worker's tree, never the worker's text.
- **No second re-review after fixes.** If a reviewer requests changes, the author fixes them and the lead verifies the fix; the reviewer is not run again for the same task.
- **Pre-action check before any merge:** confirm the repo, the branch, the `sha` and the tool versions with your own commands first.
- **Standing merge approval:** a PR the owner asked for may be merged by the lead once the independent review says PASS, the security pass says PASS where the task requires one, the lead's own fresh-clone build passes, and CI is green on the final head — with no open owner question. Everything else still needs the owner's explicit approval.
- **Owner away: phone tests queue for his return.** Do not start a phone step while he is away; keep it queued and pick it up when he is back.

**Memory flags (the machine has 15.7 GB and several workers):** one Gradle invocation at a time, `--max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m`. "Failed to load jvm.dll" or "Gradle build daemon disappeared" is a memory crash, not a code failure: rerun.

## 7. In flight right now (check with `hermes kanban --board eqo-android list --status running`)

Nothing is running on the board as of this update. **TASK-007** (PR #51) and **TASK-012** (PR #52) are merged, and the two-worker model trial is finished (result in section 4); its two branches (`agent/android/44-shape-redaction-sol`, `agent/android/44-shape-redaction-mimo`) still exist on origin with no PR opened from either.

**TASK-008** (wireless ADB pairing) is the next device spike. It, and any other phone test (device scenarios, TASK-009-style accessibility records), wait until the owner is back: **owner away — phone tests queue for his return.** Only one worker may use the phone at a time.

## 8. The team and the board

Board `eqo-android`. Commands: `hermes kanban --board eqo-android stats | list [--status running|ready|blocked|todo] | show <id> | create ... | comment <id> "..." | block|unblock|complete|reclaim|promote <id> | set-model --provider <p> <id> <model> | dispatch [--dry-run]`.

| Profile | Model | Provider |
|---|---|---|
| eqo-core-dev, eqo-architect, eqo-security, eqo-reviewer-mimo | `mimo-v2.6-pro` | `xiaomi` (MiMo API) |
| eqo-routine-dev, eqo-build-fixer, eqo-qa, eqo-reviewer-glm | `glm-5.3-flash` | `opencode-go` |
| eqo-docs | `deepseek-v4.1-flash` | `opencode-go` (workspace set to Global) |
| eqo-trial | `gpt-6.1-sol`, fallback `mimo-v2.6-pro` (xiaomi), then `mimo-v2.6-pro` (opencode-go) | `openai-codex` |
| eqo-probe | `gpt-6-astra`, testing only: pass `--provider` and `-m` to override | `openai-codex` (no fallback) |

All `eqo-*` profiles except the probe and trial fall back to `opencode-go` / `deepseek-v4-flash`. Test a model without masking: `hermes -p eqo-probe --provider <p> -m <model> --usage-file out.json -z "Reply with exactly: OK"` (the usage file names the model and provider that answered; a profile with fallbacks can report a false OK). A HTTP 400 "model not supported" does not trigger a fallback; rate limits, overload and connection errors do. Cards in `triage` (Hermes parks a card that blocked repeatedly) cannot be unblocked from the CLI: open the card in the dashboard and press "-> ready".

## 9. Lessons learned (each one cost hours)

- **Workers invent things.** One produced fake file names, SHAs and test names. Never accept evidence you did not reproduce.
- **Stale Gradle results:** files a test reads but that are not declared inputs (for example `res/xml/*.xml`) do not trigger a rerun: use `--rerun` on the test task. A 24-second "success" after a change is suspicious.
- **Files made on Windows lack the executable bit** (`gradlew`, `scripts/*.sh`): `git update-index --chmod=+x <file>`; CI says "Permission denied" (exit 126).
- **`scripts/check-branding.sh` is strict by design:** any-case upstream names are rejected in user-visible code, prompts, resources and manifests (including XML comments); every Kotlin file needs a provenance-map row, every row must name an existing path.
- **Phone (Realme Narzo 20 RMX2193, Realme UI 2.0, Android 11):** read-only adb checks (`dumpsys accessibility`, `settings get secure ...`); never write secure settings; the owner switches accessibility services on by hand (Settings, Additional Settings, Accessibility, Downloaded services). `am instrument` force-stops the app and leaves the service Crashed on this device, so device records run in-process (`am start ... --ez runRecords true`). Touches on the floating accessibility icon are not delivered as takeover events. A touch prompt must be a large banner at the top. Vibrate needs the VIBRATE permission or a try/catch (a missing permission crashed the test app). Reinstalling a test APK can clear the enabled service. Only one worker may use the phone at a time; the owner taps only when you tell them a window has started.
- **Hermes is slow when the machine is busy:** long hermes commands may exceed tool timeouts; run them in the background and read the output file. The status line's running list can come back empty.
- **A fix commit can wake a dormant test** (the upstream backup-rules test was skipped until backup rule files existed, then failed in CI): read CI failures instead of guessing.

## 10. Devices

- **Physical:** Realme Narzo 20 (RMX2193), Android 11 (API 30), 3.8 GB RAM, 22 GB free, serial `<DEVICE_SERIAL>` over USB with USB debugging authorized. Installed apart from stock apps: `ai.eqo.test` (TASK-009 instrumentation APK, its "EQO" accessibility service is switched on by the owner: leave it alone unless a task says otherwise; when TASK-009-style testing is over, switch it off and uninstall it) and `com.pocketpalai` (the owner's own app: never touch). 
- **Emulators:** `eqo-api31` (Android 12) and `eqo-api33` (Android 13), AVD home `C:\Users\<user>\Android\avd`. Label emulator evidence "emulator" next to physical evidence. No physical Android 12 or 13 device has been available so far; those two API levels are still untested (section 11).

## 11. Open follow-ups

1. **Security SF-1 (TASK-007 security pass):** the helper keeps upstream's `WRITE_SECURE_SETTINGS` self-grant to the manager (`helper-server/.../ShizukuService.java`). Under `adb shell` activation it fails harmlessly, but where the server runs as root it silently gives EQO `WRITE_SECURE_SETTINGS` — enough to enable EQO's own accessibility service and rewrite secure settings without consent. **Remove it or gate it behind explicit user consent before release / TASK-008 (S2).** Also from that pass: SF-2 (pin the signing cert or uid if the manager allowlist grows past one id) and SF-3 (verify binder provenance if the broadcast is ever sent cross-uid).
2. Issue #44 (for TASK-015): enforce the cost-disclosure gate at the provider boundary and show the screen; install `RedactingLog` and `LogRedactorCrashHook` in an `Application` class and replace the 168 direct `Log` call sites; shape-based redaction (the N1 trial task); release rotated keys from `SecretRegistry`.
3. Issue #50 (for TASK-012): SF-1 to SF-4 and N-3 are implemented in PR #52. Residuals: secure-window detection is reflection-based (`AccessibilityWindowInfo.isSecure()` is hidden in the public SDK), any future observe->model sink must go through `UntrustedScreenText.wrap`, and the grant-time copy (N-7) still says "the moment you touch the screen EQO pauses" without the mid-gesture caveat; the hard-block list (#42) is deliberately not built (D-010).
4. **Nothing constructs `ActionLoop` in `main`/`:app` yet** — the loop and its policy are unit- and race-tested but unwired; TASK-015 wires it (the loop's callers supply permission/approval/execute/observe lambdas).
5. **Android 12 and 13 are untested** (the owner has no such device; only the `eqo-api31`/`eqo-api33` emulators exist). Everything so far is Android 11.
6. **TASK-008 is pending** (wireless ADB pairing; needs the phone).
7. The Shizuku trademark/naming question must be settled before any public distribution (`NOTICE` flags it and claims nothing).
8. `AGENTS.md` line 21 says "D-001 to D-006"; it should say D-010. Protected file: needs the owner's approval.
9. `docs/USER-FLOWS.md` should be updated from `android/Phase-One/design/README.md`; `ci.yml` wrapper-validation and setup-gradle steps are not gated; the four named lint suppressions in `android/app/lint.xml` stay "no new entries without a stated reason".
10. Not verified anywhere yet: screenshots of the app label and notices screen (TASK-005), the cost-disclosure screen, Keystore behaviour on a phone, the deep link on a device.

## 12. Next steps (in order)

1. **TASK-008** (wireless ADB pairing, after TASK-007): release its phone card, brief, author on GPT 6.1 Sol (`eqo-trial`), review (`eqo-reviewer-glm`), security pass (`eqo-security`), verify in a fresh clone, then PR. It needs the phone, so it waits for the owner (owner away: phone tests queue for his return).
2. **TASK-010** (Chrome CDP spike, after TASK-008): same chain.
3. **TASK-015** when 008 and 010 are done (007, 012 and 014 already done): wire `ActionLoop` into the app, close issues #44 and #50, then **TASK-016** (exit review; gate 6 recorded as deferred). First usable build is the study APK from TASK-015.
4. Fix security SF-1 (remove or consent-gate the `WRITE_SECURE_SETTINGS` self-grant) before release.
5. Keep this page current after every merge (docs PR, owner approves).
