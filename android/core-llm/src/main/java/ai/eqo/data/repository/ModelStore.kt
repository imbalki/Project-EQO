/*
 * ADAPTER (TASK-004 Phase 2) - minimal interface replacing the quarantined
 * upstream class com.opendroid.ai.data.repository.ModelRepository (540 lines,
 * Room + WorkManager + file management; not in Phase One scope). Members are
 * exactly those the moved code (LiteRTLMProvider) calls.
 */
package ai.eqo.data.repository

import ai.eqo.core.llm.OnDeviceModelSpec
import ai.eqo.data.db.entities.ModelEntity
import kotlinx.coroutines.flow.Flow

interface ModelStore {
    val allModelsFlow: Flow<List<ModelEntity>>

    fun resolveLiteRTSpec(modelId: String): OnDeviceModelSpec?
}
