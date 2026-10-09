// Origin: EQO-authored input_audio provider tests; interceptors return synthetic responses without network.
package ai.eqo.core.llm.providers

import ai.eqo.core.llm.InputAudio
import ai.eqo.core.llm.LLMRequest
import ai.eqo.core.security.CredentialStoreResult
import ai.eqo.core.security.ProviderCredentialId
import ai.eqo.core.security.ProviderCredentialRecoveryState
import ai.eqo.core.security.ProviderCredentialStore
import ai.eqo.data.repository.SettingsRepository
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class OpenRouterAudioTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val directory = Files.createTempDirectory("eqo-audio-test").toFile()
    private var acceptsAudio = true
    private var completionCode = 200
    private var uploads = 0
    private var payload = ""
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val catalog = request.url.encodedPath.endsWith("/models")
        val body = if (catalog) {
            val modality = if (acceptsAudio) "audio" else "text"
            """{"data":[{"id":"test/model","architecture":{"input_modalities":["$modality"]}}]}"""
        } else {
            uploads++
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            payload = buffer.readUtf8()
            """{"choices":[{"message":{"content":"verbatim words"}}]}"""
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(if (catalog) 200 else completionCode).message("synthetic")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()
    private val repository = SettingsRepository(
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { directory.resolve("settings.preferences_pb") },
        providerCredentialStore = FakeCredentials(), runStartupMigration = false,
    )
    private val provider = OpenRouterProvider(client, repository)

    @After
    fun cleanup() {
        scope.cancel()
        client.dispatcher.executorService.shutdown()
        directory.deleteRecursively()
    }

    @Test
    fun audioUsesConfiguredModelAndVerbatimPrompt() = runBlocking {
        assertEquals("verbatim words", provider.transcribe("test/model", InputAudio("synthetic-audio"), "hi-IN"))
        val json = JsonParser.parseString(payload).asJsonObject
        assertEquals("test/model", json.get("model").asString)
        assertFalse(json.has("response_format"))
        val messages = json.getAsJsonArray("messages")
        assertTrue(messages[0].asJsonObject.get("content").asString.contains("verbatim"))
        val content = messages[1].asJsonObject.getAsJsonArray("content")[0].asJsonObject
        assertEquals("input_audio", content.get("type").asString)
        assertEquals("wav", content.getAsJsonObject("input_audio").get("format").asString)
        assertEquals("synthetic-audio", content.getAsJsonObject("input_audio").get("data").asString)
        assertEquals(1, uploads)
    }

    @Test
    fun textOnlyModelNeverReceivesAudio() {
        acceptsAudio = false
        assertThrows(AudioUnsupportedException::class.java) {
            runBlocking { provider.transcribe("test/model", InputAudio("synthetic-audio"), "en-IN") }
        }
        assertEquals(0, uploads)
    }

    @Test
    fun payloadCannotAppearInRequestRenderingOrHttpErrors() {
        assertFalse(LLMRequest("prompt", emptyList(), inputAudio = InputAudio("synthetic-audio"))
            .toString().contains("synthetic-audio"))
        completionCode = 400
        val failure = assertThrows(java.io.IOException::class.java) {
            runBlocking { provider.transcribe("test/model", InputAudio("synthetic-audio"), "en-IN") }
        }
        assertEquals("Audio provider request failed with HTTP 400", failure.message)
    }

    private class FakeCredentials : ProviderCredentialStore {
        override val recoveryState = MutableStateFlow<ProviderCredentialRecoveryState>(ProviderCredentialRecoveryState.Ready)
        override fun read(credential: ProviderCredentialId): CredentialStoreResult<String?> =
            CredentialStoreResult.Success("synthetic-test-key")
        override fun readProviderApiKeys(): CredentialStoreResult<Map<String, String>> =
            CredentialStoreResult.Success(mapOf("OpenRouter" to "synthetic-test-key"))
        override fun write(credential: ProviderCredentialId, value: String): CredentialStoreResult<Unit> =
            CredentialStoreResult.Success(Unit)
        override fun remove(credential: ProviderCredentialId): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)
        override fun migrateLegacyCredentials(): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)
        override fun resetForReentry(): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)
    }
}
