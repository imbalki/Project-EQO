// TASK-006 security pass F2 (issue #11): the Gemini model-list request must not
// carry the API key in its URL, and fetch failures must not log raw exception
// messages.
package ai.eqo.core.llm

import ai.eqo.core.security.CredentialStoreResult
import ai.eqo.core.security.CredentialStoreResult.Success
import ai.eqo.core.security.ProviderCredentialId
import ai.eqo.core.security.ProviderCredentialRecoveryState
import ai.eqo.core.security.ProviderCredentialStore
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
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.IOException
import java.nio.file.Files

/**
 * F2 of the TASK-006 security pass: the Gemini key travels in the
 * `x-goog-api-key` header (as [ai.eqo.core.llm.providers.GeminiProvider]
 * already does for chat), never as a `?key=` query parameter — query parameters
 * surface through server-side URL logs, `Request.url` rendering and exception
 * messages. The two log sites that could quote such a message log the exception
 * class name and fixed text only.
 *
 * Robolectric supplies a real `org.json` and captures logcat via [ShadowLog].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelFetcherSecurityTest {
    /** Synthetic; only its absence from URLs and log lines is asserted. */
    private val apiKey = "AIza...test-0001"

    private val geminiModelsBody =
        """
        {"models":[{"name":"models/gemini-2.0-flash","displayName":"Gemini 2.0 Flash",
         "inputTokenLimit":1048576,"supportedGenerationMethods":["generateContent"]}]}
        """.trimIndent()

    @Before
    fun setUp() {
        ShadowLog.clear()
    }

    @After
    fun tearDown() {
        ShadowLog.clear()
    }

    @Test
    fun `gemini model list request sends the key as a header and never as a query parameter`() =
        runBlocking {
            val recorded = mutableListOf<Request>()
            val client = recordingClient(recorded)

            val outcome = newFetcher(client).fetchModels(GEMINI)

            assertTrue("expected Success but was $outcome", outcome is ModelFetchOutcome.Success)
            assertEquals(
                "gemini-2.0-flash",
                (outcome as ModelFetchOutcome.Success).models.single().id,
            )
            val request = recorded.single()
            assertNull(
                "the key must not travel as a query parameter: ${request.url}",
                request.url.queryParameter("key"),
            )
            assertFalse(
                request.url.encodedQuery
                    .orEmpty()
                    .contains(apiKey),
            )
            assertEquals(apiKey, request.header("x-goog-api-key"))
        }

    @Test
    fun `a model fetch failure logs the exception class name and never the raw message`() =
        runBlocking {
            val keyBearingMessage =
                "Failed to reach https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor { _ -> throw IOException(keyBearingMessage) }
                    .build()

            val outcome = newFetcher(client).fetchModels(GEMINI)

            assertTrue("expected Failed but was $outcome", outcome is ModelFetchOutcome.Failed)
            val entries = ShadowLog.getLogs().filter { it.tag == "ModelFetcher" }
            assertTrue("expected a ModelFetcher log line", entries.isNotEmpty())
            entries.forEach { entry ->
                assertFalse(
                    "raw exception message leaked: ${entry.msg}",
                    entry.msg.contains(apiKey) || entry.msg.contains("Failed to reach"),
                )
                assertNull(
                    "the throwable renders its message in logcat: ${entry.throwable}",
                    entry.throwable,
                )
            }
            assertTrue(entries.any { it.msg.contains("IOException") })
        }

    /** Records the outbound request and answers with a Gemini model list. */
    private fun recordingClient(recorded: MutableList<Request>): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                recorded += chain.request()
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(geminiModelsBody.toResponseBody(JSON_MEDIA_TYPE))
                    .build()
            }.build()

    private fun newFetcher(client: OkHttpClient): ModelFetcher = ModelFetcher(client, newSettingsRepository())

    private fun newSettingsRepository() =
        SettingsRepository(
            dataStore =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                    produceFile = {
                        Files
                            .createTempDirectory("opendroid-model-fetcher-test")
                            .resolve("settings.preferences_pb")
                            .toFile()
                    },
                ),
            providerCredentialStore = FakeCredentialStore(mapOf(GEMINI to apiKey)),
            runStartupMigration = false,
        )

    /** Reads the API keys the fetch under test resolves; stores nothing. */
    private class FakeCredentialStore(
        private val apiKeys: Map<String, String>,
    ) : ProviderCredentialStore {
        override val recoveryState: StateFlow<ProviderCredentialRecoveryState> =
            MutableStateFlow(ProviderCredentialRecoveryState.Ready)

        override fun read(credential: ProviderCredentialId): CredentialStoreResult<String?> = Success(null)

        override fun readProviderApiKeys(): CredentialStoreResult<Map<String, String>> = Success(apiKeys)

        override fun write(
            credential: ProviderCredentialId,
            value: String,
        ): CredentialStoreResult<Unit> = Success(Unit)

        override fun remove(credential: ProviderCredentialId): CredentialStoreResult<Unit> = Success(Unit)

        override fun migrateLegacyCredentials(): CredentialStoreResult<Unit> = Success(Unit)

        override fun resetForReentry(): CredentialStoreResult<Unit> = Success(Unit)
    }

    private companion object {
        const val GEMINI = "Google Gemini"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
