# Phase One status and handoff

Last updated: 2026-10-02 (after TASK-003 and the UX prototype merged). Update this page in the PR that finishes each task. It is written so a person or a new AI chat can restart from the repo alone.

## How to resume in a new chat

Open the chat in the `project-eqo` folder (so `AGENTS.md` loads) and paste:

> Continue EQO Phase One. Read `docs/agents/PHASE-ONE-STATUS.md`, then run `hermes kanban --board eqo-android stats` and `list`, and `gh pr list --repo imbalki/project-eqo`. Give me a plain-language status: done, running, waiting on me. Ask before choosing between options. When something is ready to merge, run the merge command so I can approve it, and remind me when you are waiting on me.

Owner preferences (the owner is non-technical): plain language; real command output and exit codes; say what was not tested; never choose silently between options; merges are invoked by the agent and approved by the owner; use the `eqo-*` Hermes team, never the owner's personal Telegram profiles.

## Where everything lives

| What | Where |
|---|---|
| All Phase One code, docs, evidence, issues, PRs | this repo, `imbalki/project-eqo` (ADR-0002). `project-eqo-android` is read-only reference |
| Android code | `android/` (Gradle project, `:app`, `ai.eqo`) |
| Phase One documents and evidence | `android/Phase-One/` (`DECISIONS.md`, `docs/`, `evidence/`, `design/`) |
| Task specs | `agents/android/tasks/` (`INDEX.md` lists dependencies) |
| Decisions | `android/Phase-One/DECISIONS.md` (D-001 to D-007) and `docs/adr/` (0001 to 0003) |
| Model routing | `docs/agents/MODEL-ROUTING.md` |
| Local worktrees | `C:\Users\<user>\Claude\worktrees\` (one per task) |
| Pinned upstream study clone | `C:\Users\<user>\Claude\worktrees\_upstream\opendroid` at `6ff5a061` (read-only) |

## Decisions in force

- Android floor: **Android 11 (minSdk 30)**, because wireless-debugging pairing exists from Android 11 (D-007, ADR-0003).
- **No LiquidAI Leap SDK** anywhere in EQO code, dependencies or docs. CI and TASK-003/004 carry a grep check.
- OpenRouter bring-your-own-key is the primary model path; local Gemma is opportunistic (D-001, D-006). App connectors are Phase 2 (D-002).
- The bundled Python bridge stays inert (D-005); helper naming per D-004.
- All Phase One work (code, docs, evidence, issues) lives in this repo (ADR-0002).
- Visual identity: **Warm (clay)**, light and soft, not OpenDroid's look. **Animations are Phase 2.**
- Fallback model for all worker profiles is DeepSeek V4 Flash. Qwen 3.7 Plus was dropped for now.

## Task progress (16 tasks)

| Task | Issue | State | Evidence |
|---|---|---|---|
| TASK-001 toolchain | #33 (closed) | **Done**, reviewed, merged (PR #22) | `android/Phase-One/evidence/toolchain.txt`, `toolchain-gradle-build.txt`, `docs/TOOLCHAIN.md` |
| TASK-002 upstream build and lint | #34 (closed) | **Done**, reviewed, merged (PR #30). Real results: assembleDebug exit 0; unit tests exit 1 (537 run, 2 fail); lint exit 1 (40 errors) | `evidence/task-002-upstream-build-and-lint.md`, `evidence/task-002-lint/` |
| TASK-003 skeleton and CI | #35 (closed) | **Done**, reviewed, merged (PR #32). CI green | `evidence/task-003-skeleton-ci.md` |
| TASK-004 adapter extraction | #36 | **Next. Not started** | |
| TASK-005, 006, 009, 014 | #10, #11, #14, #19 | Waiting on TASK-004 (can then run in parallel) | |
| TASK-007, 008, 010, 011, 012, 013, 015, 016 | #12, #13, #15, #16, #17, #18, #20, #21 | Waiting; also need the phone | |

Issue numbers: TASK-001 to 004 were #6 to #9. They were moved to `project-eqo-android` by a command that was interrupted halfway, and then moved back, so they now have the numbers above. Commit messages and PRs from before that still say `Refs #6`, `#7`, `#8`: read those as #33, #34, #35.

## Merged and open pull requests

