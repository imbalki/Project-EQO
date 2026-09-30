# ADR-0001: Monorepo with trunk-based workflow and per-agent worktrees

- Status: accepted
- Date: 2026-09-30

## Context
One product (EQO) with phone, desktop and server parts, built by several agents in parallel by a solo non-technical founder.

## Decision
Single monorepo. Short-lived branches off `main`, one git worktree per agent task, squash-merge PRs, protected `main`, CI on every PR.

## Consequences
Agents see the whole product and share schemas in `packages/shared`. Isolation comes from worktrees and directory ownership rather than separate repos.
