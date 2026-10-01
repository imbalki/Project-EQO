# Constraints and non-goals

Superseded items from the earlier plan are replaced by decisions D-001 to D-006 (`Project-EQO-Android/Phase-One/DECISIONS.md`).

- One sideloaded APK. Android 11+ (minSdk 30; D-007, ADR-0003). The bundled Python bridge stays inert (D-005); no Termux calls.
- No LiquidAI Leap SDK (ai.liquid.*, leap-sdk) in EQO code, dependencies or docs except this exclusion rule.
- Phase 1 DOES include screen control via AccessibilityService, wireless-ADB helper, Chrome CDP and virtual display spikes. It does NOT include connectors (Composio etc.), server-side memory, cloud VM tier, desktop sync or wake word.
- Privacy default: nothing leaves the phone unless the user supplies an OpenRouter key and starts a task. Local Gemma is opportunistic (D-006).
- Never log message content, tokens or API keys.
- Store API keys in Android Keystore-backed encrypted storage.
- No subsidized key in the build; bring-your-own-key only.
- Sensitive or irreversible actions need user approval; user can pause, stop or take over at any time.
