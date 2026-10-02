// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/data/models/Plan.kt
package ai.eqo.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Plan(
    val planId: String,
    val goal: String,
    val estimatedDuration: String,
    val estimatedSteps: Int,
    val steps: List<PlanStep>,
    val status: PlanStatus = PlanStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class PlanStatus {
    PROPOSED,
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    PAUSED,
    CANCELLED,
}
