# EQO

Privacy-first personal AI assistant. Your voice comes back as action.

Runs on the phone with zero cloud by default. Cloud (OpenRouter) is opt-in.

## Repository layout

| Path | Contents |
|------|----------|
| `android/` | Kotlin + Jetpack Compose phone app (Phase 1) |
| `desktop/` | Electron desktop app (Phase 2+) |
| `server/` | Server-side memory and agents (GBrain etc., Phase 2+) |
| `packages/shared/` | Shared schemas and protocol definitions |
| `docs/` | Product spec, architecture, ADRs, agent workflow |
| `scripts/` | Repo tooling (worktree helper, checks) |
| `.github/` | CI, issue and PR templates, CODEOWNERS |

## Working on EQO (humans and agents)

Read [AGENTS.md](AGENTS.md) first. Parallel work uses git worktrees:
see [docs/agents/WORKTREES.md](docs/agents/WORKTREES.md).

## Roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md).
