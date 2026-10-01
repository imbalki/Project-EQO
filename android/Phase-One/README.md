# EQO Android — Phase One

Phase One scope: prove the EQO daily-life assistant on Android 12+ — OpenDroid base, OpenRouter bring-your-own-key, Accessibility automation, **mandatory authenticated wireless ADB**, an **integrated EQO-branded privileged helper** (no separate third-party helper app), Chrome + CDP browser automation, virtual display, guided onboarding, approvals, Pause/Stop/takeover and verified outcomes — starting with feasibility and a first **study APK**.

Later phases are planned only after the study APK is reviewed on real devices.

## Deliverables

| File | What it is | Authoring agent / model |
|---|---|---|
| [`FEASIBILITY-REPORT.md`](FEASIBILITY-REPORT.md) | CTO synthesis: verdict, gaps, gates, ordered spikes | CTO (orchestration) |
| [`DECISIONS.md`](DECISIONS.md) | Owner scope decisions: model strategy (OpenRouter primary, local Gemma opportunistic, advisory UX = Phase 2), connectors = Phase 2 | owner + CTO |
| [`docs/PRD.md`](docs/PRD.md) | Product requirements with EQO-001… IDs, conflicts ledger, acceptance criteria | Product/UX — MiMo 2.6 Flash |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Source-cited architecture audit + proposed contracts | Architect — MiMo 2.6 Pro (read with [`team-reports/architect-controller-verification.md`](team-reports/architect-controller-verification.md)) |
| [`docs/USER-FLOWS.md`](docs/USER-FLOWS.md) | Complete onboarding / task / recovery user flows, states, microcopy | Product/UX — MiMo 2.6 Flash |
| [`docs/TEST-PLAN.md`](docs/TEST-PLAN.md) | Test matrices, priorities, device scenarios, release gates | QA — MiMo 2.6 Pro |

## Role-agent reports

Each role ran as a **separate agent with its own verified model session**. Reports are the raw evidence behind the deliverables:

- `team-reports/architect-report.md` — architecture audit (MiMo 2.6 Pro)
- `team-reports/architect-controller-verification.md` — CTO corrections to the architect's overstatements
- `team-reports/developer-readiness.md` — 17-task implementation backlog + lint gate analysis (MiMo 2.6 Pro)
- `team-reports/qa-feasibility-report.md` — CI/test audit (MiMo 2.6 Pro)
- `team-reports/security-review.md` — privacy, approvals, licensing, dependency terms (MiMo 2.6 Pro)
- `team-reports/integration-report.md` — single-APK branded helper feasibility: **feasible-with-conditions**, 411-line file:line change list (MiMo 2.6 Pro; first run timed out, preserved as `integration-prior-run-failure-record.md`)
- `team-reports/apk-review-report.md` + `apk-review-controller-verification.md` + `apk-review-hashes.txt` — static study-APK review of 4 upstream release artifacts: source build mandatory for EQO (MiMo 2.6 Pro)

## Evidence

- `evidence/evidence.json` — pinned commits, upstream CI runs/jobs, release assets, local toolchain state
- `evidence/ci-detail.json` — exact OpenDroid CI job breakdown at the inspected commit
- `evidence/lint-evidence.json` — lint job annotations (logs were 401; finding names unknown)

## Status

**Conditional GO for Phase One spikes; NO-GO for release.** See `FEASIBILITY-REPORT.md` §8 for the eight exit gates and §9 for the ordered spike plan. All seven role workstreams are complete (integration re-run and study-APK review included). No combined APK exists yet; nothing here is a runtime claim.
