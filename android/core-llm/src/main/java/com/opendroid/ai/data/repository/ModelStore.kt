/*
 * ADAPTER (TASK-004 Phase 2) - minimal interface replacing the quarantined
 * upstream class com.opendroid.ai.data.repository.ModelRepository (540 lines,
 * Room + WorkManager + file management; not in Phase One scope). Members are
 * exactly those the moved code (LiteRTLMProvider) calls.
 */
package com.opendroid.ai.data.repository

import com.opendroid.ai.core.llm.OnDeviceModelSpec
import com.opendroid.ai.data.db.entities.ModelEntity
import kotlinx.coroutines.flow.Flow

interface ModelStore {
    val allModelsFlow: Flow<List<ModelEntity>>

    fun resolveLiteRTSpec(modelId: String): OnDeviceModelSpec?
}
