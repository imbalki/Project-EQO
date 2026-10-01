# TASK-004: Extract the OpenDroid base into EQO modules

- Status: todo
- Depends on: 003
- Area: android
- Gate: 1 (DEV-05)
- Models: author `mimo-v2.6-pro`, reviewer `glm-5.3-flash`
- Branch: agent/android/<issue>-extraction

## Goal
Move the parts of OpenDroid that EQO needs into clean modules, with provenance, without rewriting behavior in the same change.

## Scope
Modules: `:core-agent`, `:core-llm`, `:core-security`, `:platform-a11y`. Keep OpenDroid's Gemma / LiteRT-LM provider and model-download code in `:core-llm` as an optional, non-default provider (D-006). Drop or quarantine anything not in Phase One scope. Every moved file gets a provenance header (origin repo, commit, path). Do not import or depend on any ClosePaw module or code that requires the LiquidAI Leap SDK.

## Acceptance criteria
- [ ] Each module compiles with only its declared dependencies
- [ ] Ported OpenDroid unit tests run in the new layout; pass counts recorded, failures triaged not deleted
- [ ] One OkHttp major version at runtime (dependency convergence check)
- [ ] Move commits contain no behavior changes (bisectable)
- [ ] grep -rniE "liquid|leap-sdk|ai\.liquid" android/ --include=*.gradle --include=*.kts --include=*.kt --include=*.toml returns nothing

## Evidence required
Test counts before and after, dependency report.

## Notes
Provenance map is completed in TASK-005.
