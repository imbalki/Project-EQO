# TASK-009: LLM fact extraction

- Status: todo
- Depends on: 006, 008
- Area: android
- Branch: agent/android/<issue>-fact-extraction

## Goal
After a conversation, extract durable facts with the LLM and store them; inject relevant facts into future prompts.

## Scope
`memory/FactExtractor`, prompt templates.

## Acceptance criteria
- [ ] Facts extracted and deduplicated
- [ ] Relevant facts retrieved and added to context
- [ ] Sensitive categories are not stored (health, financial, IDs)
- [ ] Unit tests with a fake `LlmClient`

## Notes
Read `../AGENT.md` and `../context/` first.