Merged: #2 to #5 (Phase One documents and task rewrite), #22 (toolchain, evidence, ADR-0002), #26 (Android 11 floor, no Leap, ADR-0003, D-007), #27 (docs reconcile), #29 (model routing), #30 (TASK-002 evidence), #31 (UX prototype, copy deck, accessibility review), #32 (TASK-003 skeleton and CI). No open pull requests.

Issue #25 (UX) stays open for the remaining UX steps.

## The team and the board

Hermes kanban board `eqo-android` (about 70 cards). Nothing runs on its own: workers start only when the lead runs `hermes kanban --board eqo-android dispatch`. Each task is a chain: author, then review by the opposite model family, then a security pass (TASK-006, 007, 008, 009, 012), then docs. The next task starts only after the gate card (review or security) is done.

| Profile | Model | Provider |
|---|---|---|
| eqo-core-dev, eqo-architect, eqo-security, eqo-reviewer-mimo | `mimo-v2.6-pro` | `xiaomi` (MiMo API) |
| eqo-routine-dev, eqo-build-fixer, eqo-qa, eqo-reviewer-glm | `glm-5.3-flash` | `opencode-go` |
| eqo-docs | `deepseek-v4.1-flash` | `opencode-go` (needs the OpenCode workspace set to Global, done) |
| eqo-probe | no fixed model: pass `--provider` and `-m` | no fallback; use it only to test models |

Gotchas learned the hard way:
- `hermes kanban dispatch` also restarts author cards sitting in `review` status. Verify and `complete` them first, and check `list --status ready` before dispatching.
- The default Hermes profile has fallback providers that can report a false OK. Test models through `eqo-probe`.
- Repo CI runs only on pull requests and pushes to `main`: open a draft PR to trigger it for a branch.
- Files made on Windows lack the executable bit: `git update-index --chmod=+x android/gradlew`.
- `AGENTS.md` is protected: workers cannot edit it; the owner must approve any change.
- Nine "OWNER: connect phone" cards (tasks 007 to 013, 015, 016) stay blocked. Complete each one only when its task's real predecessors are done, or the task would start early.

## Devices

- Physical: Realme Narzo 20 (RMX2193), Android 11 (API 30), USB debugging on. On 2026-10-02 `adb devices` showed it as `offline` even after restarting the ADB server; Windows sees the "ADB Interface". It needs the phone to re-approve the computer. A second `adb.exe` (scrcpy's) is earlier on `PATH`; use the SDK one: `C:\Users\<user>\Android\Sdk\platform-tools\adb.exe`.
- Emulators: `eqo-api31` (Android 12) and `eqo-api33` (Android 13), AVD home `C:\Users\<user>\Android\avd`. API 31 booted in about 7 minutes; API 33 not boot-tested. Emulator evidence must be labelled "emulator" next to physical-device evidence.
- Toolchain: Temurin JDK 21, `ANDROID_HOME=C:\Users\<user>\Android\Sdk`, Gradle 9.7.0, AGP 9.3.1, Kotlin 2.4.0.

## Open follow-ups (small, none blocking TASK-004)

1. `AGENTS.md` line 21 says "D-001 to D-006"; it should say D-007. Protected file: needs the owner's approval.
2. The TASK-003 reviewer noted that `ci.yml` wrapper-validation and setup-gradle steps are not gated by the module check.
3. The four named lint suppressions in `android/app/lint.xml` are accepted with conditions: no new entries without a stated reason, drop each when its pin is refreshed, add dependabot or Renovate.
4. `docs/USER-FLOWS.md` should be updated from the notes in `android/Phase-One/design/README.md` (S-08 check count, missing S-17 and S-18, wording changes).
5. Task specs may still contain stale minSdk 31 text; check each when its task starts.
6. UX: the full prototype is merged; the remaining UX steps (more flows, device validation of placeholders marked PENDING) continue under issue #25.

## Next

1. **TASK-004** (issue #36): extract the OpenDroid base into EQO modules (`:core-agent`, `:core-llm`, `:core-security`, `:platform-a11y`). Author `eqo-core-dev` (`mimo-v2.6-pro`), review by `eqo-reviewer-glm`. Must not import anything that needs the LiquidAI Leap SDK; the grep gate in the spec enforces it.
2. After TASK-004 passes review, run TASK-005, 006, 009 and 014 in parallel in separate worktrees (and 013 after 006). They share the module layout, so check each branch against the others before merging.
3. Phone tasks (007 onward) need the physical phone online (state `device`) and Android 12 and 13 emulator evidence labelled as such.
