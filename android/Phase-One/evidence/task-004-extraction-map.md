# TASK-004 extraction map (Phase 0/1)

- Repository: imbalki/project-eqo, branch `agent/android/36-extraction`
- Source: `yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51` (verified `git rev-parse HEAD` in the read-only clone)
- Scope: modules `:core-agent`, `:core-llm`, `:core-security`, `:platform-a11y`
- Imports below are **script-computed** (`tools/gen-map.py` reads the upstream tree); decisions are human-edited.

## Decisions for the lead

1. **Issue/branch**: card body says Refs #9 / `9-extraction`; the 2026-10-02 brief says issue **#36** and branch `agent/android/36-extraction` (already created). Followed the brief: commits end `Refs #36`.
2. **Module dependency DAG (no cycles)**: `:core-security` <- `:core-llm` <- `:core-agent` <- `:platform-a11y`. The upstream graph has hard cycles (core.agent <-> core.llm, core.agent <-> accessibility) that Gradle cannot express; cycles are broken by **split packages** and **minimal interfaces** (both sanctioned by the brief), never by editing moved code in the move commits.
3. **Split package**: `ActionRisk.kt`, `ActionSchema.kt`, `DeviceStateProvider.kt`, `IntentClassifier.kt` keep their `com.opendroid.ai.core.agent` package but physically live in `:core-llm`, because `LLMProviderFactory`/`ProviderSelectionPolicy`/prompts (in-scope llm) import them while 12 other agent files import llm types. JVM allows split packages across modules; renaming to `ai.eqo` is TASK-005. This keeps every moved file byte-identical to upstream (bisectable).
4. **SettingsRepository moves into `:core-llm`** (not an interface): it is DataStore-only (no Room/DAO), 351 lines, and is consumed by 19 moved files (all providers); an interface would touch 19 files for no compile-win. Its own deps (core.security, data.models) are both in the DAG below it. **Review point**: it is `data.*` app-layer code living in a core module; TASK-005 may relocate it.
5. **Room-backed dependencies**: (a) files that are *only* Room annotations + pure types/state (`ModelDao`, `UnknownActionDao`, `NotificationDao`, `UnknownActionEntity`, `NotificationEntity`, `ModelEntity` incl. the `ModelStatus` enum + `markDownload*` extensions) are **moved verbatim** — annotated interfaces/data classes compile with `androidx.room:room-runtime` alone (no Room KSP, SQL strings untouched), preserving behavior exactly; (b) concrete classes needing a live `OpenDroidDatabase` (`ConversationRepository`, `PlanRepository`) and `MemoryManager` become **minimal interfaces** (Phase 2 `refactor(android): adapter ...` commits, listed under Non-move changes). `OpenDroidDatabase` + the remaining Room layer are QUARANTINED (not in Phase One scope; no moved file needs them directly).
6. **ActionDispatcher + actions.base + UnknownActionDao/Entity move verbatim into `:core-llm`** (not :core-agent, as first drafted): `LLMProviderFactory` (in-scope llm) imports `ActionDispatcher`, and agent files import llm types, so a module `:core-llm`-atop-`:core-agent` placement would create a cycle. ActionDispatcher is 331 lines with no Android framework beyond Context/Log; the four files move as a unit (upstream behavior unchanged).
7. **:platform-a11y depends on :core-agent (and transitively :core-llm,:core-security)** so `OpenDroidAccessibilityService` can keep its `AgentLoop`/`AgentState`/`SettingsRepository` imports in the move commit (no behavior change). If the dependency weight (litertlm/mlkit coming along transitively) is unacceptable, the Phase-2 alternative is a `SettingsRepository`-lite interface in :platform-a11y — flagged, not decided.
8. **`R` usage in moved code**: `OpenDroidAccessibilityService` and `ModelDownloadForegroundInfoFactory` import `com.opendroid.ai.R`. Handling happens in Phase 2 (module-local resources vs resource-provider interface); move commits carry the files as-is (they cannot compile until resolved — expected for move commits).
9. **LiteRT-LM / ML Kit GenAI stay as declared deps of `:core-llm` only** (D-006 optional non-default provider): GemmaProvider, LiteRTLMProvider, HybridOnDeviceProvider, OnDeviceModelRegistry etc. keep their imports. No `ai.liquid.*` anywhere (gate grep).
10. Dependencies that are Android framework or third-party (Hilt, KSP, Datastore, OkHttp BOM 5.4.0, Retrofit, kotlinx-serialization, WorkManager, coroutines, junit, MockWebServer) are declared per module in Phase 2 exactly at the upstream versions; version catalog is the single source.

## Per-file decisions (in-scope packages)

Columns: file (repo-relative) | decision | one-line reason | imports of OTHER app packages (script-computed).

### com.opendroid.ai.core.agent -> :core-agent  (agent loop + planning/execution (TASK-004 scope))

| File | Decision | Reason | App imports (other packages) |
|---|---|---|---|
| `core/agent/ActionRisk.kt` | MOVE (verbatim, split-package into :core-llm) | see Decision 3 (cycle break: llm files import it) | - |
| `core/agent/ActionSchema.kt` | MOVE (verbatim, split-package into :core-llm) | see Decision 3 (cycle break: llm files import it) | - |
| `core/agent/ActionSequenceExecutor.kt` | MOVE (verbatim) | in-scope package | `actions.base.ActionResult`, `core.crash.CrashLogRedactor`, `data.models.PlanStep`, `data.models.StepStatus` |
| `core/agent/AgentLoop.kt` | MOVE (verbatim) | in-scope package | `actions.ActionDispatcher`, `actions.base.ActionResult`, `core.llm.LLMProviderFactory`, `core.llm.LLMRequest`, `core.llm.LLMResponse`, `core.llm.LatencyBudgetStatus`, `core.llm.ResponseFormat`, `core.llm.error.LLMException`, `core.llm.prompts.PlanningPrompts`, `core.memory.ExecutionHistoryPrivacy`, `core.memory.MemoryManager`, `core.util.NetworkErrorFormatter`, `data.models.AutoMode`, `data.models.ChatMessage`, `data.models.Plan`, `data.models.PlanStatus`, `data.models.PlanStep`, `data.models.StepStatus`, `data.models.approvalSettings`, `data.models.effectiveGrantedActions`, `data.models.resolvedAutoMode`, `data.repository.ConversationRepository` |
| `core/agent/AliasResolver.kt` | MOVE (verbatim) | in-scope package | `core.util.DurationParser` |
| `core/agent/AutoApprovalPolicy.kt` | MOVE (verbatim) | in-scope package | `data.models.AutoMode`, `data.models.Plan`, `data.models.PlanStep` |
| `core/agent/AutoReplyEngine.kt` | MOVE (verbatim) | in-scope package | `core.llm.LLMProviderFactory`, `core.llm.LLMRequest`, `core.llm.ResponseFormat`, `core.llm.prompts.AutoReplyPrompts`, `core.memory.MemoryManager`, `core.memory.NotificationIntelligence`, `data.db.dao.NotificationDao`, `data.db.entities.NotificationEntity`, `data.models.AutoReplyConfig`, `data.repository.SettingsRepository` |
| `core/agent/ChatErrorUiState.kt` | MOVE (verbatim, split-package into :core-llm) | see Decision 3 (cycle break: llm files import it) | `core.llm.error.LLMError`, `core.llm.error.LLMException`, `core.llm.error.RedactedDetail` |
| `core/agent/ContactResolver.kt` | MOVE (verbatim) | in-scope package | `core.memory.MemoryManager` |
| `core/agent/DeviceStateProvider.kt` | MOVE (verbatim, split-package into :core-llm) | see Decision 3 (cycle break: llm files import it) | - |
| `core/agent/IntentClassifier.kt` | MOVE (verbatim, split-package into :core-llm) | see Decision 3 (cycle break: llm files import it) | `core.llm.LLMProviderFactory`, `core.llm.LLMRequest`, `core.llm.ResponseFormat`, `data.models.ChatMessage` |
| `core/agent/PlanManager.kt` | MOVE (verbatim) | in-scope package | `core.memory.WorkingMemory`, `data.models.Plan`, `data.models.PlanStatus`, `data.models.PlanStep`, `data.models.StepStatus`, `data.repository.PlanRepository` |
| `core/agent/PlanValidator.kt` | MOVE (verbatim) | in-scope package | `actions.ActionDispatcher`, `data.db.dao.UnknownActionDao`, `data.db.entities.UnknownActionEntity`, `data.models.Plan`, `data.models.PlanStep` |
| `core/agent/ReEvaluationEngine.kt` | MOVE (verbatim) | in-scope package | `actions.ActionDispatcher`, `core.llm.LLMProviderFactory`, `core.llm.LLMRequest`, `core.llm.ProviderSelectionPolicy`, `core.llm.ResponseFormat`, `core.llm.error.LLMError`, `core.llm.error.LLMErrorMapper`, `core.llm.error.LLMException`, `core.llm.prompts.ReEvalPrompts`, `data.db.dao.UnknownActionDao`, `data.db.entities.UnknownActionEntity`, `data.models.Plan`, `data.models.PlanStep`, `data.models.StepStatus` |
| `core/agent/ReplyDispatcher.kt` | MOVE (verbatim) | in-scope package | `core.util.DeviceCapabilities` |
| `core/agent/VisionEngine.kt` | MOVE (verbatim) | in-scope package | `accessibility.OpenDroidAccessibilityService`, `core.llm.LLMProviderFactory`, `core.llm.LLMRequest`, `core.llm.ResponseFormat`, `data.models.ChatMessage` |

