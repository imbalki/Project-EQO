# Constraints and non-goals

- No embedded Node.js runtime. No Termux. One APK, single-click install.
- Phase 1 does NOT include: screen control via AccessibilityService (Phase 3), server-side GBrain (Phase 2), cloud VM tier (Phase 4), desktop sync, wake word.
- Privacy default: nothing leaves the phone unless the user enables cloud or connects an app.
- Never log message content, tokens or API keys.
- Store API keys in Android Keystore-backed encrypted storage, not plain SharedPreferences.
- Subsidized OpenRouter key for freemium is a build-time value injected in CI, never committed.
