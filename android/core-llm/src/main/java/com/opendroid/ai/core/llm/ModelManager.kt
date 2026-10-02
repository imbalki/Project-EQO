// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/core/llm/ModelManager.kt
package com.opendroid.ai.core.llm

typealias OnDeviceModel = OnDeviceModelSpec

interface ModelManager {
    suspend fun download(model: OnDeviceModel)

    suspend fun delete(model: OnDeviceModel)

    suspend fun load(model: OnDeviceModel)

    suspend fun isDownloaded(model: OnDeviceModel): Boolean

    suspend fun currentModel(): OnDeviceModel?
}
