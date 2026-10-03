# Model routing (2026-10-03)

Provider and model IDs below are expected values; verify each with
`hermes -z "Reply with exactly: OK" --provider <p> -m <id>` before dispatching.

Routing in force after the owner's 2026-10-03 decision and the N1 A/B trial:

| Activity | Model | Provider / profile | Why |
|----------|-------|--------------------|-----|
| New substantial authoring | `gpt-6.1-sol` | `openai-codex`, profile `eqo-trial` (fallback chain below) | Won the N1 A/B trial |
| Older cards created under the previous routing | `mimo-v2.6-pro` | `xiaomi`, profiles `eqo-core-dev` / `eqo-architect` | Still in use for cards already dispatched |
| Security passes | `mimo-v2.6-pro` | `xiaomi`, profile `eqo-security` | Strongest liked model for security-sensitive review |
| First independent review | `glm-5.3-flash` | `opencode-go`, profile `eqo-reviewer-glm` | A different model family from the author (Sol is OpenAI-family), so different mistakes are caught |
| Routine authoring: build scripts, CI, config, tests, UI screens | `glm-5.3-flash` | `opencode-go`, profiles `eqo-routine-dev` / `eqo-build-fixer` / `eqo-qa` | Cheap ($0.15/$0.50) and fast |
| Summaries, copy, docs, handoffs, log triage, index upkeep | `deepseek-v4.1-flash` | `opencode-go`, profile `eqo-docs` | Cheap, fast flash-tier model (see the workspace-region note) |

`eqo-trial` fallback chain (verified 2026-10-03 by breaking each link and reading the
usage file, which named `openai-codex`, then `xiaomi`, then `opencode-go`):
`gpt-6.1-sol` (`openai-codex`) -> MiMo 2.6 Pro (`xiaomi`) -> MiMo 2.6 Pro (`opencode-go`).
Only the owner's OpenAI login answers `gpt-6.1-sol`; the names `gpt-6.1` and `6.1-sol` are
rejected (HTTP 400, which does not trigger a fallback — see `PHASE-ONE-STATUS.md` section 8).

## N1 A/B trial result (owner, 2026-10-03)

The same task — N1 shape-based redaction in `LogRedactor` (issue #44, `:core-llm` only),
an identical brief — was given to two workers:

| Worker | Card | Outcome |
|--------|------|---------|
| GPT 6.1 Sol, profile `eqo-trial` | `t_3c04decf` | Finished in **49 min** (card started 10:19, completed 11:08 local; the worker reported start 10:20:30 / finish 11:07:09). Reported 269 `:core-llm` tests green, ktlint/detekt/branding clean, no PR (branch `agent/android/44-shape-redaction-sol`) |
| MiMo 2.6 Pro, profile `eqo-core-dev` | `t_5fde84fb` | **Stopped by the owner at 109 min, unfinished** (started 10:19, reclaimed 12:08 local). No PR (branch `agent/android/44-shape-redaction-mimo`) |

Decision: author new substantial work on **GPT 6.1 Sol** through `eqo-trial`. No routing
change is made for work already in flight.

## Rules

- The author never reviews its own work; each task file names author and reviewer. The first
  independent review is by the **other model family** (author Sol/OpenAI -> reviewer GLM;
  author GLM -> reviewer MiMo for the security pass).
- Gates are decided by real command output and recordings, not by a model's opinion.
- Task spec files for tasks completed before 2026-10-03 keep the models that were actually
  used (for example TASK-007 and TASK-012 ran on MiMo 2.6 Pro). The specs updated in this PR
  name the new routing for work not yet done.
- Escalate to `glm-5.3` ($1/$4) only after two failed attempts by a flash model, and note why
  in the task.
- Plan caps (one source): about $12 per 5 hours, $30 per week, $60 per month. Check the
  OpenCode Go dashboard; run tasks serially if near the cap.
- `deepseek-v4.1-flash` needs the OpenCode Go workspace region set to Global (Privacy
  settings); otherwise the API returns HTTP 400 "requires Global regions". Owner action;
  record the date it was enabled here.
