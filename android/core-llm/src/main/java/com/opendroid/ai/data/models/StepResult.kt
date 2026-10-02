// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/data/models/StepResult.kt
package com.opendroid.ai.data.models

import kotlinx.serialization.Serializable

@Serializable
data class StepResult(
    val stepId: String,
    val success: Boolean,
    val data: String? = null,
    val error: String? = null,
)
