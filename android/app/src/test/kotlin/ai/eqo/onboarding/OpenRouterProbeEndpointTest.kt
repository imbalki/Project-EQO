package ai.eqo.onboarding

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Regression: the probe constant is the API base, never the full chat URL (a full URL 404'd every model). */
class OpenRouterProbeEndpointTest {
    @Test
    fun `probe endpoint is the api base without a chat completions suffix`() {
        assertFalse(
            "ConnectionTestRunner appends /chat/completions itself",
            ModelKeySetupActivity.OPENROUTER_ENDPOINT.trimEnd('/').endsWith("/chat/completions"),
        )
        assertEquals("https://openrouter.ai/api/v1", ModelKeySetupActivity.OPENROUTER_ENDPOINT)
    }

    @Test
    fun `composed probe url has exactly one chat completions segment`() {
        val url = (ModelKeySetupActivity.OPENROUTER_ENDPOINT.trimEnd('/') + "/chat/completions").toHttpUrl()
        assertEquals(listOf("api", "v1", "chat", "completions"), url.pathSegments)
    }
}