### com.opendroid.ai.core.llm -> :core-llm  (LLM providers incl. Gemma/LiteRT-LM + model download (D-006, non-default))

| File | Decision | Reason | App imports (other packages) |
|---|---|---|---|
| `core/llm/ClaudeModelCatalog.kt` | MOVE (verbatim) | in-scope package | - |
| `core/llm/ConnectionTest.kt` | MOVE (verbatim) | in-scope package | `data.models.LLMConfig`, `data.models.selectedModelFor` |
| `core/llm/LLMProvider.kt` | MOVE (verbatim) | in-scope package | `data.models.ChatMessage` |
| `core/llm/LLMProviderFactory.kt` | MOVE (verbatim) | in-scope package | `actions.ActionDispatcher`, `core.agent.ActionSchema`, `core.agent.DeviceStateProvider`, `core.agent.IntentClassifier`, `core.agent.QueryComplexity`, `data.models.LLMConfig`, `data.models.selectedModelFor`, `data.repository.SettingsRepository` |
| `core/llm/LiteRtCompatibility.kt` | MOVE (verbatim) | in-scope package | - |
| `core/llm/ModelArtifactIntegrity.kt` | MOVE (verbatim) | in-scope package | - |
| `core/llm/ModelDownloadForegroundInfoFactory.kt` | MOVE (verbatim) | in-scope package | `MainActivity` |
| `core/llm/ModelDownloadRetryPolicy.kt` | MOVE (verbatim) | in-scope package | `data.db.entities.ModelStatus` |
| `core/llm/ModelDownloadStopReason.kt` | MOVE (verbatim) | in-scope package | - |
| `core/llm/ModelDownloadWorkRequest.kt` | MOVE (verbatim) | in-scope package | - |
| `core/llm/ModelDownloadWorker.kt` | MOVE (verbatim) | in-scope package | `core.security.CredentialStoreResult`, `core.security.ProviderCredentialId`, `core.security.ProviderCredentialStore`, `data.db.dao.ModelDao`, `data.db.dao.markDownloadFailed`, `data.db.dao.markDownloadReady`, `data.db.entities.ModelStatus` |
| `core/llm/ModelFetcher.kt` | MOVE (verbatim) | in-scope package | `core.util.UrlUtils`, `data.repository.SettingsRepository` |
| `core/llm/ModelManager.kt` | MOVE (verbatim) | in-scope package | - |
| `core/llm/ModelStoragePaths.kt` | MOVE (verbatim) | in-scope package | - |
| `core/llm/OnDeviceLatencyProfile.kt` | MOVE (verbatim) | in-scope package | - |
| `core/llm/OnDeviceModelRegistry.kt` | MOVE (verbatim) | in-scope package | - |
| `core/llm/PromptBudget.kt` | MOVE (verbatim) | in-scope package | - |
| `core/llm/ProviderCatalog.kt` | MOVE (verbatim) | in-scope package | - |
| `core/llm/ProviderSelectionPolicy.kt` | MOVE (verbatim) | in-scope package | `core.agent.ActionRisk`, `core.agent.ActionRiskPolicy` |
| `core/llm/error/LLMError.kt` | MOVE (verbatim) | in-scope package | `core.llm.ProviderCatalog` |
| `core/llm/error/ProviderErrorDetail.kt` | MOVE (verbatim) | in-scope package | `core.llm.LLMRequest` |
| `core/llm/prompts/AutoReplyPrompts.kt` | MOVE (verbatim) | in-scope package | - |
| `core/llm/prompts/PlanningPrompts.kt` | MOVE (verbatim) | in-scope package | `core.agent.ActionSchema` |
| `core/llm/prompts/ReEvalPrompts.kt` | MOVE (verbatim) | in-scope package | - |
| `core/llm/prompts/SystemPrompts.kt` | MOVE (verbatim) | in-scope package | `core.agent.ActionSchema` |
| `core/llm/providers/ClaudeProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `core.llm.error.LLMException`, `core.llm.error.ProviderErrorDetail`, `core.llm.error.toSafeProviderException`, `data.models.resolveClaudeModelOrNull`, `data.repository.SettingsRepository` |
| `core/llm/providers/CohereProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `core.llm.error.ProviderErrorDetail`, `core.llm.error.toSafeProviderException`, `data.repository.SettingsRepository` |
| `core/llm/providers/CopilotProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `core.llm.error.ProviderErrorDetail`, `core.llm.error.toSafeProviderException`, `core.util.UrlUtils`, `data.repository.SettingsRepository` |
| `core/llm/providers/CustomOpenAIProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `core.llm.error.ProviderErrorDetail`, `core.llm.error.toSafeProviderException`, `core.util.UrlUtils`, `data.repository.SettingsRepository` |
| `core/llm/providers/DeepSeekProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `core.llm.error.ProviderErrorDetail`, `core.llm.error.toSafeProviderException`, `data.repository.SettingsRepository` |
| `core/llm/providers/GeminiProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `core.llm.error.ProviderErrorDetail`, `core.llm.error.toSafeProviderException`, `data.repository.SettingsRepository` |
| `core/llm/providers/GemmaProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `data.models.ChatMessage`, `data.models.selectedModelFor`, `data.repository.SettingsRepository` |
| `core/llm/providers/GroqProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `core.llm.error.ProviderErrorDetail`, `core.llm.error.toSafeProviderException`, `data.repository.SettingsRepository` |
| `core/llm/providers/HybridOnDeviceProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `data.models.ChatMessage`, `data.models.selectedModelFor`, `data.repository.SettingsRepository` |
| `core/llm/providers/LiteRTLMProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `data.models.ChatMessage`, `data.models.selectedModelFor`, `data.repository.SettingsRepository` |
| `core/llm/providers/MistralProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `core.llm.error.ProviderErrorDetail`, `core.llm.error.toSafeProviderException`, `data.repository.SettingsRepository` |
| `core/llm/providers/OllamaProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `core.llm.error.ProviderErrorDetail`, `core.llm.error.toSafeProviderException`, `core.util.NetworkErrorFormatter`, `core.util.UrlUtils`, `data.repository.SettingsRepository` |
| `core/llm/providers/OpenAIProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `core.llm.error.ProviderErrorDetail`, `core.llm.error.toSafeProviderException`, `data.repository.SettingsRepository` |
| `core/llm/providers/OpenRouterProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `core.llm.error.ProviderErrorDetail`, `core.llm.error.toSafeProviderException`, `data.repository.SettingsRepository` |
| `core/llm/providers/TogetherAIProvider.kt` | MOVE (verbatim) | in-scope package | `core.llm.`, `core.llm.error.ProviderErrorDetail`, `core.llm.error.toSafeProviderException`, `data.repository.SettingsRepository` |

