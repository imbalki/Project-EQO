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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class OpenRouterAudioTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val directory =
        Files
            .createTempDirectory(
                java.io.File(System.getenv("TMPDIR") ?: System.getProperty("java.io.tmpdir")).toPath(),
                "eqo-audio-test",
            ).toFile()
    private var acceptsAudio = true
    private var completionCode = 200
    private var uploads = 0
    private var requests = 0
    private var payload = ""
    private var finishReason = "stop"
    private val credentials = FakeCredentials()
    private val client =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests++
                val catalog = request.url.encodedPath.endsWith("/models")
                val body =
                    if (catalog) {
                        val modality = if (acceptsAudio) "audio" else "text"
                        """{"data":[{"id":"test/model","architecture":{"input_modalities":["$modality"]}}]}"""
                    } else {
                        assertEquals("Bearer synthetic-test-key", request.header("Authorization"))
                        uploads++
                        val buffer = Buffer()
                        request.body!!.writeTo(buffer)
                        payload = buffer.readUtf8()
                        """{"choices":[{"finish_reason":"$finishReason","message":{"content":"verbatim words"}}]}"""
                    }
                Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (catalog) 200 else completionCode)
                    .message("synthetic")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()
    private val repository =
        SettingsRepository(
            dataStore =
                PreferenceDataStoreFactory.create(scope = scope) {
                    directory.resolve("settings.preferences_pb")
                },
            providerCredentialStore = credentials,
            runStartupMigration = false,
        )
    private val provider = OpenRouterProvider(client, repository)

    @After
    fun cleanup() {
        scope.cancel()
        client.dispatcher.executorService.shutdown()
        directory.deleteRecursively()
    }

    @Test
    fun audioUsesConfiguredModelAndVerbatimPrompt() =
        runBlocking {
            // No display-name key exists in the legacy configuration map.
            assertTrue(
                repository.llmConfig
                    .first()
                    .apiKeys["OpenRouter"]
                    .isNullOrBlank(),
            )
            assertEquals("verbatim words", provider.transcribe("test/model", InputAudio("synthetic-audio"), "hi-IN"))
            val json = JsonParser.parseString(payload).asJsonObject
            assertEquals("test/model", json.get("model").asString)
            assertEquals(4096, json.get("max_tokens").asInt)
            assertFalse(json.has("response_format"))
            val messages = json.getAsJsonArray("messages")
            assertTrue(
                messages[0]
                    .asJsonObject
                    .get("content")
                    .asString
                    .contains("verbatim"),
            )
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
    fun cachedAudioCatalogAvoidsAnotherMetadataRequest() =
        runBlocking {
            val catalog =
                OpenRouterModelCatalog.parse(
                    """{"data":[{"id":"test/model","architecture":{"input_modalities":["audio"]}}]}""",
                )
            assertEquals("verbatim words", provider.transcribe("test/model", InputAudio("audio"), "en-IN", catalog))
            assertEquals(1, requests)
            assertEquals(1, uploads)
        }

    @Test
    fun payloadCannotAppearInRequestRenderingOrHttpErrors() {
        assertFalse(
            LLMRequest("prompt", emptyList(), inputAudio = InputAudio("synthetic-audio"))
                .toString()
                .contains("synthetic-audio"),
        )
        completionCode = 400
        val failure =
            assertThrows(java.io.IOException::class.java) {
                runBlocking { provider.transcribe("test/model", InputAudio("synthetic-audio"), "en-IN") }
            }
        assertEquals("Audio provider request failed", failure.message)
        assertEquals(400, (failure as AudioProviderException).status)
    }

    @Test
    fun cancellationCancelsProviderCallWithoutNetwork() =
        runBlocking {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val observed = AtomicReference<okhttp3.Call>()
            val fakeClient =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        observed.set(chain.call())
                        entered.countDown()
                        release.await(10, TimeUnit.SECONDS)
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("synthetic")
                            .body("{}".toResponseBody("application/json".toMediaType()))
                            .build()
                    }.build()
            try {
                val job =
                    launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                        val provider = OpenRouterProvider(fakeClient, repository)
                        provider.transcribe("test/model", InputAudio("audio"), "en-IN")
                    }
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                job.cancelAndJoin()
                assertTrue(observed.get().isCanceled())
            } finally {
                release.countDown()
                fakeClient.dispatcher.executorService.shutdown()
            }
        }

    @Test
    fun cancellationClosesAStalledResponseBodyAfterHeaders() =
        runBlocking {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val closed = AtomicBoolean(false)
            val blockedBody =
                object : ResponseBody() {
                    private val input =
                        object : Source {
                            override fun read(
                                sink: Buffer,
                                byteCount: Long,
                            ): Long {
                                entered.countDown()
                                release.await(10, TimeUnit.SECONDS)
                                throw java.io.IOException("synthetic stalled body")
                            }

                            override fun timeout(): Timeout = Timeout.NONE

                            override fun close() {
                                closed.set(true)
                                release.countDown()
                            }
                        }.buffer()

                    override fun contentType() = "application/json".toMediaType()

                    override fun contentLength(): Long = -1L

                    override fun source() = input
                }
            val fakeClient =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain ->
                        Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("synthetic")
                            .body(blockedBody)
                            .build()
                    }.build()
            try {
                val job =
                    launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                        val provider = OpenRouterProvider(fakeClient, repository)
                        provider.transcribe("test/model", InputAudio("audio"), "en-IN")
                    }
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                job.cancelAndJoin()
                assertTrue(closed.get())
            } finally {
                release.countDown()
                fakeClient.dispatcher.executorService.shutdown()
            }
        }

    @Test
    fun truncatedTranscriptIsNeverReturnedAsComplete() {
        finishReason = "length"
        assertThrows(AudioIncompleteException::class.java) {
            runBlocking { provider.transcribe("test/model", InputAudio("synthetic-audio"), "hi-IN") }
        }
    }

    @Test
    fun missingKeyFailsBeforeCatalogOrAudioRequest() {
        credentials.hasKey = false
        assertThrows(AudioKeyMissingException::class.java) {
            runBlocking { provider.transcribe("test/model", InputAudio("synthetic-audio"), "en-IN") }
        }
        assertEquals(0, uploads)
        assertEquals(0, requests)
    }

    @Test
    fun inaccessibleStoreIsNotReportedAsAnAbsentKey() {
        credentials.unavailable = true
        val failure =
            assertThrows(java.io.IOException::class.java) {
                runBlocking { provider.transcribe("test/model", InputAudio("audio"), "en-IN") }
            }
        assertEquals(java.io.IOException::class.java, failure.javaClass)
        assertEquals(0, requests)
    }

    private class FakeCredentials : ProviderCredentialStore {
        var hasKey = true
        var unavailable = false
        override val recoveryState =
            MutableStateFlow<ProviderCredentialRecoveryState>(ProviderCredentialRecoveryState.Ready)

        override fun read(credential: ProviderCredentialId): CredentialStoreResult<String?> =
            if (unavailable) {
                CredentialStoreResult.StorageUnavailable
            } else {
                CredentialStoreResult.Success(
                    if (hasKey && credential == ProviderCredentialId.ApiKey("openrouter")) {
                        "synthetic-test-key"
                    } else {
                        null
                    },
                )
            }

        override fun readProviderApiKeys(): CredentialStoreResult<Map<String, String>> =
            CredentialStoreResult.Success(if (hasKey) mapOf("openrouter" to "synthetic-test-key") else emptyMap())

        override fun write(
            credential: ProviderCredentialId,
            value: String,
        ): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)

        override fun remove(credential: ProviderCredentialId): CredentialStoreResult<Unit> {
            val result = CredentialStoreResult.Success(Unit)
            return result
        }

        override fun migrateLegacyCredentials(): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)

        override fun resetForReentry(): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)
    }
}
