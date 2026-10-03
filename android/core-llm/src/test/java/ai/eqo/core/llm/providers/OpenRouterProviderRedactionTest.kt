// TASK-006 security pass N9 (issue #11): the per-request redactor registration
// in OpenRouterProvider.complete.
package ai.eqo.core.llm.providers

import ai.eqo.core.llm.LLMRequest
import ai.eqo.core.llm.ProviderRequestConfig
import ai.eqo.core.llm.error.LLMException
import ai.eqo.core.llm.security.LogRedactor
import ai.eqo.core.security.CredentialStoreResult
import ai.eqo.core.security.CredentialStoreResult.Success
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * N9 of the TASK-006 security pass: [OpenRouterProvider.complete] registers the
 * resolved key with [LogRedactor] for the lifetime of the request and releases
 * it afterwards — on the success path and on the failure path alike.
 *
 * The request carries its own credential (as the connection-test callers do),
 * so only the provider's own registration is under observation.
 */
class OpenRouterProviderRedactionTest {
    /** Synthetic; only its registration lifetime is asserted. */
    private val apiKey = "sk-or-...n901"

    private val prompt = "the-user-prompt-must-not-leak"

    /** The secret snapshot taken while the request is executing. */
    private var secretsDuringRequest: Set<String> = emptySet()

    private val client =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                secretsDuringRequest = LogRedactor.registeredSecrets()
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message("OK")
                    .body(responseBody.toResponseBody(JSON_MEDIA_TYPE))
                    .build()
            }.build()

    private var responseCode = 200

    private var responseBody = """{"choices":[{"message":{"content":"pong"}}],"usage":{"total_tokens":3}}"""

    @Test
    fun `the resolved key is registered for the request and released after it`() =
        runBlocking {
            val provider = newProvider()

            assertFalse(LogRedactor.registeredSecrets().contains(apiKey))
            val response = provider.complete(newRequest())

            assertEquals("pong", response.content)
            assertTrue(
                "the key must be registered while the request runs",
                secretsDuringRequest.contains(apiKey),
            )
            assertFalse(
                "the key must be released after the request",
                LogRedactor.registeredSecrets().contains(apiKey),
            )
        }

    @Test
    fun `a failed request releases the key too`() {
        responseCode = 401
        responseBody = """{"error":{"message":"Invalid key provided: $apiKey"}}"""
        val provider = newProvider()

        assertThrows(LLMException::class.java) {
            runBlocking { provider.complete(newRequest()) }
        }

        assertTrue(secretsDuringRequest.contains(apiKey))
        assertFalse(
            "the key must be released after the failed request",
            LogRedactor.registeredSecrets().contains(apiKey),
        )
    }

    private fun newProvider() = OpenRouterProvider(client, newSettingsRepository())

    private fun newRequest() =
        LLMRequest(
            systemPrompt = "you are a test",
            messages = listOf(ChatMessage("1", prompt, ChatMessage.Sender.USER)),
            model = "openrouter/auto",
            providerConfig =
                ProviderRequestConfig(
                    apiKey = apiKey,
                    endpoint = "https://openrouter.ai/api/v1",
                ),
        )

    private fun newSettingsRepository() =
        SettingsRepository(
            dataStore =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                    produceFile = {
                        Files
                            .createTempDirectory("opendroid-openrouter-redaction-test")
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

        override fun read(credential: ProviderCredentialId): CredentialStoreResult<String?> = Success(null)

        override fun readProviderApiKeys(): CredentialStoreResult<Map<String, String>> = Success(emptyMap())

        override fun write(
            credential: ProviderCredentialId,
            value: String,
        ): CredentialStoreResult<Unit> = Success(Unit)

        override fun remove(credential: ProviderCredentialId): CredentialStoreResult<Unit> = Success(Unit)

        override fun migrateLegacyCredentials(): CredentialStoreResult<Unit> = Success(Unit)

        override fun resetForReentry(): CredentialStoreResult<Unit> = Success(Unit)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
