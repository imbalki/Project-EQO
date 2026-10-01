# Android task index (Phase 1)

Rewritten for the Phase One plan in `android/Phase-One/` (decisions D-001 to D-006). The earlier 15-task list assumed a from-scratch Kotlin app and is superseded.

Status values: `todo`, `in-progress`, `blocked`, `in-review`, `done`.
Pick the first `todo` task whose dependencies are all `done`. Set it to `in-progress` in the same commit that starts work.

Model routing: see `docs/agents/MODEL-ROUTING.md`. Each task names its author and reviewer model.

| ID | Title | Depends on | Gate | Needs device | Status |
|----|-------|-----------|------|--------------|--------|
| TASK-001 | Toolchain pinned and verified | - | 1 | no | todo |
| TASK-002 | Reproduce upstream build, authentic lint | 001 | 1 | no | todo |
| TASK-003 | Project skeleton and CI | 002 | 1 | no | todo |
| TASK-004 | Adapter extraction into modules | 003 | 1 | no | todo |
| TASK-005 | Rebrand and provenance | 004 | 8 | no | todo |
| TASK-006 | OpenRouter BYOK and secret security | 004 | 8 | no | todo |
| TASK-007 | Helper spike (Shizuku-derived) | 005 | 2 | yes | todo |
| TASK-008 | Wireless ADB pairing | 007 | 3 | yes | todo |
| TASK-009 | Accessibility merge | 004 | 4 | yes | todo |
| TASK-010 | Chrome CDP spike | 008 | 5 | yes | todo |
| TASK-011 | Virtual display spike | 007, 008 | 6 | yes | todo |
| TASK-012 | Action loop with Pause, Stop, takeover | 006, 009 | 7 | yes | todo |
| TASK-013 | Local Gemma (opportunistic, D-006) | 004, 006 | - | yes | todo |
| TASK-014 | SMS compose-only and permission narrowing | 004 | 8 | no | todo |
| TASK-015 | Study APK and guided onboarding | 007-014 | 1-8 | yes | todo |
| TASK-016 | Device matrix and exit review | 015 | all | yes | todo |

Code location (ADR-0002, supersedes assumption A-1): Android code lives in `android/` in this repo; tasks and orchestration live here. The A-1 question is confirmed by ADR-0002; no separate confirmation is needed before TASK-003.
