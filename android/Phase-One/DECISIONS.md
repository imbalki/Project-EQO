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

## D-004 — Privileged helper naming: EQO branding at the UI level; app ID and permission strings renamed together (as corrected)

**Decision:** Resolves the open helper-rename item. The owner does not want a large rename effort in Phase One and accepts handling branding at the UI/UX level. Therefore: (1) every user-visible surface (setup screens, notifications, permission explanations, error copy) uses **EQO** naming only; (2) the **application-ID-bearing sites** listed in `FEASIBILITY-REPORT.md` §4 are renamed to EQO's package as a **Phase One spike item**, because the helper is expected to locate and trust its manager by app ID (`ShizukuService.java:146-148`) and EQO's APK has its own package name; (3) the **`moe.shizuku.manager.permission.*` permission strings are renamed in lockstep with the app ID**, because a one-sided rename breaks binder delivery between manager and helper; **internal class and package names may keep upstream names** for now. The developer gate G8 greps for `moe.shizuku.privileged.api`, `moe.shizuku.manager.permission.`, Shizuku icon hashes and the name "Shizuku", so the rename must satisfy it. *Correction (2026-10-01): the first version of this entry said permission strings keep their upstream wire strings; that conflicted with G8 and the developer-readiness notes.*

**Status:** DECIDED (owner approved this recommendation 2026-10-01). **Not yet proven:** nothing has been compiled or device-tested; the spike must show the helper starts and binds under EQO's app ID. Upstream names stay out of all UI; attribution lives in the Legal / Open-source notices area (see `FEASIBILITY-REPORT.md` §6).

## D-005 — Bundled ClosePaw Python bridge: keep in Phase One as a known deviation

**Decision:** The ClosePaw Python bridge asset (`res/raw/closepaw_bridge_py`, 13,715 B, packaged by the `copyClosePawBridge` preBuild task) is **not stripped in Phase One**. The owner's reasoning: it is already included, keeping it adds negligible size or cost, and removal engineering can be done separately if it proves worthwhile.

**Recorded deviation:** this conflicts with the main repo's "no Python on the phone" rule (`AGENTS.md`). Phase One must therefore (a) verify the app never launches or depends on the bridge or on Termux, and (b) add one line to the security review noting an unreviewed script ships in a privileged-helper app. The `termux_shell` tool stays disabled and the Termux RUN_COMMAND permission is not requested (TASK-014). Removal is **deferred, not rejected**; it moves to a later phase if (a) fails or the review asks for it.

**Status:** DECIDED (owner). Whether the bridge is runtime-optional remains **unproven** (`FEASIBILITY-REPORT.md` §5 item 5).

## D-006 — Local Gemma in the first build, if feasible

**Decision:** Refines D-001. The first study build should **include OpenDroid's existing Gemma / LiteRT-LM on-device path** where it works without separate engineering, with **OpenRouter BYOK remaining the primary path** (D-001). EQO-specific work (device-tier model advisory, rebranded download/import flows) stays in Phase 2 unless trivial.

**Condition:** no claim that local inference works is made until it is run on real devices; small on-device models can be weaker at tool calling, so the first device test must exercise at least one tool-calling task on the local model and record the result in `evidence/`.

**Status:** DECIDED (owner); feasibility to be confirmed on-device.

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
