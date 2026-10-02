// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/data/models/Memory.kt
package ai.eqo.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Memory(
    val key: String,
    val value: String,
    val type: MemoryType,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class MemoryType {
    WORKING,
    EPISODIC,
    SEMANTIC,
    PROCEDURAL,
}
