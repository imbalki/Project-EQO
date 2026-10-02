// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/data/models/HabitEvent.kt
package com.opendroid.ai.data.models

import kotlinx.serialization.Serializable

enum class HabitEventType {
    APP_OPEN,
    AGENT_ACTION,
    URL_OPEN,
    SYSTEM_EVENT,
}

@Serializable
data class HabitEvent(
    val id: String,
    val eventType: HabitEventType,
    val packageName: String,
    val actionName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val minuteOfHour: Int,
    val metadata: Map<String, String> = emptyMap(),
)
