/*
 * ADAPTER (TASK-004 Phase 2) - minimal interface replacing the quarantined
 * upstream class com.opendroid.ai.core.memory.MemoryManager (Room/repository-backed,
 * not in Phase One scope). Members are exactly those the moved code calls.
 * Implementation lands with the EQO app layer in a later task.
 */
package com.opendroid.ai.core.memory

import com.opendroid.ai.core.agent.Contact
import com.opendroid.ai.data.models.ChatMessage

interface MemoryStore {
    suspend fun storeMessage(
        message: ChatMessage,
        sessionId: String,
    )

    suspend fun getRelevantContext(currentGoal: String): String

    suspend fun logTaskExecution(
        stepId: String,
        planId: String,
        description: String,
        actionType: String,
        params: Map<String, String>,
        success: Boolean,
        resultData: String?,
        errorMessage: String?,
    )

    suspend fun storeContactPreference(
        query: String,
        contact: Contact,
    )

    suspend fun recallContactPreference(query: String): Contact?
}