### com.opendroid.ai.core.security -> :core-security  (credential security + redaction)

| File | Decision | Reason | App imports (other packages) |
|---|---|---|---|
| `core/security/KeystoreSecretStorage.kt` | MOVE (verbatim) | in-scope package | - |
| `core/security/LegacyPreferenceMigration.kt` | MOVE (verbatim) | in-scope package | `core.settings.AppSettingsStore` |
| `core/security/LegacySecurePreferenceInventory.kt` | MOVE (verbatim) | in-scope package | `core.settings.AppSettingsStore` |
| `core/security/ProviderCredentialStore.kt` | MOVE (verbatim) | in-scope package | - |
| `core/security/SensitiveMemoryStore.kt` | MOVE (verbatim) | in-scope package | - |
| `core/security/SocialCredentialStore.kt` | MOVE (verbatim) | in-scope package | `social.domain.model.SocialCredentials`, `social.domain.model.SocialPlatform` |
| `core/security/UserProfileStore.kt` | MOVE (verbatim) | in-scope package | - |

### com.opendroid.ai.accessibility -> :platform-a11y  (accessibility automation)

| File | Decision | Reason | App imports (other packages) |
|---|---|---|---|
| `accessibility/AccessibilityNodeTraversal.kt` | MOVE (verbatim) | in-scope package | - |
| `accessibility/CallFlowVerifier.kt` | MOVE (verbatim) | in-scope package | - |
| `accessibility/GenericAppAutomator.kt` | MOVE (verbatim) | in-scope package | - |
| `accessibility/OpenDroidAccessibilityService.kt` | MOVE (verbatim) | in-scope package | `R`, `core.agent.AgentLoop`, `core.agent.AgentState`, `core.service.OpenDroidService`, `data.repository.SettingsRepository` |
| `accessibility/SmsAutomator.kt` | MOVE (verbatim) | in-scope package | - |
| `accessibility/TelegramAutomator.kt` | MOVE (verbatim) | in-scope package | - |
| `accessibility/WhatsAppAutomator.kt` | MOVE (verbatim) | in-scope package | - |

## External files needed by moved code

