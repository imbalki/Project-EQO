# Android task index (Phase 1)

Rewritten for the Phase One plan in `android/Phase-One/` (decisions D-001 to D-010). The earlier 15-task list assumed a from-scratch Kotlin app and is superseded.

Status values: `todo`, `in-progress`, `blocked`, `in-review`, `done`, `deferred`.
Pick the first `todo` task whose dependencies are all `done`. Set it to `in-progress` in the same commit that starts work. The live state of every task (and who is working on it) is in `docs/agents/PHASE-ONE-STATUS.md`; this table is the plan, that page is the truth.

Model routing: see `docs/agents/MODEL-ROUTING.md` and the "Decisions in force" section of `docs/agents/PHASE-ONE-STATUS.md`. Each task names its author and reviewer model.

| ID | Title | Depends on | Gate | Needs device | Status |
|----|-------|-----------|------|--------------|--------|
| TASK-001 | Toolchain pinned and verified | - | 1 | no | done |
| TASK-002 | Reproduce upstream build, authentic lint | 001 | 1 | no | done |
| TASK-003 | Project skeleton and CI | 002 | 1 | no | done |
| TASK-004 | Adapter extraction into modules | 003 | 1 | no | done |
| TASK-005 | Rebrand and provenance | 004 | 8 | no | done |
| TASK-006 | OpenRouter BYOK and secret security | 004 | 8 | no | done (acceptance criterion 5 not met: issue #44) |
| TASK-007 | Helper spike (Shizuku-derived) | 005 | 2 | yes | in-progress |
| TASK-008 | Wireless ADB pairing | 007 | 3 | yes | todo |
| TASK-009 | Accessibility merge | 004 | 4 | yes | done |
| TASK-010 | Chrome CDP spike | 008 | 5 | yes | todo |
| TASK-011 | Virtual display spike | 007, 008 | 6 | yes | deferred (D-008) |
| TASK-012 | Action loop with Pause, Stop, takeover | 006, 009 | 7 | yes | in-progress (also carries issue #50) |
| TASK-013 | Local Gemma (opportunistic, D-006) | 004, 006 | - | yes | deferred (D-009) |
| TASK-014 | SMS compose-only and permission narrowing | 004 | 8 | no | done |
| TASK-015 | Study APK and guided onboarding | 007, 008, 010, 012, 014 (011 deferred by D-008, 013 by D-009) | 1-5, 7, 8 | yes | todo |
| TASK-016 | Device matrix and exit review | 015 | all except 6 (deferred) | yes | todo |

Code location (ADR-0002, supersedes assumption A-1): Android code lives in `android/` in this repo; tasks and orchestration live here. The A-1 question is confirmed by ADR-0002; no separate confirmation is needed before TASK-003.
