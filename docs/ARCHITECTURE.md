# Architecture

> **Reconciled with Phase One decisions.** The statements below now follow decisions D-001 to D-006 in `android/Phase-One/DECISIONS.md`: OpenRouter bring-your-own-key is the primary model path, on-device Gemma is opportunistic, connectors (Composio and similar) are Phase 2, and the bundled Python bridge stays inert (D-005).


## Products
1. Android app (Kotlin, Jetpack Compose): voice, SMS, Composio tools, on-device Gemma, Room memory.
2. Desktop app (Electron): full Node.js stack, later phases.
3. Server components (later phases): GBrain, Mem0 and other memory services.

Phone and desktop sync over local network or Tailscale.

## Phase 1 stack (Android)
- Agent loop: ~300 lines Kotlin, direct HTTP tool-calling.
- Models: OpenRouter bring-your-own-key primary (D-001); on-device Gemma (LiteRT) opportunistic, reusing the base implementation where it already works (D-001, D-006).
- Integrations: none — tasks complete through accessibility, wireless ADB, Chrome CDP and virtual display. Connectors (Composio, OpenConnector, ActivePieces) are Phase 2 (D-002).
- Runtime: the bundled Python bridge stays inert (D-005); no new Node.js or Python runtime dependencies.
- Memory: Room + LLM fact extraction.
- Input: mic button, no wake word.

## Decisions
Phase One: `android/Phase-One/DECISIONS.md` (D-001 to D-006). Longer-lived: `docs/adr/`.