| File | Decision | Reason | Imported by (in-scope) |
|---|---|---|---|
| `com/opendroid/ai/actions/ActionDispatcher.kt` | MOVE (verbatim) -> :core-llm | executor used by AgentLoop/PlanValidator/ReEvaluationEngine AND LLMProviderFactory (llm) - must sit below :core-agent, so it lives in :core-llm (cycle break) | imported by 25 moved file(s) |
| `com/opendroid/ai/actions/base/Action.kt` | MOVE (verbatim) -> :core-llm | contract used by ActionDispatcher + moved code | imported by 21 moved file(s) |
| `com/opendroid/ai/actions/base/ActionResult.kt` | MOVE (verbatim) -> :core-llm | contract used by ActionDispatcher + ActionSequenceExecutor + moved code | imported by 21 moved file(s) |
| `com/opendroid/ai/core/agent/ActionRisk.kt` | MOVE (verbatim) -> :core-llm | agent contract (ActionRisk/ActionRiskPolicy) imported by LLMProviderFactory+ProviderSelectionPolicy; split-package into :core-llm to break llm<->agent cycle | imported by 22 moved file(s) |
| `com/opendroid/ai/core/agent/ActionSchema.kt` | MOVE (verbatim) -> :core-llm | agent contract imported by LLMProviderFactory + prompts; split-package into :core-llm (cycle break) | imported by 22 moved file(s) |
| `com/opendroid/ai/core/agent/DeviceStateProvider.kt` | MOVE (verbatim) -> :core-llm | agent contract imported by LLMProviderFactory; split-package into :core-llm (cycle break) | imported by 22 moved file(s) |
| `com/opendroid/ai/core/agent/IntentClassifier.kt` | MOVE (verbatim) -> :core-llm | agent class imported by LLMProviderFactory; split-package into :core-llm (cycle break) | imported by 22 moved file(s) |
| `com/opendroid/ai/core/util/DeviceCapabilities.kt` | MOVE (verbatim) -> :core-llm | small util used by 7 moved files | imported by 12 moved file(s) |
| `com/opendroid/ai/core/util/DurationParser.kt` | MOVE (verbatim) -> :core-llm | small util, used by moved code | imported by 12 moved file(s) |
| `com/opendroid/ai/core/util/NetworkErrorFormatter.kt` | MOVE (verbatim) -> :core-llm | small util, used by moved providers | imported by 12 moved file(s) |
| `com/opendroid/ai/core/util/UrlUtils.kt` | MOVE (verbatim) -> :core-llm | small util, used by 4 moved files | imported by 12 moved file(s) |
| `com/opendroid/ai/data/db/dao/ModelDao.kt` | MOVE (verbatim) -> :core-llm | pure Room-annotated interface used directly by ModelDownloadWorker; compiles with room-runtime, no Room KSP needed (SQL untouched) | imported by 24 moved file(s) |
| `com/opendroid/ai/data/db/dao/NotificationDao.kt` | MOVE (verbatim) -> :core-llm | pure Room-annotated interface used by AutoReplyEngine; compiles with room-runtime only | imported by 24 moved file(s) |
| `com/opendroid/ai/data/db/dao/UnknownActionDao.kt` | MOVE (verbatim) -> :core-llm | pure Room-annotated interface used by ActionDispatcher/PlanValidator/ReEvaluationEngine | imported by 24 moved file(s) |
| `com/opendroid/ai/data/db/entities/ModelEntity.kt` | MOVE (verbatim) -> :core-llm | contains pure ModelStatus enum + markDownload* extensions used by ModelDownloadRetryPolicy/Worker (Room @Entity class rides along) | imported by 37 moved file(s) |
| `com/opendroid/ai/data/db/entities/NotificationEntity.kt` | MOVE (verbatim) -> :core-llm | pure Room-annotated data class behind NotificationDao | imported by 37 moved file(s) |
| `com/opendroid/ai/data/db/entities/UnknownActionEntity.kt` | MOVE (verbatim) -> :core-llm | pure Room-annotated data class behind UnknownActionDao | imported by 37 moved file(s) |
| `com/opendroid/ai/data/models/AutoMode.kt` | MOVE (verbatim) -> :core-llm | model used by moved code | imported by 52 moved file(s) |
| `com/opendroid/ai/data/models/AutoReplyConfig.kt` | MOVE (verbatim) -> :core-llm | model used by moved code | imported by 52 moved file(s) |
| `com/opendroid/ai/data/models/ChatMessage.kt` | MOVE (verbatim) -> :core-llm | model used by 7 moved files | imported by 52 moved file(s) |
| `com/opendroid/ai/data/models/HabitEvent.kt` | MOVE (verbatim) -> :core-llm | model used by moved code | imported by 52 moved file(s) |
| `com/opendroid/ai/data/models/HabitRoutine.kt` | MOVE (verbatim) -> :core-llm | model used by moved code | imported by 52 moved file(s) |
| `com/opendroid/ai/data/models/LLMConfig.kt` | MOVE (verbatim) -> :core-llm | model used by providers/factory | imported by 52 moved file(s) |
| `com/opendroid/ai/data/models/Macro.kt` | MOVE (verbatim) -> :core-llm | model used by moved code | imported by 52 moved file(s) |
| `com/opendroid/ai/data/models/Memory.kt` | MOVE (verbatim) -> :core-llm | model used by moved code | imported by 52 moved file(s) |
| `com/opendroid/ai/data/models/Plan.kt` | MOVE (verbatim) -> :core-llm | model used by 5 moved files | imported by 52 moved file(s) |
| `com/opendroid/ai/data/models/PlanStatus.kt` | MOVE (verbatim) -> :core-llm | model used by moved code | imported by 52 moved file(s) |
| `com/opendroid/ai/data/models/PlanStep.kt` | MOVE (verbatim) -> :core-llm | model used by 6 moved files | imported by 52 moved file(s) |
| `com/opendroid/ai/data/models/StepResult.kt` | MOVE (verbatim) -> :core-llm | model used by moved code | imported by 52 moved file(s) |
| `com/opendroid/ai/data/models/UserProfile.kt` | MOVE (verbatim) -> :core-llm | model used by moved code | imported by 52 moved file(s) |
| `com/opendroid/ai/data/repository/SettingsRepository.kt` | MOVE (verbatim) -> :core-llm | DataStore-only (no Room); used by 19 moved files incl. all providers | imported by 51 moved file(s) |
| `com/opendroid/ai/core/crash/CrashLogRedactor.kt` | MOVE (verbatim) -> :core-agent | small pure redactor used by AgentLoop + ActionSequenceExecutor | imported by 8 moved file(s) |
| `com/opendroid/ai/core/memory/ExecutionHistoryPrivacy.kt` | MOVE (verbatim) -> :core-agent | small pure redaction helper used by AgentLoop | imported by 15 moved file(s) |
| `com/opendroid/ai/core/memory/WorkingMemory.kt` | MOVE (verbatim) -> :core-agent | small pure class used by PlanManager | imported by 15 moved file(s) |
| `com/opendroid/ai/core/settings/AppSettingsStore.kt` | MOVE (verbatim) -> :core-security | DataStore store used by LegacyPreferenceMigration + LegacySecurePreferenceInventory (security in-scope) | imported by 6 moved file(s) |
| `com/opendroid/ai/social/domain/model/SocialModels.kt` | MOVE (verbatim) -> :core-security | models used by SocialCredentialStore (security in-scope) | imported by 33 moved file(s) |
| `com/opendroid/ai/social/domain/model/SocialPlatform.kt` | MOVE (verbatim) -> :core-security | model used by SocialCredentialStore (security in-scope) | imported by 33 moved file(s) |
| `com/opendroid/ai/MainActivity.kt` | ADAPTER (Phase 2 interface; NOT copied) | UI (out of scope); used by ModelDownloadForegroundInfoFactory only for notification tap Intent -> injectable NotificationTapTarget (Phase 2) | - |
| `com/opendroid/ai/core/memory/MemoryManager.kt` | ADAPTER (Phase 2 interface; NOT copied) | Room/repository-backed (369 lines); used by AgentLoop/AutoReplyEngine/ContactResolver -> minimal interface MemoryStore | - |
| `com/opendroid/ai/core/service/OpenDroidService.kt` | ADAPTER (Phase 2 interface; NOT copied) | full service component; used by OpenDroidAccessibilityService -> minimal interface ServiceBridge (Phase 2) | - |
| `com/opendroid/ai/data/repository/ConversationRepository.kt` | ADAPTER (Phase 2 interface; NOT copied) | Room-backed (OpenDroidDatabase/DAO); used by AgentLoop -> minimal interface ChatHistoryStore | - |
| `com/opendroid/ai/data/repository/PlanRepository.kt` | ADAPTER (Phase 2 interface; NOT copied) | Room-backed (PlanDao/PlanEntity); used by PlanManager -> minimal interface PlanStore | - |
| `com/opendroid/ai/core/memory/NotificationIntelligence.kt` | QUARANTINE (not copied) | used by AutoReplyEngine for notification smarts; 206 lines, pulls Room; reviewed as Phase-2 candidate, for now quarantined behind NotificationStore | - |

## Not moved (out of Phase One scope)

Packages entirely unchanged and NOT copied (quarantined by exclusion; TASK-005 provenance map records them): `ui/**`, `di/**` (Hilt wiring for the upstream app, rewritten in EQO anyway), `data/db/**` (Room db + remaining daos/entities), `data/repository/{Memory,Social}Repository`, `data/crash/**`, `core/memory/**` (except WorkingMemory + ExecutionHistoryPrivacy), `core/memory/graph/**`, `core/voice/**`, `core/routine/**`, `core/storage/**`, `core/permissions/**`, `core/service/**`, `core/crash/**` (except CrashLogRedactor), `core/settings/**` (except AppSettingsStore), `actions/**` (except ActionDispatcher + base), `social/**` (except the two model files), root `MainActivity`/`OpenDroidApp`/application classes, `assets/`, `res/`.

## Module boundaries are provisional (owner decision 2026-10-02, ADR-0004)

Layout A is accepted as-is for TASK-004, but several files live in a module other than their package/folder name suggests. This section is the follow-up cleanup list (shared module or minimal interfaces); it is generated from the moved tree (`package` header vs module home package) and covers all 37 such files. A later cleanup task (recorded as an open item in ADR-0004) should revisit them together with the TASK-005 package rename.

