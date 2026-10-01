# Claude Code kickoff (paste into Claude Code on the PC)

Before pasting: open PowerShell, run `claude`, sign in through the browser. Work folder: `C:\Users\<user>\Claude`.

---

You are the engineering lead for EQO, an Android assistant app. Read `AGENTS.md`, `docs/agents/MODEL-ROUTING.md` and `agents/android/tasks/INDEX.md` in the Project-EQO repo (github.com/imbalki/project-eqo, branch `docs/phase-one-tasks-and-routing` until merged), and `Phase-One/DECISIONS.md` in imbalki/project-eqo-android. Clone both under `C:\Users\<user>\Claude`. Never commit to main; branch and open PRs.

Do these in order and report after each:

1. Android toolchain (TASK-001). Install via winget: JDK 21 (Temurin), Android command-line tools; use sdkmanager for platform-tools, platforms;android-36, build-tools, NDK 29, CMake 3.31+. Set JAVA_HOME and ANDROID_HOME. Stop and ask me to accept SDK licenses myself. Record versions in `evidence/toolchain.txt`.
2. Hermes providers. Confirm `hermes doctor` and that each of `mimo-v2.6-pro` (provider `xiaomi`), `deepseek-v4.1-flash` and `glm-5.3-flash` (provider `opencode-go`) answers "Reply with exactly: OK" through an eqo-* profile (not the default profile, whose fallbacks can mask failures). If an ID is wrong, list the real IDs and fix MODEL-ROUTING.md. Do not print API keys.
3. Hermes kanban. `hermes kanban init`; create one card per task TASK-001..016 with dependencies and `set-model` per the task files. Do not start the daemon yet.
4. Run TASK-001 through TASK-003 using Hermes workers in separate worktrees (`--worktree`), reviewer on a different model. Verify every acceptance criterion with command output yourself before marking done.
5. Tell me when a task needs a physical phone (TASK-007 onward). I will connect one with wireless debugging.

Rules: announce before irreversible or external actions; no secrets in commits; free/open-source tools only; keep PRs small.
