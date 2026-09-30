# Android architecture (Phase 1)

```
UI (Compose)  ->  AssistantViewModel  ->  AgentLoop
                                            |-- LlmClient (interface)
                                            |     |-- OpenRouterClient (HTTP, opt-in)
                                            |     |-- LocalGemmaClient (LiteRT, default)
                                            |-- ToolRegistry
                                            |     |-- ComposioTools (meta tools over HTTP)
                                            |     |-- SmsTool
                                            |     |-- LocalTools (time, notes)
                                            |-- MemoryStore (Room) + FactExtractor
```

## Package layout under `android/app/src/main/java/app/eqo/`
```
ui/          Compose screens, theme, navigation
agent/       AgentLoop, Message types, tool-call parsing
llm/         LlmClient, OpenRouterClient, LocalGemmaClient
tools/       Tool interface, ToolRegistry, Composio, SMS
memory/      Room entities, DAOs, FactExtractor
voice/       Speech input, TTS
settings/    Key storage, model choice, cloud opt-in
di/          Wiring
```
Package name `app.eqo` is a placeholder; confirm in TASK-001.

## Rules
- The agent loop depends only on the `LlmClient` and `Tool` interfaces so it is testable without Android.
- Tool calls are JSON parsed by us; no framework.
- Every tool that acts outside the phone (send SMS, email, calendar write) needs user confirmation until the learning system removes it (later phase).
