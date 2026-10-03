# Phase One status and handoff (runbook for ANY agent)

Last updated: 2026-10-03 about 10:30 local. TASK-001 to 006, 009 and 014 are merged. In flight: TASK-007 (helper spike, has the phone), TASK-012 (action loop, code first) and a two-worker model trial. Update this page in the PR that finishes each task. It is written so that a person or **any** AI agent (Claude Code, Codex, Hermes, anything that can run a shell, `git`, `gh` and `hermes`) can continue the work from the repo alone, with no knowledge held only by a previous chat.

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
| Model routing | `docs/agents/MODEL-ROUTING.md` (not yet updated to the 2026-10-03 routing in section 4) |
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
- **Model routing (owner, 2026-10-03):** MiMo 2.6 Pro (`xiaomi`) authors all substantial tasks and does security passes and independent reviews; lighter models (GLM 5.3 Flash, DeepSeek V4.1 Flash) only for routine work (screens, copy, docs, QA). Reason: early GLM Flash runs stalled or produced invented evidence and the rework cost more than it saved. The owner also enabled an OpenAI login in Hermes (`openai-codex`): model **`gpt-6.1-sol`** answers there (the names `gpt-6.1` and `6.1-sol` are rejected). Trial profile `eqo-trial` = `gpt-6.1-sol`, then fallback MiMo 2.6 Pro on `xiaomi`, then MiMo 2.6 Pro on `opencode-go` (the chain was verified by breaking each link: the usage file showed `openai-codex`, then `xiaomi`, then `opencode-go` answering). A two-worker speed trial is running (section 7); no routing change is made until its result is recorded here and the owner agrees.
- **Merge policy:** the owner approves merges. The 2026-10-03 pre-approval (TASK-005, 006, 014, 009, on conditions: independent review passed, security pass passed where required, the lead's own fresh-clone build passed, CI green, no open owner question) is **used up**. Every later PR needs the owner's explicit approval, except docs PRs the owner asked for in chat.
- Open owner decisions: the Shizuku trademark/naming question before any public distribution (`NOTICE` flags it and claims nothing); the outcome of the model trial.

## 5. Task progress (16 tasks)

| Task | Issue | State | Evidence |
|---|---|---|---|
| 001 toolchain | #33 | **Done** (PR #22) | `evidence/toolchain*.txt` |
| 002 upstream build and lint | #34 | **Done** (PR #30) | `evidence/task-002-*` |
| 003 skeleton and CI | #35 | **Done** (PR #32) | `evidence/task-003-skeleton-ci.md` |
| 004 module extraction | #36 | **Done** (PR #41, `a567321`) | `evidence/task-004-extraction-map.md`, ADR-0004 |
| 005 identity, branding, NOTICE | #10 | **Done** (PR #43, `97fc72b`) | `evidence/task-005-*` |
| 006 OpenRouter key, redaction | #11 | **Done with a known gap** (PR #46, `edf9403`): acceptance criterion 5 (cost disclosure before first use) is NOT MET; redactor and crash hook have no production callers yet. Follow-ups: issue #44 | `evidence/task-006-byok-security.md` |
| 007 helper spike (Shizuku-derived) | #12 | **In progress** (author card `t_139aca28`, MiMo 2.6 Pro, has the phone). Largest risk: if the spike fails, stop and report, do not work around it | |
| 008 wireless ADB pairing | #13 | Waiting on 007 | |
| 009 one accessibility service, takeover | #14 | **Done** (PR #49, `70b26e0`): criteria 1 to 4 pass on the physical phone, criterion 5 not tested. Security follow-ups: issue #50 | `evidence/task-009-accessibility.md` |
| 010 Chrome CDP spike | #15 | Waiting on 008 | |
| 011 virtual display | #16 | **Deferred (D-008)** | |
| 012 action loop, Pause/Stop/takeover | #17 | **In progress** (author card `t_ef511d4a`, code first, phone after 007). Scope also includes issue #50 (gated actions, secure-window and password filtering, untrusted screen text, user-initiated resume) and approvals decided by a static policy, not by the model | |
| 013 local Gemma | #18 | **Parked (D-009)** | |
| 014 SMS compose-only, permissions | #19 | **Done** (PR #45, `9120e1d`) | `evidence/task-014-sms-permissions.md` |
| 015 study APK and onboarding | #20 | Waiting (needs 007, 008, 010, 012, 014 done). Also carries issues #44 and #50 | |
| 016 device matrix and exit review | #21 | Waiting on 015; gate 6 is recorded as deferred | |

Issue numbers: TASK-001 to 004 were #6 to #9, moved by an interrupted command and moved back (#33 to #36). Commit messages from before say `Refs #6/#7/#8`: read as #33/#34/#35.

Pull requests: merged #2 to #5, #22, #26, #27, #29 to #32, #38, #40, #41, #43, #45, #46, #49. Open: #48 (this documentation refresh). Open issues to know: #25 (UX), #44 (TASK-015 follow-ups from the TASK-006 security pass), #50 (TASK-012 follow-ups from the TASK-009 security pass), #42 (superseded by D-010 and the TASK-012 brief; close it).

## 6. The operating loop (do exactly this for every task)

The lead (you) drives; workers are Hermes cards. A task is a chain of cards: **author -> independent review (the other model family) -> security pass (tasks 006, 007, 008, 009, 012) -> docs**; phone tasks also have a blocked "OWNER: connect physical phone and confirm" card that you complete only when the task's real predecessors are done.

1. **Release a task:** complete its phone card (`hermes kanban --board eqo-android complete <id> --summary "..."`) when predecessors are done and the owner is present/phone connected (`adb devices` shows `device`).
2. **Always dry-run before dispatching:** `hermes kanban --board eqo-android dispatch --dry-run`, check the spawn list is exactly what you intend, then `... dispatch`. Dispatch is blanket: it starts every ready card and also restarts author cards sitting in `review`. Hold cards with `block <id> "reason"`; cards in `review` cannot be blocked (complete them after verifying).
3. **Brief every worker** with a comment (`comment <id> "..."` or `create ... --body-file`) that contains: the task spec path, the phone rule, the memory flags (below), branch from current `origin/main`, evidence rules, "every number, file name, SHA and test name must come from a command you ran", push before the 2-hour limit, heartbeat every 10 minutes.
4. **Watch heartbeats, not just status** (`hermes kanban --board eqo-android show <id>`). A worker silent for 30+ minutes is stalled: `reclaim <id>`, re-brief ("read your worktree first, do not start over"), dispatch again. If a worker is about to hit the 2-hour limit with unpushed local commits, push its branch yourself (normal fast-forward push).
5. **VERIFY before the review starts, with your own commands** (never trust the worker's text): (a) pushed head is based on current `origin/main`; (b) fresh clone of the pushed branch and run the full gate from `android/`: `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m` (about 15 to 30 minutes), quote the BUILD line, exit code and test counts rolled from `*/build/test-results/testDebugUnitTest/*.xml` by script; (c) `bash scripts/check-branding.sh` and `bash scripts/check.sh` exit 0; (d) no-Leap grep clean; (e) spot-check the claims in the evidence doc against git and the XML results.
6. **Review and security cards:** brief them with a focus list, tell them to work in a scratch clone and run no Gradle if possible (the lead's build is the gate), and to end with an explicit `VERDICT: PASS` or `VERDICT: CHANGES REQUESTED`. A reviewer that requests changes completes its card with the findings in an attachment; **you** create the fix card (`create "<title>" --assignee eqo-core-dev --body-file <file> --created-by eqo-lead`), dispatch it, verify it, and run a short second-family re-check.
7. **Branch and PR:** after a re-base onto a renamed or updated main, create a backup branch, `git rebase --onto origin/main <old-base> <branch>`, prove it mechanical by comparing the changed lines before and after (normalise renames), then `git push --force-with-lease` your OWN branch only, after announcing it. Open the PR with a body that states what changed, the real verification outputs, and what is NOT verified. Mark draft until phone records are complete.
8. **Merge:** only when the independent review and the security pass (where required) say PASS, your own fresh-clone gate passed, CI is green on the final head, and the owner has approved (section 4). Squash merge: `gh pr merge <n> --repo imbalki/project-eqo --squash --subject "<type>(android): <summary> (TASK-0NN) (#n)" --body "<summary>"`. Then release the task's docs card and update this page in a docs PR.
9. **Record follow-ups** as GitHub issues, never only in chat.

**Memory flags (the machine has 15.7 GB and several workers):** one Gradle invocation at a time, `--max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m`. "Failed to load jvm.dll" or "Gradle build daemon disappeared" is a memory crash, not a code failure: rerun.

## 7. In flight right now (check with `hermes kanban --board eqo-android list --status running`)

- **TASK-007** `t_139aca28` (helper spike, `eqo-architect`/MiMo 2.6 Pro) has the phone. It may block itself with a reason starting `OWNER ACTION:` when it needs the owner for a prompt on the phone: relay the exact steps to the owner, then comment `GRANTED`/done and unblock.
- **TASK-012** `t_ef511d4a` (action loop, `eqo-core-dev`): code and unit tests first; must not use the phone until you post `PHONE FREE` on its card (only one worker may use the phone at a time).
- **Model trial (started 10:19:30 local):** the same task, shape-based redaction in `LogRedactor` (issue #44, item N1, only `:core-llm`), given to GPT 6.1 Sol (card `t_3c04decf`, profile `eqo-trial`, branch `agent/android/44-shape-redaction-sol`) and to MiMo 2.6 Pro (card `t_5fde84fb`, `eqo-core-dev`, branch `agent/android/44-shape-redaction-mimo`). Compare elapsed time (card timestamps), test counts, review findings and code quality, record the result in this page, and ask the owner before changing routing. No PR is opened from the trial; if one branch is good, ask the owner before it goes anywhere.

## 8. The team and the board

Board `eqo-android`. Commands: `hermes kanban --board eqo-android stats | list [--status running|ready|blocked|todo] | show <id> | create ... | comment <id> "..." | block|unblock|complete|reclaim|promote <id> | set-model --provider <p> <id> <model> | dispatch [--dry-run]`.

| Profile | Model | Provider |
|---|---|---|
| eqo-core-dev, eqo-architect, eqo-security, eqo-reviewer-mimo | `mimo-v2.6-pro` | `xiaomi` (MiMo API) |
| eqo-routine-dev, eqo-build-fixer, eqo-qa, eqo-reviewer-glm | `glm-5.3-flash` | `opencode-go` |
| eqo-docs | `deepseek-v4.1-flash` | `opencode-go` (workspace set to Global) |
| eqo-trial | `gpt-6.1-sol`, fallback `mimo-v2.6-pro` (xiaomi), then `mimo-v2.6-pro` (opencode-go) | `openai-codex` |
| eqo-probe | no fixed model: pass `--provider` and `-m` | no fallback; testing only |

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
- **Emulators:** `eqo-api31` (Android 12) and `eqo-api33` (Android 13), AVD home `C:\Users\<user>\Android\avd`. Label emulator evidence "emulator" next to physical evidence.

## 11. Open follow-ups

1. Issue #44 (for TASK-015): enforce the cost-disclosure gate at the provider boundary and show the screen; install `RedactingLog` and `LogRedactorCrashHook` in an `Application` class and replace the 168 direct `Log` call sites; shape-based redaction (the model trial task); release rotated keys from `SecretRegistry`.
2. Issue #50 (for TASK-012): route every action through the takeover-gated path, skip password fields and secure windows, frame screen text as untrusted data, user-initiated resume only, soften the grant-time description.
3. `docs/agents/MODEL-ROUTING.md` and the "Models:" lines of the task specs still show the old routing: update after the model trial result.
4. `AGENTS.md` line 21 says "D-001 to D-006"; it should say D-010. Protected file: needs the owner's approval.
5. `docs/USER-FLOWS.md` should be updated from `android/Phase-One/design/README.md`; `ci.yml` wrapper-validation and setup-gradle steps are not gated; the four named lint suppressions in `android/app/lint.xml` stay "no new entries without a stated reason".
6. Not verified anywhere yet: screenshots of the app label and notices screen (TASK-005), the cost-disclosure screen, Keystore behaviour on a phone, the deep link on a device, Android 12 and 13.

## 12. Next steps (in order)

1. **Finish TASK-007:** when its author card completes, run the verification routine (section 6), brief the review (`eqo-reviewer-glm`) and the security pass (`eqo-security`), then docs, PR and ask the owner to approve the merge. Release the phone to TASK-012 (`PHONE FREE`) as soon as TASK-007 stops using it.
2. **TASK-012** continues in parallel; its device scenario needs the owner (run, user taps, loop pauses, resume, stop).
3. **TASK-008** (after 007) and **TASK-010** (after 008): complete their phone cards, brief, verify, review.
4. **TASK-015** when 007, 008, 010, 012, 014 are done, then **TASK-016**. First usable build is the study APK from TASK-015; the exit review is TASK-016.
5. Record the model-trial result in this page; ask the owner whether to change the routing.
6. Keep this page current after every merge (docs PR, owner approves).
