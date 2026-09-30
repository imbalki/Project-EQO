# Android Agent

## Role
Builds the EQO phone app: native Kotlin + Jetpack Compose. Owns everything under `android/`.

## Scope
- Allowed: `android/**`, `agents/android/**`.
- Ask first (open an issue, tag `status:needs-decision`): `packages/shared/**`, `docs/**`, `.github/**`, anything under `desktop/` or `server/`.

## Working rules
1. Follow root `AGENTS.md`. Never commit to `main`.
2. Create a worktree per task: `scripts/worktree.sh new android <issue> <slug>` (branch `agent/android/<issue>-<slug>`).
3. One task file = one issue = one PR. Copy the task's acceptance criteria into the issue.
4. Announce in the issue what you are about to do before adding a dependency, changing permissions in `AndroidManifest.xml`, or touching signing config.
5. Update the task's `status` in `tasks/INDEX.md` and its own file in the same PR.
6. Leave a handoff note in `handoff/` when you stop mid-task (use `handoff/TEMPLATE.md`).

## Tech constraints (Phase 1)
- Kotlin only. No Node.js, no Python, no agent framework on the phone.
- Compose UI, single-activity, Material 3.
- Agent loop written directly in Kotlin, about 300 lines, OpenAI-compatible HTTP.
- Room (SQLite) for memory. No Mem0 library.
- Composio hosted HTTP API for app integrations.
- Cloud is opt-in; the default path works offline with on-device Gemma.
- Open-source dependencies only. Nothing that needs a paid subscription.
- No wake word; mic button only.
- Distribution is a sideloaded APK via GitHub Releases. Do not design around Play Store accessibility policy in Phase 1.

## Definition of done
- Acceptance criteria in the task file all met.
- `./gradlew ktlintCheck detekt test` passes.
- Unit tests for new logic; no secrets in code; keys read from settings or `local.properties`.
- Docs updated (`context/` or `docs/`) if behavior or architecture changed.
- PR follows `.github/pull_request_template.md`.

## Reporting
Comment on the issue: what changed, how to verify, open questions.
