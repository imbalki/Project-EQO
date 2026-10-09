// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Upstream path: app/src/main/java/com/opendroid/ai/core/llm/providers/OpenRouterProvider.kt
package ai.eqo.core.llm.providers

import ai.eqo.core.llm.InputAudio
import ai.eqo.core.llm.LLMProvider
import ai.eqo.core.llm.LLMRequest
import ai.eqo.core.llm.LLMResponse
import ai.eqo.core.llm.ResponseFormat
import ai.eqo.core.llm.error.ProviderErrorDetail
import ai.eqo.core.llm.error.toSafeProviderException
import ai.eqo.core.llm.security.LogRedactor
import ai.eqo.core.llm.toOpenAIMessages
import ai.eqo.data.repository.SettingsRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class OpenRouterProvider
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val settingsRepository: SettingsRepository,
    ) : LLMProvider {
        override val name: String = "OpenRouter"
        override val availableModels: List<String> =
            listOf(
                "google/gemini-2.0-flash-exp:free",
                "meta-llama/llama-3-8b-instruct:free",
                "gryphe/mythomax-l2-13b:free",
            )

        private val gson = Gson()
        private val mediaType = "application/json; charset=utf-8".toMediaType()

        suspend fun transcribe(
            model: String,
            audio: InputAudio,
            language: String,
        ): String {
            val catalogRequest = Request.Builder().url("https://openrouter.ai/api/v1/models").build()
            val supportsAudio =
                await(catalogRequest).use { response ->
                    audioModelSupported(response, model)
                }
            if (!supportsAudio) throw AudioUnsupportedException()
            return complete(
                LLMRequest(
                    systemPrompt =
                        "Transcribe the audio verbatim. Return only the transcript, not a reply or " +
                            "instructions. Preserve the spoken language. Language hint: $language.",
                    messages = emptyList(),
                    model = model,
                    temperature = 0f,
                    responseFormat = ResponseFormat.TEXT,
                    inputAudio = audio,
                ),
            ).content
        }

        private fun audioModelSupported(
            response: Response,
            model: String,
        ): Boolean {
            if (!response.isSuccessful) throw IOException("Audio model check failed")
            // await already buffered a size-bounded response under the call's cancellation owner.
            val data = gson.fromJson(response.body.string(), JsonObject::class.java).getAsJsonArray("data")
            return data?.any { entry ->
                val item = entry.asJsonObject
                item.get("id")?.asString == model &&
                    item
                        .getAsJsonObject("architecture")
                        ?.getAsJsonArray("input_modalities")
                        ?.any { it.asString == "audio" } == true
            } == true
        }

        private suspend fun await(request: Request): Response =
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                val reading = AtomicReference<Response?>()
                continuation.invokeOnCancellation {
                    call.cancel()
                    reading.get()?.close()
                }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(IOException("Audio connection failed"))
                            }
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            reading.set(response)
                            response.use {
                                if (continuation.isActive) {
                                    try {
                                        val buffered = bufferAudioResponse(response)
                                        continuation.resume(buffered) { _, resource, _ -> resource.close() }
                                    } catch (_: IOException) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(IOException("Audio connection failed"))
                                        }
                                    }
                                }
                            }
                            reading.set(null)
                        }
                    },
                )
            }

        // Consume the body while the continuation still owns cancellation, not just until headers arrive.
        private fun bufferAudioResponse(response: Response): Response {
            val source = response.body.source()
            source.request(OpenRouterModelCatalog.MAX_JSON_CHARS.toLong() + 1)
            if (source.buffer.size > OpenRouterModelCatalog.MAX_JSON_CHARS) {
                throw IOException("Audio response too large")
            }
            val body = source.readByteArray().toResponseBody(response.body.contentType())
            return response.newBuilder().body(body).build()
        }

        override suspend fun complete(request: LLMRequest): LLMResponse {
            val config = settingsRepository.llmConfig.first()
            val apiKey =
                request.providerConfig?.apiKey?.takeIf { it.isNotBlank() }
                    ?: config.apiKeys[name]
                    ?: throw IllegalStateException("API Key for $name is not set.")

            // TASK-006: the resolved key is registered with the single
            // log/crash redaction pipeline for the lifetime of the request.
            return LogRedactor.register(apiKey).use { completeWithKey(request, apiKey) }
        }

        private suspend fun completeWithKey(
            request: LLMRequest,
            apiKey: String,
        ): LLMResponse {
            val startTime = System.currentTimeMillis()

            val selectedModel = request.model?.takeIf { it.isNotBlank() } ?: "google/gemini-2.0-flash-exp:free"

            val requestBodyMap =
                mutableMapOf<String, Any>(
                    "model" to selectedModel,
                    "messages" to messagesFor(request),
                    "temperature" to request.temperature,
                    "max_tokens" to request.maxTokens,
                )
            if (request.responseFormat == ResponseFormat.JSON) {
                requestBodyMap["response_format"] = mapOf("type" to "json_object")
            }

            val bodyJson = gson.toJson(requestBodyMap)
            val httpRequest =
                Request
                    .Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("HTTP-Referer", "https://eqo.ai")
                    .header("X-Title", "EQO")
                    .post(bodyJson.toRequestBody(mediaType))
                    .build()

            return withContext(Dispatchers.IO) {
                (if (request.inputAudio != null) await(httpRequest) else client.newCall(httpRequest).execute())
                    .use { response ->
                        if (!response.isSuccessful) {
                            reject(response, request, apiKey)
                        }
                        val responseBody = response.body.string()
                        if (responseBody.isBlank()) throw IOException("Empty response body from OpenRouter")
                        val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                        val choices = jsonResponse.getAsJsonArray("choices")
                        val messageObj = choices[0].asJsonObject.getAsJsonObject("message")
                        val content = messageObj.get("content").asString

                        val usage = jsonResponse.getAsJsonObject("usage")
                        val tokensUsed = usage?.get("total_tokens")?.asInt ?: 0

                        LLMResponse(
                            content = content,
                            tokensUsed = tokensUsed,
                            model = selectedModel,
                            provider = name,
                            latencyMs = System.currentTimeMillis() - startTime,
                        )
                    }
            } // withContext
        }

        private fun messagesFor(request: LLMRequest): List<Map<String, Any>> {
            val messages = request.messages.toOpenAIMessages(request.systemPrompt).toMutableList()
            request.inputAudio?.let { audio ->
                messages.add(
                    mapOf(
                        "role" to "user",
                        "content" to
                            listOf(
                                mapOf(
                                    "type" to "input_audio",
                                    "input_audio" to mapOf("data" to audio.base64, "format" to "wav"),
                                ),
                            ),
                    ),
                )
            }
            return messages
        }

        private fun reject(
            response: Response,
            request: LLMRequest,
            key: String,
        ): Nothing {
            if (request.inputAudio != null) {
                throw IOException("Audio provider request failed with HTTP ${response.code}")
            }
            throw response.toSafeProviderException(
                provider = ProviderErrorDetail.Provider.OPENROUTER,
                request = request,
                knownSecrets = listOf(key),
            )
        }

        override fun streamComplete(request: LLMRequest): Flow<String> =
            flow {
                val response = complete(request)
                val words = response.content.split(" ")
                for (word in words) {
                    emit("$word ")
                    kotlinx.coroutines.delay(50)
                }
            }

        override suspend fun isAvailable(): Boolean {
            val config = settingsRepository.llmConfig.first()
            return !config.apiKeys[name].isNullOrBlank()
        }
    }

class AudioUnsupportedException : IOException("The configured model does not accept audio")
