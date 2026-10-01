# Model routing (OpenCode Go only)

Provider `opencode-go`. Model IDs below are expected values; verify each with
`hermes -z "Reply with exactly: OK" --provider opencode-go -m <id>` before dispatching (TASK-001 / kickoff step).

| Activity | Model | Why |
|----------|-------|-----|
| Hard authoring: security-sensitive code, concurrency, helper/ADB/CDP spikes, integration | `mimo-v2.6-pro` | Strongest of the three liked models; $0.43/$0.87 per M tokens |
| Routine authoring: build scripts, CI, config, rebrand, tests, UI screens | `glm-5.3-flash` | Cheap ($0.15/$0.50) and fast |
| Review of mimo output | `glm-5.3-flash` | Different model family catches different mistakes |
| Review of glm output, security passes, race-test review | `mimo-v2.6-pro` | Reviewer should be the stronger model |
| Summaries, copy, log triage, index upkeep | `mimo-v2.6-flash` | Cheapest ($0.14/$0.28) |

Rules
- The author never reviews its own work; each task file names author and reviewer.
- Gates are decided by command output and recordings, not by a model's opinion.
- Escalate to `glm-5.3` ($1/$4) only after two failed attempts by a flash model, and note why in the task.
- Plan caps (one source): about $12 per 5 hours, $30 per week, $60 per month. Check the OpenCode Go dashboard; run tasks serially if near the cap.
- No independent tool-calling benchmarks were found for these models; TASK-006 is the first bake-off and its result may change this table.
