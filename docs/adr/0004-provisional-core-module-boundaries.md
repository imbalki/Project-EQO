# ADR-0004: Provisional core module boundaries (layout A)

- Status: accepted
- Date: 2026-10-02
- Deciders: owner

## Context

OpenDroid's package graph has hard cycles that Gradle modules cannot express: `core.agent` <-> `core.llm` and `core.agent` <-> `accessibility`. TASK-004 extracts the Phase One base (D-001/D-002/D-006: agent loop, LLM providers, credential security, accessibility automation) into `:core-security`, `:core-llm`, `:core-agent`, `:platform-a11y`, and the cycles must be broken without rewriting moved code (move commits are verbatim and bisectable).

## Decision

**Layout A (owner, 2026-10-02)**: keep the DAG `:core-security` <- `:core-llm` <- `:core-agent` <- `:platform-a11y` and break the cycles with **split packages** and **relocation of small shared files**, never by editing moved code:

- `ActionRisk`, `ActionSchema`, `DeviceStateProvider`, `IntentClassifier`, `ChatErrorUiState` keep package `com.opendroid.ai.core.agent` but live physically in `:core-llm`.
- `ActionDispatcher` + `actions.base` + `UnknownActionDao`/`UnknownActionEntity` move verbatim into `:core-llm`.
- `SettingsRepository` (DataStore-only) moves into `:core-llm`.
- No fifth shared module and no interface refactor in this task.

The full list of files living in a module other than their package suggests is in `android/Phase-One/evidence/task-004-extraction-map.md` ("Module boundaries are provisional").

## Consequences

- `:core-llm` is a catch-all: it carries `data.*` and `actions.*` code besides LLM providers. Acceptable for the extraction; not the final architecture.
- Split packages across modules are legal on the JVM but confusing for tooling and for package renaming; TASK-005 renames packages to `ai.eqo.*` and must decide placement at the same time.
- Follow-up cleanup (open item): a shared `:core-model`/`:core-data` module or minimal interfaces to remove the catch-all and the split packages. Recorded as an open item; not done in TASK-004.
