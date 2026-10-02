/*
 * ADAPTER (TASK-004 Phase 2) - minimal interface replacing the quarantined
 * upstream class com.opendroid.ai.data.repository.ConversationRepository
 * (Room/EQODatabase-backed, not in Phase One scope). Members are exactly
 * those the moved code calls. Implementation lands with the EQO app layer.
 */
package ai.eqo.data.repository

import ai.eqo.data.models.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatHistoryStore {
    fun getMessages(sessionId: String): Flow<List<ChatMessage>>

    suspend fun insertMessage(
        sessionId: String,
        message: ChatMessage,
    )

    suspend fun getLastMessages(
        sessionId: String,
        limit: Int,
    ): List<ChatMessage>

    suspend fun ensureCurrentSessionId(): String
}
