# Model routing (OpenCode Go and MiMo API)

Provider `opencode-go`. Model IDs below are expected values; verify each with
`hermes -z "Reply with exactly: OK" --provider opencode-go -m <id>` before dispatching (TASK-001 / kickoff step).

| Activity | Model | Why |
|----------|-------|-----|
| Hard authoring: security-sensitive code, concurrency, helper/ADB/CDP spikes, integration | `mimo-v2.6-pro` | Strongest of the three liked models; $0.43/$0.87 per M tokens |
| Routine authoring: build scripts, CI, config, rebrand, tests, UI screens | `glm-5.3-flash` | Cheap ($0.15/$0.50) and fast |
| Review of mimo output | `glm-5.3-flash` | Different model family catches different mistakes |
| Review of glm output, security passes, race-test review; reviewer for major tasks | `mimo-v2.6-pro` | Reviewer should be the stronger model |
| Summaries, copy, docs and handoffs, log triage, index upkeep | `deepseek-v4.1-flash` | Cheap, fast flash-tier model (check the OpenCode Go dashboard for price). Replaces `mimo-v2.6-flash` (owner decision 2026-10-02) |

Rules
- The author never reviews its own work; each task file names author and reviewer.
- Gates are decided by command output and recordings, not by a model's opinion.
- Escalate to `glm-5.3` ($1/$4) only after two failed attempts by a flash model, and note why in the task.
- Plan caps (one source): about $12 per 5 hours, $30 per week, $60 per month. Check the OpenCode Go dashboard; run tasks serially if near the cap.
- No independent tool-calling benchmarks were found for these models; TASK-006 is the first bake-off and its result may change this table.
- `deepseek-v4.1-flash` needs the OpenCode Go workspace region set to Global (Privacy settings); otherwise the API returns HTTP 400 "requires Global regions". Owner action; record the date it was enabled here.
