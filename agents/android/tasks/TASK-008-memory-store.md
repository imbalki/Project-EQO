# TASK-008: Room memory store

- Status: todo
- Depends on: 001
- Area: android
- Branch: agent/android/<issue>-memory-store

## Goal
Persist conversations and facts locally with Room.

## Scope
`memory/` package: entities, DAOs, migrations, repository.

## Acceptance criteria
- [ ] Conversations and facts tables with DAOs
- [ ] Schema export enabled and committed
- [ ] Instrumented or Robolectric tests for DAOs
- [ ] Delete-all-data function for privacy

## Notes
Read `../AGENT.md` and `../context/` first.
