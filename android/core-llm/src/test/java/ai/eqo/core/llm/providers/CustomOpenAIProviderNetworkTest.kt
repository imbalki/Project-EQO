// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/test/java/com/opendroid/ai/core/llm/providers/CustomOpenAIProviderNetworkTest.kt
package ai.eqo.core.llm.providers

import ai.eqo.core.llm.LLMRequest
import ai.eqo.core.llm.ProviderRequestConfig
import ai.eqo.core.llm.error.LLMError
import ai.eqo.core.llm.error.LLMException
import ai.eqo.core.security.CredentialStoreResult
import ai.eqo.core.security.ProviderCredentialId
import ai.eqo.core.security.ProviderCredentialRecoveryState
import ai.eqo.core.security.ProviderCredentialStore
import ai.eqo.data.models.ChatMessage
import ai.eqo.data.repository.SettingsRepository
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * End-to-end coverage of one cloud provider against a real socket, so the OkHttp 5
 * request/response path (and the redaction boundary that consumes error bodies) is
 * exercised rather than mocked.
 *
 * [CustomOpenAIProvider] is the provider under test because its endpoint is
 * caller-supplied; every other OpenAI-compatible provider shares this request and
 * error-handling code with a hard-coded host.
 */
class CustomOpenAIProviderNetworkTest {
    private val server = MockWebServer().also { it.start(InetAddress.getByName("127.0.0.1"), 0) }
    private val provider =
        CustomOpenAIProvider(
            // TASK-004 Phase 2 adapter: upstream took the client from the quarantined
            // di/AppModule; inlined here verbatim from AppModule.provideOkHttpClient().
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build(),
            newSettingsRepository(),
        )

    /** Never logged or asserted on directly; only its absence from failures is asserted. */
    private val apiKey = "sk-test-0123456789abcdef"
    private val prompt = "the-user-prompt-must-not-leak"

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a successful completion is parsed and the request carries the credential`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .setHeader("Content-Type", "application/json")
                    .body(
                        """
                        {
                          "choices": [{"message": {"content": "pong"}}],
                          "usage": {"total_tokens": 42}
                        }
                        """.trimIndent(),
                    ).build(),
            )

            val response = provider.complete(newRequest())

            assertEquals("pong", response.content)
            assertEquals(42, response.tokensUsed)
            assertEquals("gpt-4o-mini", response.model)
            assertEquals("Custom OpenAI Compatible", response.provider)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/v1/chat/completions", recorded.target)
            assertEquals("Bearer $apiKey", recorded.headers["Authorization"])
            assertTrue(
                recorded.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"model\":\"gpt-4o-mini\""),
            )
        }

    @Test
    fun `a non-2xx response becomes a redacted LLMException`() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(401)
                .setHeader("Content-Type", "application/json")
                .body(
                    """
                    {"error":{"type":"invalid_request_error","code":"invalid_api_key",
                     "message":"Incorrect API key provided: $apiKey. Prompt was: $prompt"}}
                    """.trimIndent(),
                ).build(),
        )

        val failure =
            assertThrows(LLMException::class.java) {
                runBlocking { provider.complete(newRequest()) }
            }

        assertEquals(LLMError.AuthInvalid, failure.error)
        assertEquals(401, failure.status)
        assertEquals("invalid_request_error", failure.detail?.vendorType)
        assertEquals("invalid_api_key", failure.detail?.vendorCode)

        val rendered = "${failure.message} ${failure.detail} ${failure.error.code}"
        assertFalse(rendered.contains(apiKey))
        assertFalse(rendered.contains(prompt))
        assertFalse(rendered.contains(server.hostName))
        assertFalse(rendered.contains("Incorrect API key provided"))
    }

    @Test
    fun `an oversized error body is not classified and never surfaces`() {
        val filler = "y".repeat(40_000)
        server.enqueue(
            MockResponse
                .Builder()
                .code(500)
                .setHeader("Content-Type", "application/json")
                .body("""{"error":{"type":"server_error","code":"$filler"}}""")
                .build(),
        )

        val failure =
            assertThrows(LLMException::class.java) {
                runBlocking { provider.complete(newRequest()) }
            }

        assertEquals(LLMError.ServerError, failure.error)
        assertEquals(500, failure.status)
        assertNull(failure.detail?.vendorType)
        assertNull(failure.detail?.vendorCode)
        assertFalse(failure.message.orEmpty().contains(filler))
    }

    private fun newRequest() =
        LLMRequest(
            systemPrompt = "you are a test",
            messages = listOf(ChatMessage("1", prompt, ChatMessage.Sender.USER)),
            model = "gpt-4o-mini",
            providerConfig =
                ProviderRequestConfig(
                    apiKey = apiKey,
                    endpoint = server.url("/v1").toString(),
                ),
        )

    private fun newSettingsRepository() =
        SettingsRepository(
            dataStore =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                    produceFile = {
                        Files
                            .createTempDirectory("eqo-network-test")
                            .resolve("settings.preferences_pb")
                            .toFile()
                    },
                ),
            providerCredentialStore = EmptyProviderCredentialStore(),
            runStartupMigration = false,
        )

    /** The request under test carries its own credential, so nothing is stored. */
    private class EmptyProviderCredentialStore : ProviderCredentialStore {
        override val recoveryState: StateFlow<ProviderCredentialRecoveryState> =
            MutableStateFlow(ProviderCredentialRecoveryState.Ready)

        override fun read(credential: ProviderCredentialId): CredentialStoreResult<String?> = CredentialStoreResult.Success(null)

        override fun readProviderApiKeys(): CredentialStoreResult<Map<String, String>> = CredentialStoreResult.Success(emptyMap())

        override fun write(
            credential: ProviderCredentialId,
            value: String,
        ): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)

        override fun remove(credential: ProviderCredentialId): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)

        override fun migrateLegacyCredentials(): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)

        override fun resetForReentry(): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)
    }
}
