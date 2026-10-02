// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/data/models/Macro.kt
package com.opendroid.ai.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Macro(
    val id: String,
    val name: String,
    val trigger: String, // voice trigger or cron expression
    val steps: List<PlanStep>,
    val isSystem: Boolean = false,
    val isEnabled: Boolean = true,
)
