package ai.eqo.core.llm

import ai.eqo.core.llm.error.LLMError
import ai.eqo.core.llm.error.LLMErrorMapper
import ai.eqo.core.llm.error.LLMException
import ai.eqo.core.llm.error.ProviderErrorDetail
import ai.eqo.core.llm.error.SecretRegistry
import ai.eqo.core.llm.error.toSafeProviderException
import ai.eqo.data.models.ChatMessage
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenRouter BYOK connection test.
 *
 * Posts a minimal OpenAI-compatible chat-completions probe against a caller
 * supplied endpoint and maps the outcome to the shared [ConnectionTestState]:
 *
 *  - HTTP 200 with a conversation payload  -> [ConnectionTestState.Connected]
 *  - HTTP 401                              -> failed, [LLMError.AuthInvalid]
 *  - HTTP 429                              -> failed, rate limit / quota category
 *  - DNS failure / refused connect         -> failed, [LLMError.Network] (offline)
 *  - read timeout                          -> failed, [LLMError.Network]
 *
 * The candidate key never persists and never reaches a rendered failure:
 * [registerSecret] scrubs it from typed errors while the test runs, and it is
 * released before the outcome leaves this class.
 */
class ConnectionTestRunner(
    private val client: OkHttpClient,
) {
    private val gson = com.google.gson.Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Runs one probe against [endpoint]. Returns the terminal test state;
     * this method never stores, logs, or returns the raw key.
     *
     * The endpoint must be https — `http` is accepted only for a loopback host
     * (localhost, 127.0.0.1, ::1), which is what the in-process mock tests use.
     * Anything else is rejected with a typed failure before a request is built.
     */
    fun run(
        endpoint: String,
        apiKey: String,
        model: String,
    ): ConnectionTestState {
        require(endpoint.isNotBlank()) { "OpenRouter endpoint is not configured." }
        require(apiKey.isNotBlank()) { "OpenRouter API key is not set." }
        require(model.isNotBlank()) { "OpenRouter model is not selected." }

        // TASK-006 security pass F4: the probe posts the key as
        // `Authorization: Bearer`, so a caller-supplied endpoint must not be
        // plain http off-loopback. The rejection carries no free-text field —
        // nothing can echo the endpoint or the key.
        if (!isTransportAllowed(endpoint)) {
            return ConnectionTestState.Failed(
                provider = PROVIDER_DISPLAY_NAME,
                model = model,
                error = LLMError.RequestInvalid,
                testedAtMillis = System.currentTimeMillis(),
            )
        }

        val registration = registerSecret(apiKey)
        try {
            return probe(endpoint = endpoint, apiKey = apiKey, model = model)
        } finally {
            registration.close()
        }
    }

    /** https anywhere; `http` only on a loopback host. */
    private fun isTransportAllowed(endpoint: String): Boolean {
        val url = endpoint.toHttpUrlOrNull() ?: return false
        return when (url.scheme) {
            "https" -> true
            "http" -> isLoopbackHost(url.host)
            else -> false
        }
    }

    private fun isLoopbackHost(host: String): Boolean {
        val normalized = host.lowercase().removePrefix("[").removeSuffix("]")
        return normalized == "localhost" || normalized == "127.0.0.1" || normalized == "::1"
    }

    private fun probe(
        endpoint: String,
        apiKey: String,
        model: String,
    ): ConnectionTestState =
        try {
            executeProbe(endpoint, apiKey, model)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (io: IOException) {
            ConnectionTestPlanner.fromException(
                provider = PROVIDER_DISPLAY_NAME,
                model = model,
                throwable = io,
                testedAtMillis = System.currentTimeMillis(),
            )
        } catch (runtime: IllegalStateException) {
            ConnectionTestPlanner.fromException(
                provider = PROVIDER_DISPLAY_NAME,
                model = model,
                throwable = runtime,
                testedAtMillis = System.currentTimeMillis(),
            )
        } catch (runtime: IllegalArgumentException) {
            ConnectionTestPlanner.fromException(
                provider = PROVIDER_DISPLAY_NAME,
                model = model,
                throwable = runtime,
                testedAtMillis = System.currentTimeMillis(),
            )
        }

    private fun buildBodyJson(
        model: String,
        request: LLMRequest,
    ): String =
        gson.toJson(
            linkedMapOf<String, Any>(
                "model" to model,
                "messages" to request.messages.toOpenAIMessages(request.systemPrompt),
                "max_tokens" to PROBE_MAX_TOKENS,
            ),
        )

    private fun probeRequest(
        endpoint: String,
        apiKey: String,
        model: String,
        request: LLMRequest,
    ): Request =
        Request
            .Builder()
            .url(trimTrailingSlash(endpoint) + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(buildBodyJson(model, request).toRequestBody(mediaType))
            .build()

    private fun executeProbe(
        endpoint: String,
        apiKey: String,
        model: String,
    ): ConnectionTestState {
        val startedAt = System.nanoTime()
        val request =
            LLMRequest(
                systemPrompt = "Connection test.",
                messages = listOf(ChatMessage("0", "ping", ChatMessage.Sender.USER)),
                model = model,
                responseFormat = ResponseFormat.TEXT,
                providerConfig = ProviderRequestConfig(apiKey = apiKey, endpoint = endpoint),
            )
        val httpRequest = probeRequest(endpoint, apiKey, model, request)

        return client.newCall(httpRequest).execute().use { response ->
            if (response.isSuccessful) {
                val latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                ConnectionTestPlanner.success(
                    provider = PROVIDER_DISPLAY_NAME,
                    model = model,
                    latencyMs = latencyMs,
                    testedAtMillis = System.currentTimeMillis(),
                )
            } else {
                ConnectionTestPlanner.fromException(
                    provider = PROVIDER_DISPLAY_NAME,
                    model = model,
                    throwable = response.toTypedFailure(apiKey, request),
                    testedAtMillis = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun registerSecret(apiKey: String): AutoCloseable = SecretRegistry.register(apiKey)

    /**
     * Maps an unsuccessful response to a typed 401/429/offline/timeout
     * failure: the body is seen only at the classification endpoint and only
     * non-secret-fragment vendor tokens can surface on the exception.
     */
    private fun Response.toTypedFailure(
        apiKey: String,
        request: LLMRequest,
    ): LLMException =
        try {
            toSafeProviderException(
                provider = ProviderErrorDetail.Provider.OPENROUTER,
                request = request,
                knownSecrets = listOf(apiKey),
            )
        } catch (t: IOException) {
            LLMErrorMapper.fromThrowable(PROVIDER_DISPLAY_NAME, request.model.orEmpty(), t)
        }

    private fun trimTrailingSlash(endpoint: String): String = endpoint.trimEnd('/')

    private companion object {
        const val PROVIDER_DISPLAY_NAME = "OpenRouter"
        const val PROBE_MAX_TOKENS = 16
    }
}
