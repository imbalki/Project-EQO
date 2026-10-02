// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/core/llm/ProviderSelectionPolicy.kt
package ai.eqo.core.llm

import ai.eqo.core.agent.ActionRisk
import ai.eqo.core.agent.ActionRiskPolicy
import ai.eqo.core.llm.error.LLMError

/** Pure policy for provider fallback ordering and trust-boundary checks. */
object ProviderSelectionPolicy {
    fun explicitFallbacks(
        activeProvider: String,
        configuredFallbacks: Iterable<String>,
        risk: ActionRisk,
    ): List<String> {
        if (!ActionRiskPolicy.allowsAutomaticFallback(risk)) return emptyList()

        val active = ProviderCatalog.canonicalName(activeProvider)
        return configuredFallbacks
            .map(ProviderCatalog::canonicalName)
            .filter { ProviderCatalog.isKnown(it) }
            .filter { it != active }
            .distinct()
    }

    fun shouldEscalateMalformed(
        error: LLMError,
        hasNextProvider: Boolean,
    ): Boolean = error == LLMError.MalformedResponse && hasNextProvider

    fun terminalError(
        activeProvider: String,
        providerCount: Int,
    ): LLMError =
        if (providerCount == 1 && ProviderCatalog.isOnDevice(activeProvider)) {
            LLMError.SafeFallbackUnavailable
        } else {
            LLMError.MalformedResponse
        }
}
