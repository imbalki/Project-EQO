# AGENTS.md

Instructions for any AI agent (Claude Code, Codex, Hermes, etc.) or human working in this repo.

## Project
EQO is a privacy-first personal AI assistant. Android-first (Kotlin), desktop second. See `docs/PRODUCT.md` and `docs/ARCHITECTURE.md`.

## Rules
1. Never commit to `main`. Work on a branch in your own worktree (see `docs/agents/WORKTREES.md`).
2. One task = one issue = one branch = one PR. Keep PRs small and reviewable.
3. Branch names: `agent/<agent-name>/<issue-number>-<slug>` or `feat|fix|docs|chore/<issue-number>-<slug>`.
4. Commits follow Conventional Commits: `type(scope): summary`. Scopes: `android`, `desktop`, `server`, `shared`, `docs`, `ci`.
5. Stay inside your task's scope. Touch only the directories the issue names. Ask before cross-cutting changes.
6. Never commit secrets, API keys or `.env` files. Use `.env.example` for shape only.
7. Run the relevant checks before opening a PR (see Checks). CI must be green.
8. Announce what you are about to do in the issue or PR before irreversible or external actions (publishing, deleting, force-pushing, creating new services).
9. Record significant decisions as an ADR in `docs/adr/`.
10. Prefer open-source tools and dependencies. Do not add anything that requires a paid subscription.

## Architecture constraints
Source of truth: decisions D-001 to D-006 in `android/Phase-One/DECISIONS.md`.
- Android app is a fork of OpenDroid with ClosePaw and Shizuku donor code. Agent loop in Kotlin, no agent framework.
- OpenRouter bring-your-own-key is the primary LLM path; local Gemma is opportunistic.
- The bundled Python bridge stays inert (D-005). Do not add new Node.js or Python runtime dependencies.
- App connectors (Composio and similar) are Phase 2.
- Android code lives in `android/` in this repo (ADR-0002, supersedes A-1); tasks live in `agents/android/tasks/`.

## Checks
- `android/`: `./gradlew ktlintCheck detekt test`
- `desktop/`: `npm run lint && npm test`
- `server/`: see `server/README.md` once created
- Repo: `scripts/check.sh`

## Agent workspaces
Each agent role has a folder under `agents/` (instructions, context, tasks, handoff). Android: `agents/android/README.md`.

## Ownership
Directory ownership is in `.github/CODEOWNERS`. Task-to-agent assignment is tracked in GitHub Issues and the Project board using the `agent:*` labels.