| File (repo-relative under `src/main/java/`) | Module it lives in | Its package | Why it is there |
|---|---|---|---|
| `com/opendroid/ai/core/agent/ActionRisk.kt` | :core-llm | `com.opendroid.ai.core.agent` | split package: `LLMProviderFactory`/`ProviderSelectionPolicy` import it (Decision 3) |
| `com/opendroid/ai/core/agent/ActionSchema.kt` | :core-llm | `com.opendroid.ai.core.agent` | split package: `LLMProviderFactory` + prompts import it (Decision 3) |
| `com/opendroid/ai/core/agent/ChatErrorUiState.kt` | :core-llm | `com.opendroid.ai.core.agent` | split package: llm error handling imports it (Decision 3) |
| `com/opendroid/ai/core/agent/DeviceStateProvider.kt` | :core-llm | `com.opendroid.ai.core.agent` | split package: `LLMProviderFactory` imports it (Decision 3) |
| `com/opendroid/ai/core/agent/IntentClassifier.kt` | :core-llm | `com.opendroid.ai.core.agent` | split package: `LLMProviderFactory` imports it (Decision 3) |
| `com/opendroid/ai/actions/ActionDispatcher.kt` | :core-llm | `com.opendroid.ai.actions` | cycle break: `LLMProviderFactory` (llm) and agent files both use it (Decision 6) |
| `com/opendroid/ai/actions/base/Action.kt` | :core-llm | `com.opendroid.ai.actions.base` | contract for ActionDispatcher, moved as a unit (Decision 6) |
| `com/opendroid/ai/actions/base/ActionResult.kt` | :core-llm | `com.opendroid.ai.actions.base` | contract for ActionDispatcher, moved as a unit (Decision 6) |
| `com/opendroid/ai/core/util/DeviceCapabilities.kt` | :core-llm | `com.opendroid.ai.core.util` | small util needed by moved code; lowest legal home in the DAG |
| `com/opendroid/ai/core/util/DurationParser.kt` | :core-llm | `com.opendroid.ai.core.util` | small util needed by moved code |
| `com/opendroid/ai/core/util/NetworkErrorFormatter.kt` | :core-llm | `com.opendroid.ai.core.util` | small util needed by moved providers |
| `com/opendroid/ai/core/util/UrlUtils.kt` | :core-llm | `com.opendroid.ai.core.util` | small util needed by moved code |
| `com/opendroid/ai/data/db/dao/ModelDao.kt` | :core-llm | `com.opendroid.ai.data.db.dao` | Room-annotated interface used by ModelDownloadWorker (Decision 5a) |
| `com/opendroid/ai/data/db/dao/NotificationDao.kt` | :core-llm | `com.opendroid.ai.data.db.dao` | Room-annotated interface used by AutoReplyEngine (Decision 5a) |
| `com/opendroid/ai/data/db/dao/UnknownActionDao.kt` | :core-llm | `com.opendroid.ai.data.db.dao` | Room-annotated interface used by ActionDispatcher/PlanValidator/ReEvaluationEngine (Decision 6) |
| `com/opendroid/ai/data/db/entities/ModelEntity.kt` | :core-llm | `com.opendroid.ai.data.db.entities` | ModelStatus enum + markDownload* used by model download (Decision 5a) |
| `com/opendroid/ai/data/db/entities/NotificationEntity.kt` | :core-llm | `com.opendroid.ai.data.db.entities` | behind NotificationDao (Decision 5a) |
| `com/opendroid/ai/data/db/entities/UnknownActionEntity.kt` | :core-llm | `com.opendroid.ai.data.db.entities` | behind UnknownActionDao (Decision 6) |
| `com/opendroid/ai/data/models/AutoMode.kt` | :core-llm | `com.opendroid.ai.data.models` | shared model, lowest legal home in the DAG |
| `com/opendroid/ai/data/models/AutoReplyConfig.kt` | :core-llm | `com.opendroid.ai.data.models` | shared model |
| `com/opendroid/ai/data/models/ChatMessage.kt` | :core-llm | `com.opendroid.ai.data.models` | shared model used by providers and agent |
| `com/opendroid/ai/data/models/HabitEvent.kt` | :core-llm | `com.opendroid.ai.data.models` | shared model |
| `com/opendroid/ai/data/models/HabitRoutine.kt` | :core-llm | `com.opendroid.ai.data.models` | shared model |
| `com/opendroid/ai/data/models/LLMConfig.kt` | :core-llm | `com.opendroid.ai.data.models` | shared model used by providers/factory |
| `com/opendroid/ai/data/models/Macro.kt` | :core-llm | `com.opendroid.ai.data.models` | shared model |
| `com/opendroid/ai/data/models/Memory.kt` | :core-llm | `com.opendroid.ai.data.models` | shared model |
| `com/opendroid/ai/data/models/Plan.kt` | :core-llm | `com.opendroid.ai.data.models` | shared model used by agent code |
| `com/opendroid/ai/data/models/PlanStep.kt` | :core-llm | `com.opendroid.ai.data.models` | shared model |
| `com/opendroid/ai/data/models/StepResult.kt` | :core-llm | `com.opendroid.ai.data.models` | shared model |
| `com/opendroid/ai/data/models/UserProfile.kt` | :core-llm | `com.opendroid.ai.data.models` | shared model |
| `com/opendroid/ai/data/repository/SettingsRepository.kt` | :core-llm | `com.opendroid.ai.data.repository` | DataStore-only store used by 19 moved files; app-layer code in a core module (Decision 4) |
| `com/opendroid/ai/core/crash/CrashLogRedactor.kt` | :core-agent | `com.opendroid.ai.core.crash` | small pure redactor used by AgentLoop + ActionSequenceExecutor |
| `com/opendroid/ai/core/memory/ExecutionHistoryPrivacy.kt` | :core-agent | `com.opendroid.ai.core.memory` | small pure redaction helper used by AgentLoop |
| `com/opendroid/ai/core/memory/WorkingMemory.kt` | :core-agent | `com.opendroid.ai.core.memory` | small pure class used by PlanManager |
| `com/opendroid/ai/core/settings/AppSettingsStore.kt` | :core-security | `com.opendroid.ai.core.settings` | DataStore store used by the two Legacy* security files |
| `com/opendroid/ai/social/domain/model/SocialModels.kt` | :core-security | `com.opendroid.ai.social.domain.model` | models used by SocialCredentialStore |
| `com/opendroid/ai/social/domain/model/SocialPlatform.kt` | :core-security | `com.opendroid.ai.social.domain.model` | model used by SocialCredentialStore |

(Count: 37 files. Generated from the moved tree, 2026-10-02. Run 2 additions below.)

Run 2 relocation amendments (Phase 2 compile surfaced two cross-module references the import-based Phase-0 map missed; both files moved byte-identical, no code change):

| File | Module it lives in | Its package | Why it is there |
|---|---|---|---|
| `com/opendroid/ai/core/agent/AgentLoop.kt` (incl. `AgentState`) | :platform-a11y | `com.opendroid.ai.core.agent` | split package: AgentLoop calls `OpenDroidAccessibilityService.getInstance()?.takeScreenshotAndEncode()` (agent->a11y) while the service imports AgentLoop/AgentState (a11y->agent); relocating the file (rather than an interface) follows the owner decision "do not change moved code to break cycles" |
| `com/opendroid/ai/core/agent/AliasResolver.kt` | :core-llm | `com.opendroid.ai.core.agent` | split package: `IntentClassifier` (in :core-llm) calls AliasResolver (llm->agent cycle) |
| `com/opendroid/ai/core/agent/VisionEngine.kt` | :platform-a11y | `com.opendroid.ai.core.agent` | split package: VisionEngine calls `OpenDroidAccessibilityService.getInstance()` |

Phase 2 also discovered 3 fully-qualified (non-import) references to quarantined code that the Phase-0 import scan missed - each became a minimal adapter interface (ADAPTER decision in the map): `com.opendroid.ai.core.service.OpenDroidNotificationListener` (AutoReplyEngine), `com.opendroid.ai.core.routine.HabitRoutineEngine` (OpenDroidAccessibilityService), `com.opendroid.ai.data.repository.ModelRepository` (LiteRTLMProvider). Plus the unqualified same-package references from ActionDispatcher.kt to the 14 upstream action classes + ActionAutoMapper (see Non-move changes).


## Evidence log (updated as phases complete)

