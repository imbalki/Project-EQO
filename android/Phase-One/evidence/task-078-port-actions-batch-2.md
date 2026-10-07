# TASK-078 — action executor port, batch 2: notifications, macros, routines (Refs #20)

Branch `feat/batch2-notifications-macros-routines`, from `main` ad8be86. Donor: `yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51` (re-read from a fresh clone).
Decision record: `docs/adr/0005-room-database-and-nested-macro-steps.md`.

## Port map

| Donor | Destination | Registered actions |
|---|---|---|
| actions/NotificationActions.kt | NotificationActions.kt | READ_NOTIFICATIONS, AUTO_REPLY_TOGGLE, DISMISS_NOTIFICATION |
| actions/MacroActions.kt | MacroActions.kt | RUN_MACRO, CREATE_MACRO, SCHEDULE_MACRO, DELETE_MACRO, LIST_MACROS |
| actions/RoutineActions.kt | RoutineActions.kt | RUN_ROUTINE, DETECT_ROUTINES, APPROVE_ROUTINE |
| core/routine/HabitRoutineEngine.kt | HabitRoutineEngine.kt, RoutineDetection.kt, RoutineTemplates.kt | (engine, no actions) |
| data/db entities and DAOs | `:core-llm` `ai.eqo.data.db` (`MacroEntity`, `HabitEventEntity`, `HabitRoutineEntity`, `MacroDao`, `HabitDao`, `EqoDatabase`; `NotificationEntity`/`NotificationDao` already present) | |

11 new names in the existing registry (83 + 11 = 94). GET_MORNING_BRIEFING stays with batch 3. No second dispatcher or vocabulary: `ActionSchema` already held every name, and `RegistryPlanVocabulary` (PR #74/#77, not on `main` yet) builds the planner contract from `AndroidActionRegistry.enabledActionNames`, so these names reach the planner as soon as that PR lands.
`AndroidActionRegistry.createWithStore` builds the Room DAOs lazily (`RoomAutomationDaos`) and the auto-reply settings store lazily (`SettingsAutoReplyConfigStore`); both are replaceable through `RegistryOptions` for tests. No app-module change was needed.

## Deviations from the donor (each is a safety or honesty fix)

- Inner steps of macros/routines are limited to read-only/reversible actions (ADR-0005). The donor ran any action.
- CREATE_MACRO validates the step list (JSON, 1 to 20 steps, allowed actions), rejects duplicate or control-character names; the donor stored raw text.
- RUN_MACRO refuses a turned-off macro and vets all steps before dispatching the first. RUN_ROUTINE refuses PAUSED/DISMISSED routines.
- SCHEDULE_MACRO validates five-field cron text, refuses an unknown macro (the donor silently created an empty one) and says the schedule will not start by itself.
- AUTO_REPLY_TOGGLE treats a blank `app` as "all apps" (the schema default is "", which made the donor change nothing while reporting a state) and refuses unknown channels instead of reporting success.
- READ_NOTIFICATIONS clamps `count` to 1 to 50 (a negative number is "no limit" in SQLite) and its output, LIST_MACROS and DETECT_ROUTINES are fenced as untrusted text.
- APPROVE_ROUTINE refuses when another macro already has the routine's name.
- Not ported: the learned-patterns paragraph of READ_NOTIFICATIONS (needs the memory repository), knowledge-graph writes and the briefing text of the engine (batch 3 owns the briefing), routine UI flows, `recordAction`.

## What this branch does not do

- Nothing writes the `notifications` table (no notification listener service in EQO). `READ_NOTIFICATIONS` returns "No notifications found." until capture exists.
- Nothing registers `HabitRoutineEngine` as the accessibility `HabitRoutineTracker`, so no app-usage events are recorded; `DETECT_ROUTINES` finds nothing until the owner approves recording.
- Nothing runs scheduled macros.
- The suggested "Morning Routine" calls GET_MORNING_BRIEFING and READ_NOTES, which still refuse with "needs the database (batch 2)" because `ProductivityStore` has no adapter. A routine run therefore stops at the first of those steps (it is also preceded by LIST_CALENDAR_TODAY, which only opens Calendar). An adapter is the next step and was not part of this task.
- A macro's per-step output is not shown to the owner; like the donor, only the final "completed N steps" line returns.

## Verification

See the PR description for the CI run. This cloud session had no Android SDK and the Google Maven host was blocked by the network policy, so nothing in Gradle (compile, Robolectric tests, ktlint, detekt, lint) could be run locally; only `scripts/check.sh` (secret scan, branding gate, provenance map) ran locally and passed.

## Not testable without the phone

Real notification capture; the Room file on a real device (open, upgrade, process death); runtime permission flows for steps that request permissions inside a macro; the DataStore auto-reply settings on the device; real app-open events from the accessibility service; opening apps/Calendar from routine steps; anything about OEM behavior on the Realme RM10.
