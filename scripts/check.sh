#!/usr/bin/env bash
# Repo-level checks. Run before opening a PR.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

echo "== Secret scan (basic) =="
if git grep -InE '(sk-or-v1-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{30,})' -- . ':!scripts/check.sh'; then
  echo "Possible secret found"; exit 1
fi

echo "== Shell scripts =="
for f in scripts/*.sh; do bash -n "$f"; done

# TASK-005: branding gate is a repo check (DEV-04/DEV-16).
scripts/check-branding.sh

echo "OK"
