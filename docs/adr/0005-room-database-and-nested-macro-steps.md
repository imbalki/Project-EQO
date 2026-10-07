# ADR-0005: EQO Room database and the limits on macro and routine steps

- Status: proposed (batch 2 PR, awaiting owner review)
- Date: 2026-10-06
- Deciders: owner

## Context

Batch 2 ports the OpenDroid notification, macro and routine executors. They need persistence that batches 1 to 3 left out (`ProductivityStore` was deliberately unavailable). The donor stored macros, routines and notifications in one large Room database (version 9, 20 tables, including social and chat tables EQO does not ship).

A saved macro or routine runs many actions after one approval of its own name. The planner contract says "No hidden steps, code or macros", and the owner approves a plan preview step by step. A macro whose stored steps send an SMS or tap the screen would run those steps without that preview.

## Decision

1. **One small database, `EqoDatabase` (`:core-llm`, package `ai.eqo.data.db`)**: four tables only (`macros`, `notifications`, `habit_events`, `habit_routines`), version 1, file `eqo_database` in app-private storage (backup is off). The donor migrations 1 to 9 are not carried over because they create tables EQO does not ship. Destructive migration fallback is never enabled; the first schema change must add an explicit migration. Schema export is off for version 1 and should be turned on with the first migration.
2. **The database opens on first use**, not at registry construction, so no executor opens it unless a notification/macro/routine action runs.
3. **Inner steps of macros and routines may only be read-only or reversible actions** (`NestedStepPolicy`). Refused: any `MACRO`-category action (no recursion or self-editing), anything `neverAutoApprove`, anything whose risk is `SENSITIVE` or higher (messages, calls, advanced control/tapping, money, smart home, transport) and `AUTO_REPLY_TOGGLE`. The same check runs when a macro is created, again before the first step of a run, and on every dispatch (including fallbacks). Inner steps go back through `AndroidActionRegistry`, so validation and permission requests still apply.
4. **Honest results**: `SCHEDULE_MACRO` saves the cron text but says plainly that nothing runs scheduled macros yet.

## Consequences

- A user-made macro that texts or calls someone is refused when created. The owner can relax the policy later, but only together with a per-run approval design.
- The notification table is never written by EQO today (the notification listener service is not ported), so `READ_NOTIFICATIONS` reports an empty history until a later task adds capture. The database is plaintext SQLite in private storage; capture of third-party message text should not ship before an encryption decision.
- `HabitRoutineEngine` can record foreground-app events but nothing registers it with the accessibility service, so no app-usage history is collected until the owner approves that.