- Phase 0: this file (`task-004-extraction-map.md`) plus `task-004-tools/` (`gen-map.py`, `copy-move.py`, `verify-move.py`, `move-manifest.json`, `import_graph.py`, `closure.py`) committed as 04409c9 and pushed.
- Phase 1: module scaffolding c44f676; verbatim moves f1669c3 (:core-security, 10 files), 74846d8 (:core-llm, 70 files), a2e2b99 (:core-agent, 14 files), 9c9eaa3 (:platform-a11y, 7 files) — 102 files total. `verify-move.py` result: **102/102 byte-identical to upstream besides the single provenance header line, 0 mismatches** (exit 0). Branch `agent/android/36-extraction` pushed after the first commit and after each phase.
- Phase 2: per-module compile results, non-move changes, test counts, dependency report and OkHttp convergence below (appended in place).

## Phase 2 status (run 1, 2026-10-02)

**What is verified (real command output, exit codes):**

- `./gradlew :core-security:compileDebugKotlin` (JAVA_HOME=Adoptium JDK 21.0.12.101, ANDROID_HOME=C:\Users\<user>\Android\Sdk) -> **BUILD SUCCESSFUL in 2m 23s, 7 actionable tasks: 7 executed** (log: `_upstream/t004-security-build2.log`). :core-security compiles with ONLY its declared dependencies (hilt+ksp, datastore, kotlinx-serialization, coroutines, core-ktx) under AGP 9.3.1 built-in Kotlin (no kotlin-android plugin; KGP pinned 2.4.0 via root buildscript).
- Ported unit tests run in the new layout (2 files, 37 tests, JUnit XML in `android/core-security/build/test-results/testDebugUnitTest/`): LegacySecurePreferencesRetirementTest 25 tests, ProviderCredentialStoreTest 12 tests. Run 1: 13 failures, 0 errors, 24 passes.

**Test failure triage (run 1, before re-run):**

