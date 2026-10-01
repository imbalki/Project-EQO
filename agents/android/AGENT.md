# Android Agent

## Role
Builds the EQO phone app: native Kotlin + Jetpack Compose, as a fork of OpenDroid with ClosePaw and Shizuku donor code (decisions D-001 to D-006 in `Project-EQO-Android/Phase-One/DECISIONS.md`). Code lives in the `Project-EQO-Android` repo (assumption A-1); tasks and routing live here.

## Scope
- Allowed: the `Project-EQO-Android` repo (code), `agents/android/**` (this repo).
- Ask first (open an issue, tag `status:needs-decision`): `packages/shared/**`, `docs/**`, `.github/**`, anything under `desktop/` or `server/`.

## Working rules
1. Follow root `AGENTS.md`. Never commit to `main`.
2. Create a worktree per task: `scripts/worktree.sh new android <issue> <slug>` (branch `agent/android/<issue>-<slug>`).
3. One task file = one issue = one PR. Copy the task's acceptance criteria into the issue.
4. Announce in the issue what you are about to do before adding a dependency, changing permissions in `AndroidManifest.xml`, or touching signing config.
5. Update the task's `status` in `tasks/INDEX.md` and its own file in the same PR.
6. Leave a handoff note in `handoff/` when you stop mid-task (use `handoff/TEMPLATE.md`).

## Tech constraints (Phase 1)
- Kotlin app. Agent loop written directly in Kotlin; no agent framework.
- The ClosePaw Python bridge asset may stay bundled (D-005) but must never be launched; `termux_shell` is disabled.
- OpenRouter bring-your-own-key is the primary LLM path (D-001). On-device Gemma/LiteRT ships if it works (D-006), labelled experimental if not.
- Connectors (Composio and similar) are Phase 2 (D-002). Memory beyond what upstream provides is Phase 2.
- Open-source dependencies only. Nothing that needs a paid subscription.
- Sideloaded APK via GitHub Releases, Android 12+ (minSdk 31). Do not design around Play Store accessibility policy.
- Shizuku-derived helper: rename app ID and `moe.shizuku.manager.permission.*` strings together (see D-004 as corrected).

## Definition of done
- Acceptance criteria in the task file all met.
- Checks named in the task pass (Gradle lint, detekt, tests, `scripts/check-branding.sh` once it exists).
- Unit tests for new logic; no secrets in code; keys read from settings or `local.properties`.
- Docs updated (`context/` or `docs/`) if behavior or architecture changed.
- PR follows `.github/pull_request_template.md`.

## Reporting
Comment on the issue: what changed, how to verify, open questions.
