# TASK-013: Local Gemma in the first build (opportunistic)

- Status: todo
- Depends on: 004, 006
- Area: android
- Gate: none (decision D-006)
- Models: author `glm-5.3-flash`, reviewer `mimo-v2.6-pro`
- Branch: agent/android/<issue>-local-gemma

## Goal
If OpenDroid's existing on-device Gemma / LiteRT-LM path works, ship it switched on beside OpenRouter. OpenRouter stays the primary path.

## Scope
Enable the existing model manager (download with integrity check, local import, device-memory check). No EQO-specific advisory or rebranded flows; those are Phase 2 unless trivial.

## Acceptance criteria
- [ ] A model downloads or imports and loads on a real device
- [ ] At least one tool-calling task runs on the local model; result (pass or fail) recorded in `Phase-One/evidence/`
- [ ] If it fails, the path is hidden behind a clear "experimental" label rather than removed

## Evidence required
Device test record with device model, RAM and model name.

## Notes
No claim that local inference works until this record exists.