1. 12x `Method e in android.util.Log not mocked` (KeystoreSecretStorage.kt:250/270/279 error boundaries) -> environment: upstream runs non-Robolectric tests with `testOptions.unitTests.returnDefaultValues = true` (app/build.gradle, upstream comment #104). Fix applied in bad64dd (`fix(android): mirror upstream unit test options`). RE-CHECK in next run: failures persisted in run 2, suspect AGP 9 property rename (`isReturnDefaultValues`) or testOptions block not reaching the task - VERIFY before triaging further; do not @Ignore.
2. 1x `FileNotFoundException: build.gradle` in `security crypto has no production references` (LegacySecurePreferencesRetirementTest.kt:402) -> layout-sensitive scan test: reads `build.gradle` by name relative to the test working dir; EQO root uses `build.gradle.kts`. Triage: test-environment only; fix by setting the test task workingDir (or a test-side path tweak) in a Phase-2 `fix` commit. Not a behavior defect in moved code.

**Not yet done (next run):** :core-llm / :core-agent / :platform-a11y dependencies + compile; adapter interfaces (ConversationRepository->ChatHistoryStore, PlanRepository->PlanStore, MemoryManager->MemoryStore, OpenDroidService->ServiceBridge, MainActivity->NotificationTapTarget, VisionEngine a11y interface); remaining test ports (24 more files, 205 tests); robolectric/mockwebserver/coroutines-test deps; dependency report + single-OkHttp proof; ktlint format commit; detekt baselines; lint baseline vs task-002-lint; no-Leap grep gate; `assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt` repo-wide x2 from fresh clone; CI green.

## Phase 2 status (run 2, 2026-10-02)

**What is verified (real command output, exit codes):**

- Per-module compile with ONLY declared dependencies (JAVA_HOME=Adoptium JDK 21.0.12.101, ANDROID_HOME=C:\Users\<user>\Android\Sdk): `./gradlew :core-security:compileDebugKotlin :core-llm:compileDebugKotlin :core-agent:compileDebugKotlin :platform-a11y:compileDebugKotlin` -> **BUILD SUCCESSFUL, 42 actionable tasks, EXIT=0** (log `_upstream/t004-compile-modules-final.log`).
- Ported OpenDroid unit tests run in the new layout: `./gradlew :core-llm:testDebugUnitTest :core-agent:testDebugUnitTest :core-security:testDebugUnitTest :platform-a11y:testDebugUnitTest` -> **BUILD SUCCESSFUL, EXIT=0** (log `_upstream/t004-tests-all3.log`). JUnit XML counts: **286 tests run, 0 failures, 0 errors, 3 skipped** (:core-llm 229, :core-security 37, :core-agent 17, :platform-a11y 3).

**Test counts before and after (moved packages):**

- BEFORE (upstream `app/src/test`, yashab-cyber/opendroid @ 6ff5a061): 537 tests run, 2 failed upstream (both `StorageWorkspaceProviderTest` in `core/storage`, out of Phase One scope - not ported). Tests belonging to moved packages = 31 files: `core/agent/*` 8, `core/llm/*` 16 (incl. `error/` and `providers/`), `core/security/*` 2 (already ported in run 1), `actions/base/ActionResultTest` 1, `core/util/DeviceCapabilitiesTest` + `DurationParserTest` 2, `data/models/AutoModeConfigTest` 1, `data/repository/SettingsRepositoryProviderCredentialsTest` 1. NOT ported (their subjects are quarantined): `actions/*ActionTest` (10 files testing the quarantined action classes), `core/crash/*` (4), `core/memory/*` (2), `core/net`, `core/permissions`, `core/routine`, `core/service`, `core/storage`, `core/voice`, `data/crash`, `data/db`, `di`, `social`, `build/AnnotationProcessorOutputTest`, `ResourceCleanupTest` - each QUARANTINE per the map, not deleted from upstream.
- AFTER: 286 tests run / 0 failures / 0 errors / 3 skipped (all with written reasons, none @Ignore'd or deleted). 29 test files were ported (the 2 security files in run 1 + 27 in run 2); `NeedsInputParamKeyTest` follows its subject (`paramKeyForNeedsInput`, internal to AgentLoop.kt) into :platform-a11y.

**Test failure triage (run 2; all resolved, none deleted):**

1. 12x `android.util.Log not mocked` (run 1) -> fixed by `isReturnDefaultValues = true` (bad64dd had used `returnDefaultValues`, an unresolved reference in AGP 9 Kotlin DSL - the option never took effect).
2. 2x layout-sensitive file scans (`build.gradle`/`proguard-rules.pro`/app backup rules read by name) -> `fix(android): adapt 2 ported security tests to the EQO layout` (build.gradle.kts awareness; backup-rule assertions unchanged but gated on the files existing, since the EQO skeleton :app carries none).
3. 4x `Requires newer sdk version #30 (current version is #28)` (Robolectric `@Config(sdk=[28])`) -> EQO's minSdk 30 (ADR-0003) makes the sdk-28 lane impossible; the 3 `DeviceCapabilitiesTest` lanes moved to sdk 30 (semantics "below API 33" preserved), and `foreground info omits service type before Android Q` is unreachable under the floor, so its unchanged assertions are assumption-gated (re-enable if the floor ever drops below Q).
4. 1x `merged WorkManager service declares data sync and permission` -> asserts upstream APP manifest content (WorkManager service override) the EQO skeleton :app does not carry yet; unchanged assertions gated on it existing.
5. 3 skipped in the final run = triage items 2 (1 test) + 4 (1 test) + 3 (1 test).

**Non-move changes (run 2, all separate commits):**

- `6f125d3` fix: drop `kotlin-android` plugin request in the 3 new modules (CI run 36959439166: "plugin is already on the classpath with an unknown version"; AGP 9 built-in Kotlin, KGP pinned via root buildscript).
- `e6db16e` fix: AGP 9 test option is `isReturnDefaultValues`.
- `ac6dff9` fix: 2 ported security tests adapted to the EQO layout.
- `6c92bf8` refactor: adapter ports in :core-agent (MemoryManager->MemoryStore, ConversationRepository->ChatHistoryStore, PlanRepository->PlanStore, NotificationIntelligence->NotificationStore, OpenDroidNotificationListener->NotificationListenerBridge).
- `1b9c6ca` refactor: adapter ports in :core-llm (ModelRepository->ModelStore, MainActivity->NotificationTapTarget).
- `858bca7` refactor: adapter ports in :platform-a11y (OpenDroidService->ServiceBridge, HabitRoutineEngine->HabitRoutineTracker, NotificationTapTarget wiring) + VisionEngine relocation + bot.png (upstream res/, provenance recorded here: `yashab-cyber/opendroid @ 6ff5a061, path: app/src/main/res/drawable/bot.png`).
- `6ebb90c` refactor: minimal interfaces for the 14 upstream action classes + ActionAutoMapper (same name/package so the moved ActionDispatcher.kt stays byte-identical; the references were unqualified same-package uses the Phase-0 import scan could not see).
- `db2fe96` chore: byte-identical relocations AliasResolver.kt -> :core-llm, AgentLoop.kt -> :platform-a11y (cycle breaks per owner decision) + 3 local-val smart-cast fixes in AgentLoop.kt (cross-module smart casts; behavior identical) + MemoryStore.recallContactPreference (member missed by the first call-site scan).
- `1ec3ff1` build: module dependencies (see dependency report) + AGP 9 `isReturnDefaultValues` + :platform-a11y `namespace com.opendroid.ai` (map decision 8, keeps `import com.opendroid.ai.R` byte-identical).
- Test-side adaptations in run 2's test port (all noted in-file with a `TASK-004 Phase 2` comment): `ModelDownloadSchedulingTest` passes a fake `NotificationTapTarget`; `CustomOpenAIProviderNetworkTest` inlines the quarantined `di/AppModule.provideOkHttpClient()` body verbatim; Robolectric lane bumps + assumption gates as triaged above.

**Dependency report / OkHttp convergence:** see the "Dependency report" section below (updated each run).

**Not yet done (next run):** ktlint format commit; detekt baselines per module + counts; lint baseline vs task-002-lint (only matching id+file entries); repo-wide `assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt` x2 from a fresh clone of the pushed branch; CI green confirmation on PR #41; final 'What is not verified' section.


## Dependency report (TASK-004, run 2)

Declared dependencies (version catalog `android/gradle/libs.versions.toml`, upstream-verified pins):

- :core-security: hilt-android 2.60.1 (+ksp hilt-compiler), datastore-preferences 1.1.1, kotlinx-serialization-json 1.8.1, kotlinx-coroutines-android 1.10.2, androidx.core-ktx 1.18.0.
- :core-llm: project(:core-security) + the above (minus datastore kept, plus room-runtime 2.8.4, work-runtime-ktx 2.11.2, okhttp-bom 5.4.0 + okhttp, converter-gson 3.0.0 (for com.google.gson), litertlm-android 0.14.0, mlkit-genai-prompt 1.0.0-beta2). Tests: junit 4.13.2, robolectric 4.16.1, androidx.test:core 1.6.1, mockwebserver3 (BOM 5.4.0).
- :core-agent: project(:core-llm) + hilt, kotlinx-serialization-json, kotlinx-coroutines-android, androidx.core-ktx. Tests: junit, robolectric, androidx.test:core.
- :platform-a11y: project(:core-agent), project(:core-llm) + hilt, kotlinx-serialization-json, kotlinx-coroutines-android, androidx.core-ktx.

Retrofit is NOT declared directly: no moved file imports `retrofit2`; it enters `:core-llm`'s runtime only transitively via `converter-gson` (upstream declares retrofit + converter-gson; the moved code uses OkHttp + Gson directly).

**One OkHttp major version at runtime** (`./gradlew :<module>:dependencyInsight --dependency okhttp --configuration releaseRuntimeClasspath`, log `_upstream/t004-okhttp-proof.log`, EXIT=0): every module resolves `com.squareup.okhttp3:okhttp -> 5.4.0` / `okhttp-android:5.4.0 (by constraint)` from `okhttp-bom:5.4.0`; `com.squareup.retrofit2:retrofit:3.0.0 (requested com.squareup.okhttp3:okhttp:4.12.0)` is upgraded to `4.12.0 -> 5.4.0` by the BOM. :core-security has no OkHttp at all. Major version at runtime: **5 only**.

## Phase 2 status (run 3, 2026-10-02): static checks and lint

**ktlint (AGENTS.md check `ktlintCheck`):**

- BEFORE: `./gradlew ktlintCheck --continue` -> **FAILED, EXIT=1** with 543 violations in :core-agent, 3379 in :core-llm, 492 in :core-security, 551 in :platform-a11y (logs `_upstream/t004-ktlint-check.log`, `t004-ktlint-check2.log`).
- `2a24c34` `style(android): ktlint format moved files`: ktlintFormat autocorrections (141 files) + 3 manual comment-only fixes ktlint cannot autocorrect (consecutive KDoc merged in KeystoreSecretStorage.kt; trailing value-parameter comments in SocialModels.kt moved above their parameters).
- `2e934d2` `style(android): resolve ktlint violations ktlint cannot autocorrect` (43 residual violations): 22 wildcard imports expanded to explicit imports by `task-004-tools/expand-wildcard-imports.py` (candidates computed from in-repo package declarations; the `com.google.mlkit.genai.prompt` symbols were verified against the class list inside `genai-prompt-1.0.0-beta2.aar` from the Gradle cache); 14 string literals over 140 chars wrapped via concatenation (ActionSchema 4 descriptions, OnDeviceModelRegistry 5 download URLs, VisionEngine 5 prompts/messages; split points mid-sentence, runtime strings byte-identical); 2 identifier renames (const `defaultModelId` -> `DEFAULT_MODEL_ID`, test val `TEST_TAP_TARGET` -> `testTapTarget`, ktlint property-naming); 2 misplaced comments relocated (ContactResolver.kt, LLMConfig.kt).
- AFTER: `./gradlew ktlintFormat` **EXIT=0** (`t004-ktlint-format3.log`), `./gradlew ktlintCheck` **EXIT=0** (`t004-ktlint-check4.log`), re-verified **EXIT=0** (`t004-ktlint-check5.log`).

**detekt:**

- BEFORE: `./gradlew detekt --continue` -> EXIT=1. Per-module issue counts from the checkstyle `detekt.xml` reports (raw = weighted here): **:core-security 44, :core-llm 500, :core-agent 100, :platform-a11y 145, :app 0** (`t004-detekt-before.log`).
- `f8d053b` `chore(android): detekt baselines for imported code`: the sanctioned `detektBaseline` task per module, entries 37 / 419 / 88 / 112 (dominant rules: MaxLineLength, MagicNumber, ReturnCount, TooGenericExceptionCaught). No rule disabled, no severity lowered.
- AFTER: `./gradlew detekt` -> **BUILD SUCCESSFUL, EXIT=0** (`t004-detekt-after.log`). New EQO code should not add baseline entries.

**Android Lint (`lintDebug`, warningsAsErrors = true):**

- BEFORE: **EXIT=1 with 20 errors** (`t004-lint-before.log`): MissingPermission 14 (DeviceStateProvider.kt x9, CallFlowVerifier.kt x1, 2 of the DeviceStateProvider ones sat in dead pre-M branches), GradleDependency 4 (compileSdk 36 vs 37 nag), ObsoleteSdkInt 4, IconLocation 1 (bot.png). Full id+file list preserved in `task-004-tools/lint-findings-pre-fix.csv`.
- The brief's lint-baseline path was evaluated mechanically with `task-004-tools/lint-baseline-match.py` (compares (id, file) pairs against `task-002-lint/lint-results-debug.xml`): **MATCH COUNT = 0 of 20** (task-002 has 27 distinct id+file pairs; its only ObsoleteSdkInt is `ui/viewmodel/SettingsViewModel.kt`, not a moved file). Output: `task-002 findings: 27 (id+file pairs) / EQO findings compared: 20 / MATCH COUNT (id+file): 0 / baseline path applicable: no`. **No lint-baseline.xml was written; nothing is baselined or hidden.**
- All 20 findings fixed in separate commits, no `severity=ignore` entry added anywhere, `app/lint.xml` unchanged:
  - `492e8c7` `fix(android): remove SDK_INT guards that are dead under minSdk 30` (4 ObsoleteSdkInt + the 2 MissingPermission findings inside dead branches; behavior identical on every API >= 30).
  - `2af7bfd` `fix(android): declare module permissions; move bot.png to drawable-mdpi` (7 MissingPermission + 1 IconLocation; manifest entries mirror the upstream app manifest).
  - `6d7e062` `fix(android): WorkManager FGS type override + bot.png density variants` (SpecifyForegroundServiceType surfaced after the guard removal exposed the constant; IconMissingDensityFolder after the mdpi move; hdpi/xhdpi/xxhdpi variants generated at the framework's own scale factors by `task-004-tools/generate-density-variants.py`, rendered size unchanged).
  - `dec7699` `build(android): apply the reviewed :app lint config to library modules` (GradleDependency x4). **Judgement call for the reviewer**: `lintConfig = file("../app/lint.xml")` in the 4 library modules. This adds NO suppression entry: `app/lint.xml` is the TASK-003-reviewed config whose 4 entries are accepted version-currency suppressions for the mandated TASK-001 pins, and `compileSdk 36` (nagged to 37) is one of those pins. warningsAsErrors stays on, all other checks still fail the build. If the reviewer prefers, this can be re-scoped to per-module copies of the same file.
- AFTER: `./gradlew lintDebug` -> **BUILD SUCCESSFUL, EXIT=0** (`t004-lint-after3.log`).

**Non-move changes (run 3, all separate commits, all listed above):** 2a24c34 (style), 2e934d2 (style), f8d053b (chore: baselines), 492e8c7 / 2af7bfd / 6d7e062 (fix: lint findings), dec7699 (build: lint config). No behavior change in any of them; the only runtime-visible judgment is the bot.png density bucketing (see above) and the removal of unreachable pre-30 code.

**Gates (run 3):** no-Leap grep `grep -rniE "liquid|leap-sdk|ai\.liquid" android/ --include=*.gradle --include=*.kts --include=*.kt --include=*.toml` -> 0 matches, **exit 1** (gate satisfied). Repo-wide `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt` twice from a fresh clone + CI green on PR #41: recorded in the 'Final gates' section appended below.

## What is not verified

- The style/fix commits after the verified moves change moved-file bodies (formatting, imports, wrapped literals, renames, dead-guard removal). They are behavior-neutral by inspection and green under 286 tests, but unlike the move commits they are **not** mechanically diffed against upstream for byte identity; every one is listed above with its reason.
- The 14 wrapped string literals preserve the runtime strings by construction (concatenation of the exact original text), but the equality is asserted, not machine-checked.
- bot.png density variants are generated at the framework's scale factors (mdpi x1.5/x2/x3); pixel-level identity with upstream's runtime scaling is not verified on a device.
- No emulator/device run: the accessibility service, notification listener and model download flows are unit-tested only.
- `assembleRelease` packaging details (R8/ProGuard rules for the moved code) are verified only by the gate build, not exercised at runtime.
- Provenance headers are verified only for the 102 files of the move commits (verify-move.py 102/102, run 1); adapter/interface files created in Phase 2 carry their own provenance notes in-file where they port upstream bodies.

## Final gates (run 3, 2026-10-02)

All commands below ran with JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot, ANDROID_HOME=C:\Users\<user>\Android\Sdk.

- **Fresh clone of the pushed branch**: `git clone --branch agent/android/36-extraction --single-branch` into `_upstream/t004-fresh1`, CLONE_EXIT=0, `git rev-parse HEAD` = `77d5d52a517b18d67391a0a79972a656817ab171` (= pushed HEAD).
- **Repo-wide gate run 1** (fresh clone): `./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt` -> **BUILD SUCCESSFUL in 8m 34s, 459 actionable tasks: 459 executed, RUN1_EXIT=0** (`_upstream/t004-fresh-run1.log`).
- **Repo-wide gate run 2** (immediately after, same clone): same command -> **BUILD SUCCESSFUL, 459 actionable tasks: 6 executed, 453 up-to-date, RUN2_EXIT=0** (`_upstream/t004-fresh-run2.log`).
- **Unit tests (fresh clone, all modules incl. :app)**: JUnit XML `tests=287 skipped=2 failures=0 errors=0` (:core-llm 229, :core-security 37, :core-agent 17, :platform-a11y 3, :app 1). The 2 skips are the written assumption gates still standing (pre-Q foreground-service-type lane in ModelDownloadSchedulingTest; LegacySecurePreferencesRetirementTest layout scan). The `merged WorkManager service declares data sync and permission` test is no longer skipped: it now runs and passes since `2af7bfd`/`6d7e062`/`77d5d52` gave :core-llm the upstream-equivalent manifest (it failed once in CI run 36977439930 before `77d5d52` added the missing FOREGROUND_SERVICE_DATA_SYNC permission - root-caused and fixed, not gated away).
- **CI (draft PR #41)**: run **36978121181 conclusion success** on headSha 77d5d52 (jobs `repo-checks` + `android` both success; the workflow runs `assembleDebug assembleRelease testDebugUnitTest lintDebug` + `ktlintCheck detekt` project-wide). Preceding red runs on this branch are root-caused from their own logs: run 36975406561 failed `:core-security:lintDebug` on the GradleDependency compileSdk nag (fixed by `dec7699`), run 36977439930 failed `:core-llm:testDebugUnitTest` on the now-un-skipped merged-manifest test (fixed by `77d5d52`). The two earlier runs (36972136258, 36975155159) were not individually re-inspected; the current head is green.
- **No-Leap gate**: `grep -rniE "liquid|leap-sdk|ai\.liquid" android/ --include=*.gradle --include=*.kts --include=*.kt --include=*.toml` -> 0 matches, **exit 1** (gate satisfied).
- **Acceptance criteria re-check**: (1) each module compiles with only its declared dependencies - run 2 `:core-*:compileDebugKotlin` BUILD SUCCESSFUL EXIT=0 (`t004-compile-modules-final.log`), dependency report below; (2) ported tests run in the new layout with counts before/after and triage (this map); (3) one OkHttp major at runtime = 5.4.0 everywhere (dependencyInsight, EXIT=0, `t004-okhttp-proof.log`); (4) move commits contain no behavior changes (verify-move.py 102/102 byte-identical besides the provenance header, exit 0; the move commits f1669c3/74846d8/a2e2b99/9c9eaa3 are free of any other edit).

Note: this 'Final gates' section was added in the commit after the gate runs; that commit and any later ones touch evidence files only.
