# Worktrees for parallel agents

Each agent works in its own git worktree so several agents can run at once without touching each other's files.

## Layout
```
project-eqo/                 main checkout (keep on main, read-only in practice)
project-eqo-worktrees/
  claude-12-voice-loop/      branch agent/claude/12-voice-loop
  hermes-15-composio-client/ branch agent/hermes/15-composio-client
```
Worktrees live next to the main checkout, never inside it.

## Commands
```
scripts/worktree.sh new <agent> <issue> <slug>   # create worktree + branch from latest origin/main
scripts/worktree.sh list                         # show all worktrees
scripts/worktree.sh remove <path>                # remove worktree after merge
scripts/worktree.sh prune                        # clean stale entries
```

## Rules
- One worktree per task. Do not reuse a worktree for a new task.
- Rebase on `origin/main` before opening a PR: `git fetch && git rebase origin/main`.
- Do not edit files outside the directories named in your issue.
- Two agents must not own the same directory at the same time. Check the issue's `agent:*` label and `.github/CODEOWNERS`.
- After the PR merges, remove the worktree and delete the branch.
