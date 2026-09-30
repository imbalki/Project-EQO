# TASK-005: LlmClient interface and OpenRouter client

- Status: todo
- Depends on: 001
- Area: android
- Branch: agent/android/<issue>-llm-client

## Goal
Define `LlmClient` and implement an OpenAI-compatible OpenRouter client with streaming and tool-call support.

## Scope
`llm/` package. OkHttp, kotlinx.serialization. HTTP/1.1 fallback option if HTTP/2 fails.

## Acceptance criteria
- [ ] `LlmClient` interface is Android-free and unit-testable
- [ ] OpenRouter chat completion works against MockWebServer
- [ ] Streaming tokens delivered as Flow
- [ ] Errors (401, 429, timeout) mapped to typed results

## Notes
Read `../AGENT.md` and `../context/` first.
