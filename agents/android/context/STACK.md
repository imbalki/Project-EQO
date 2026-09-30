# Stack

| Concern | Choice |
|---------|--------|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Min SDK | 29 (Android 10); confirm before locking |
| Build | Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`) |
| DI | Manual constructor injection or Koin; decide in TASK-001 (no Hilt/kapt overhead unless justified) |
| Networking | OkHttp + kotlinx.serialization |
| Local LLM | Gemma E4B via LiteRT (Google AI Edge) |
| Cloud LLM | OpenRouter, OpenAI-compatible `/chat/completions`, opt-in |
| Tools / integrations | Composio hosted API (meta tools: search_tools, authenticate_app, execute_tool) |
| Memory | Room (SQLite) + LLM fact extraction |
| Voice | Android SpeechRecognizer / on-device STT and TextToSpeech; mic button only |
| Lint / format | ktlint, detekt |
| Tests | JUnit5, MockK, Turbine, MockWebServer |
