# EQO Phase One — Decision Records (ADR-style)

Owner decisions recorded 2026-10-01. These supersede conflicting statements in the main repo's `AGENTS.md` / `docs/ARCHITECTURE.md` for Phase One; those documents will be reconciled when implementation starts, not silently edited.

---

## D-001 — Model strategy: OpenRouter BYOK primary, local models opportunistic

**Decision:** Phase One's primary model path is **OpenRouter bring-your-own-key** (user-supplied key, model selection, connection test — per the brief). **On-device models ride along** where OpenDroid's existing support works without separate engineering: Gemma 4 / LiteRT-LM inference is already implemented in the base (verified in source: `LLMProviderFactory.kt:43-71` selects `GemmaProvider`, `LiteRTLMProvider`, `HybridOnDeviceProvider`; `OnDeviceModelRegistry` + `ModelDownloadWorker` + `ModelArtifactIntegrity` (SHA-256 + engine-load verification) + `ModelStoragePaths`; README L61-67 On-Device Model Manager with Keystore-secured HF fetch and local `.task`/`.litertlm` import).

**EQO-specific local-model work is separate** — device-tier model advisory ("which model fits this phone"), rebranded download/import flows, and per-device recommendations → **Phase 2** unless it proves trivial during Phase One. OpenDroid already has `checkDeviceMemoryCompatibility` (`ModelDownloadWorker.kt:76`) and `OnDeviceLatencyProfile`, which is the natural foundation for the advisory.

**Rationale:** owner direction 2026-10-01 ("if opendroid has local model access for gemma or advise on model as per device we can add that too, if that needs separate work we can work on it in phase 2"). Keeps Phase One focused on the automation proof; local models are a capability bonus, not a gate.

## D-002 — App-integration connectors deferred to Phase 2

**Decision:** No integration-connector layer in Phase One. **Composio, OpenConnector, and ActivePieces are Phase 2.** Phase One completes tasks through Accessibility, wireless ADB, Chrome + CDP, and virtual display only.

**Phase 2 selection constraint (recorded now):** the owner requires open-source/free-only tooling — ActivePieces is self-hostable open source, Composio's hosted API has paid tiers, OpenConnector is a local/Hermes-side action framework. Evaluate against the free-only rule when Phase 2 planning starts.

## D-003 — Resolves the Phase One architecture conflict

**Decision:** For Phase One, the main repo's "offline-default Gemma + Composio" architecture statements are **superseded** by D-001/D-002: OpenRouter BYOK is the primary model path, Composio-class integrations are out of scope, and Gemma local inference is opportunistic reuse of the base's existing implementation.

**Status:** DECIDED (owner). Documentation reconciliation in `Project-EQO` (`AGENTS.md`, `docs/ARCHITECTURE.md`) is an implementation-start task.

## D-007 - Android 11 floor (minSdk 30); LiquidAI Leap SDK excluded

**Decision:** EQO's Android floor is **Android 11 = API 30 = minSdk 30** (previously Android 12 / minSdk 31). Android 10 is not acceptable: wireless-debugging pairing exists only from Android 11. The **LiquidAI Leap SDK is excluded from EQO entirely** - no EQO code, dependency or doc may use `ai.liquid.*` / `leap-sdk`, except the exclusion rule in `agents/android/context/CONSTRAINTS.md`. Donor/upstream values stay facts, not EQO requirements: ClosePaw's own `minSdk 31` (forced by its LiquidAI Leap SDK dependency) and OpenDroid's `minSdk 26`.

**Rationale:** owner decision 2026-10-01. Nothing built so far depends on Android 12; OpenDroid upstream ships minSdk 26; ClosePaw ships minSdk 31 only because of the proprietary LiquidAI Leap SDK, which EQO does not use; wireless debugging pairing exists from Android 11. Device gate: Android 11 on the owner's physical Realme Narzo 20 (Realme UI 2.0 - the OEM-skin device) plus Android 12 and 13 from emulator and/or physical devices, each result labelled emulator or physical.

**Status:** DECIDED (owner, 2026-10-01). Recorded as ADR-0003 (`docs/adr/0003-android-11-floor-and-no-leap-sdk.md`). Open items are recorded there, not decided here (LiteRT-LM/Gemma minSdk verification in TASK-013; donor code above API 30 surfaced by compile/lint in TASK-003/004/007).

---

## Open items (not yet decided)

| Item | Where |
|---|---|
| Helper rename strategy: full rename (clean branding, breaks stock clients) vs keep wire strings (smaller diff, upstream identity in code only) | `FEASIBILITY-REPORT.md` §4 trust-model paragraph |
| Exact Phase 2 scope ordering (local-model UX vs connectors vs new capabilities) | after Phase One study APK review (owner's sequencing) |
