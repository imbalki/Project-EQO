# Contributing

## Workflow
Trunk-based development. `main` is always releasable and protected.

1. Open or pick an issue. Use the templates in `.github/ISSUE_TEMPLATE/`.
2. Create a worktree and branch: `scripts/worktree.sh new <agent-or-name> <issue> <slug>`.
3. Commit in small steps using Conventional Commits.
4. Push and open a PR using the PR template. Link the issue (`Closes #N`).
5. CI must pass and one review is required. Squash merge.
6. Clean up: `scripts/worktree.sh remove <path>`.

## Commit format
`type(scope): summary` where type is one of `feat fix docs refactor test chore ci perf build`.

## Versioning
Semantic Versioning. Tags `vX.Y.Z` on `main` trigger release builds.

## Labels
- `area:android`, `area:desktop`, `area:server`, `area:shared`, `area:docs`, `area:ci`
- `phase:1` to `phase:4`
- `agent:unassigned`, `agent:<name>`
- `type:feature`, `type:bug`, `type:chore`
- `status:blocked`, `status:needs-decision`
