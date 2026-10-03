// TASK-006 security pass F5 (issue #11): LLMConfig.toString() must not render keys.
package ai.eqo.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * F5 of the TASK-006 security pass: [LLMConfig] is a data class whose
 * generated `toString()` would render `apiKeys` and `elevenLabsApiKey` — one
 * `Log.d(TAG, "$config")` away from dumping every stored key. Like
 * `ProviderRequestConfig`, it renders fixed redacted text only.
 */
class LLMConfigRedactionTest {
    /** Synthetic; only their absence from rendered text is asserted. */
    private val providerKey = "sk-or-...f501"

    private val elevenLabsKey = "elevenlabs-f5-secret"

    @Test
    fun `toString renders fixed redacted text and no key value`() {
        val config =
            LLMConfig(
                apiKeys = mapOf("OpenRouter" to providerKey),
                elevenLabsApiKey = elevenLabsKey,
            )

        val rendered = config.toString()

        assertEquals("<redacted LLMConfig>", rendered)
        assertFalse("provider key leaked: $rendered", rendered.contains(providerKey))
        assertFalse("elevenlabs key leaked: $rendered", rendered.contains(elevenLabsKey))
    }
}
