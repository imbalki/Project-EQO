# ADR-0002: Phase One code, docs, evidence and issues live in project-eqo

- Status: accepted
- Date: 2026-10-01
- Deciders: owner (imbalki)

## Context
AGENTS.md and `agents/android/tasks/INDEX.md` assumed (A-1) that Android code lives in the separate
`project-eqo-android` repo. `project-eqo` is already a monorepo (ADR-0001) with an `android/` directory,
`android/Phase-One/` documents, `agents/android/tasks/` and the task issues.

## Decision
Everything for Phase One lives in `project-eqo`:
- Android code under `android/`.
- Phase One documents under `android/Phase-One/docs/`, task evidence under `android/Phase-One/evidence/`.
- Task specs under `agents/android/tasks/`; issues and PRs in this repo.

`project-eqo-android` is retained as read-only reference for the original decision and feasibility documents.
This supersedes assumption A-1 and the "Code lives in Project-EQO-Android" line in AGENTS.md (to be reconciled
in a follow-up docs PR, per D-003).

## Consequences
One CI, one CODEOWNERS and one issue tracker. Evidence paths in task files that say `Phase-One/evidence/` mean
`android/Phase-One/evidence/`. No code is written in `project-eqo-android`.
