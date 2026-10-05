// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/core/agent/VisionEngine.kt (text fallback).
package ai.eqo.actions.impl

import ai.eqo.accessibility.UntrustedScreenText
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.llm.LLMProvider
import ai.eqo.core.llm.LLMRequest
import ai.eqo.core.llm.ResponseFormat
import ai.eqo.data.models.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** Explicit provider; no donor Hilt/Room factory or raw screenshot capture. */
class TextScreenAnalyzer(
    private val provider: suspend () -> LLMProvider,
) : ScreenAnalyzer {
    override suspend fun analyze(
        question: String,
        screenText: String,
    ): ActionResult {
        if (screenText.isBlank()) return ActionResult.Failure("No visible screen text could be read; no image was sent.")
        return try {
            val response =
                withTimeout(30_000L) {
                    provider().complete(
                        LLMRequest(
                            systemPrompt =
                                UntrustedScreenText.DIRECTIVE +
                                    " Analyze Android screen content from extracted text. Be concise and accurate.",
                            messages =
                                listOf(
                                    ChatMessage(
                                        id = UUID.randomUUID().toString(),
                                        text =
                                            "I extracted the following text from the Android screen:\n" +
                                                UntrustedScreenText.wrap(screenText) + "\nUser question: $question\n" +
                                                "Based on visible text, describe the screen and answer the question.",
                                        sender = ChatMessage.Sender.USER,
                                    ),
                                ),
                            temperature = 0.3f,
                            maxTokens = 500,
                            responseFormat = ResponseFormat.TEXT,
                        ),
                    )
                }
            val answer = response.content.trim()
            if (answer.isEmpty()) {
                ActionResult.Failure("Screen analysis returned no answer.")
            } else {
                ActionResult.Success(
                    mapOf(
                        "message" to "Analysed from screen text, not the image.\n$answer",
                        "captureMode" to "text-only",
                    ),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ActionResult.Failure("Screen text could not be analyzed. No analysis success was verified.")
        }
    }
}
