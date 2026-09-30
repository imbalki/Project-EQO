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
- Phase 1 is pure Kotlin. No embedded Node.js, no Python on the phone.
- Agent loop is written directly in Kotlin (OpenAI-compatible HTTP to OpenRouter, local Gemma via LiteRT). No agent framework.
- App integrations go through Composio's hosted HTTP API.
- Memory in Phase 1 is Room (SQLite) with LLM fact extraction.
- Cloud is opt-in. Default path must work with no network.

## Checks
- `android/`: `./gradlew ktlintCheck detekt test`
- `desktop/`: `npm run lint && npm test`
- `server/`: see `server/README.md` once created
- Repo: `scripts/check.sh`

## Agent workspaces
Each agent role has a folder under `agents/` (instructions, context, tasks, handoff). Android: `agents/android/README.md`.

## Ownership
Directory ownership is in `.github/CODEOWNERS`. Task-to-agent assignment is tracked in GitHub Issues and the Project board using the `agent:*` labels.
