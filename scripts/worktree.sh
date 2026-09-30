#!/usr/bin/env bash
# Manage per-agent git worktrees. See docs/agents/WORKTREES.md
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
BASE="$(dirname "$ROOT")/$(basename "$ROOT")-worktrees"

usage() {
  echo "Usage:"
  echo "  $0 new <agent> <issue-number> <slug>"
  echo "  $0 list"
  echo "  $0 remove <path>"
  echo "  $0 prune"
  exit 1
}

cmd="${1:-}"; shift || true
case "$cmd" in
  new)
    [ $# -eq 3 ] || usage
    agent="$1"; issue="$2"; slug="$3"
    branch="agent/${agent}/${issue}-${slug}"
    path="${BASE}/${agent}-${issue}-${slug}"
    mkdir -p "$BASE"
    git -C "$ROOT" fetch origin main
    git -C "$ROOT" worktree add -b "$branch" "$path" origin/main
    echo "Worktree: $path"
    echo "Branch:   $branch"
    ;;
  list)   git -C "$ROOT" worktree list ;;
  remove) [ $# -eq 1 ] || usage; git -C "$ROOT" worktree remove "$1" ;;
  prune)  git -C "$ROOT" worktree prune ;;
  *)      usage ;;
esac
