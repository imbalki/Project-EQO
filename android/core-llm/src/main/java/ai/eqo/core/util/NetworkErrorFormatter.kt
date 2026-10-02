// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/core/util/NetworkErrorFormatter.kt
package ai.eqo.core.util

import ai.eqo.core.agent.ChatErrorUiState
import ai.eqo.core.agent.guidance
import ai.eqo.core.agent.title
import ai.eqo.core.llm.error.LLMException
import android.util.Log

object NetworkErrorFormatter {
    private const val TAG = "NetworkErrorFormatter"

    fun toUserMessage(error: Throwable?): String {
        if (error is LLMException) {
            val state =
                ChatErrorUiState.fromException(
                    sessionId = "network",
                    requestId = "network",
                    runId = "network",
                    failure = error,
                )
            return "${state.title()} ${state.guidance()}"
        }
        val message =
            error?.localizedMessage ?: error?.message
                ?: return "Something went wrong. Please try again."
        return toUserMessage(message)
    }

    fun toUserMessage(message: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("unable to resolve host") ||
                lower.contains("no address associated with hostname") ||
                lower.contains("network is unreachable") ||
                lower.contains("failed to connect") &&
                lower.contains("enetunreach") ->
                "No internet connection. Check your network and try again."

            lower.contains("timeout") || lower.contains("timed out") ->
                "The request timed out. Check your connection and try again."

            lower.contains(listOf("10", "0", "2", "2").joinToString(".")) || lower.contains("connection refused") ->
                "Can't reach the configured server. Check your provider settings."

            else -> {
                Log.e(TAG, "Unhandled network/provider error: $message")
                "Something went wrong. Please try again."
            }
        }
    }
}
