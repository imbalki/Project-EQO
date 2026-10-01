# Architecture

> **Superseded in part.** Statements about Python, Composio, offline-default Gemma and Phase 1 scope are replaced by decisions D-001 to D-006 in `Project-EQO-Android/Phase-One/DECISIONS.md`.


## Products
1. Android app (Kotlin, Jetpack Compose): voice, SMS, Composio tools, on-device Gemma, Room memory.
2. Desktop app (Electron): full Node.js stack, later phases.
3. Server components (later phases): GBrain, Mem0 and other memory services.

Phone and desktop sync over local network or Tailscale.

## Phase 1 stack (Android)
- Agent loop: ~300 lines Kotlin, direct HTTP tool-calling.
- Models: Gemma E4B on-device (LiteRT); OpenRouter opt-in.
- Integrations: Composio hosted API.
- Memory: Room + LLM fact extraction.
- Input: mic button, no wake word.

## Decisions
Recorded in `docs/adr/`.
