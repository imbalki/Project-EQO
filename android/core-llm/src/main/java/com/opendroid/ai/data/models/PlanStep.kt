// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/data/models/PlanStep.kt
package com.opendroid.ai.data.models

import kotlinx.serialization.Serializable

@Serializable
data class PlanStep(
    val stepId: String,
    val order: Int,
    val description: String,
    val action: String,
    val params: Map<String, String> = emptyMap(),
    val dependsOn: List<String> = emptyList(),
    val canParallelize: Boolean = false,
    val fallback: String = "",
    var status: StepStatus = StepStatus.PENDING,
    var result: String? = null,
    var error: String? = null,
)

enum class StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
}
