package ai.eqo.core.llm.providers

import ai.eqo.core.llm.ConnectionTestState
import ai.eqo.core.llm.error.LLMError
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * TASK-006 acceptance: the OpenRouter connection test against a mock
 * OpenAI-compatible server for success, 401, 429 and offline — and the raw
 * key text never surfaces in any failure.
 */
class OpenRouterConnectionTestTest {
    private val server = MockWebServer().also { it.start(InetAddress.getByName("127.0.0.1"), 0) }
    private val underTest =
        ai.eqo.core.llm.ConnectionTestRunner(
            OkHttpClient
                .Builder()
                .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
        )

    /** The candidate key under test; only its absence from failures is asserted. */
    private val apiKey = "sk-or-test-0123456789abcdef"

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a successful probe yields Connected with measured latency`() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body(
                    """
                    {"choices":[{"message":{"content":"pong"}}],"usage":{"total_tokens":3}}
                    """.trimIndent(),
                ).build(),
        )

        val state = underTest.run(endpoint(), apiKey, MODEL)

        assertTrue("expected Connected but was $state", state is ConnectionTestState.Connected)
        state as ConnectionTestState.Connected
        assertEquals("OpenRouter", state.provider)
        assertEquals(MODEL, state.model)
        assertTrue(state.latencyMs >= 0)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/chat/completions", recorded.target)
        assertEquals("Bearer $apiKey", recorded.headers["Authorization"])
    }

    @Test
    fun `a 401 yields AuthInvalid and never renders the key`() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(401)
                .setHeader("Content-Type", "application/json")
                .body(
                    """
                    {"error":{"message":"Invalid key provided: $apiKey","code":401}}
                    """.trimIndent(),
                ).build(),
        )

        val state = underTest.run(endpoint(), apiKey, MODEL)

        assertTrue("expected Failed but was $state", state is ConnectionTestState.Failed)
        state as ConnectionTestState.Failed
        assertEquals(LLMError.AuthInvalid, state.error)
        assertEquals(401, state.status)
        assertFalse(state.toString().contains(apiKey))
    }

    @Test
    fun `a 429 yields a retryable failure with the Retry-After honoured`() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(429)
                .setHeader("Content-Type", "application/json")
                .setHeader("Retry-After", "2")
                .body(
                    """
                    {"error":{"message":"Rate limit exceeded: $apiKey"}}
                    """.trimIndent(),
                ).build(),
        )

        val state = underTest.run(endpoint(), apiKey, MODEL)

        assertTrue("expected Failed but was $state", state is ConnectionTestState.Failed)
        state as ConnectionTestState.Failed
        assertTrue(
            "expected RateLimited/QuotaExhausted but was ${state.error}",
            state.error == LLMError.RateLimited || state.error == LLMError.QuotaExhausted,
        )
        assertTrue("Retry-After must be honoured", (state.retryAfterMillis ?: -1) >= 1000)
        assertFalse(
            listOf(state.toString(), state.error.code).joinToString(" ").contains(apiKey),
        )
    }

    @Test
    fun `an unreachable endpoint yields a non-retryable Network failure offline`() {
        // Port 1 on loopback: nothing listens there, so connect fails fast.
        val state = underTest.run(endpointOnPort(1), apiKey, MODEL)

        assertTrue("expected Failed but was $state", state is ConnectionTestState.Failed)
        state as ConnectionTestState.Failed
        assertEquals(LLMError.Network, state.error)
        assertFalse(
            listOf(state.toString(), state.error.code).joinToString(" ").contains(apiKey),
        )
    }

    @Test
    fun `a slow endpoint yields a Network failure timeout without the key`() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .headersDelay(10, java.util.concurrent.TimeUnit.SECONDS)
                .body("""{"choices":[{"message":{"content":"late"}}]}""")
                .build(),
        )

        val state = underTest.run(endpoint(), apiKey, MODEL)

        assertTrue("expected Failed but was $state", state is ConnectionTestState.Failed)
        state as ConnectionTestState.Failed
        assertEquals(LLMError.Network, state.error)
        assertFalse(
            listOf(state.toString(), state.error.code).joinToString(" ").contains(apiKey),
        )
    }

    @Test
    fun `the key is not registered after the test completes`() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"choices":[{"message":{"content":"pong"}}]}""")
                .build(),
        )

        underTest.run(endpoint(), apiKey, MODEL)

        assertFalse(secretRegistrySnapshot().contains(apiKey))
    }

    /**
     * `127.0.0.1` is a literal loopback host — what the endpoint policy accepts
     * for plain http — instead of the mock's reverse-resolved host name.
     */
    private fun endpoint(): String =
        server
            .url("/v1")
            .newBuilder()
            .host("127.0.0.1")
            .build()
            .toString()

    private fun endpointOnPort(port: Int): String = "http://127.0.0.1:$port/v1"

    private fun secretRegistrySnapshot(): Set<String> =
        ai.eqo.core.llm.error
            .SecretRegistry
            .snapshot()

    private companion object {
        const val MODEL = "openrouter/auto"
    }
}
