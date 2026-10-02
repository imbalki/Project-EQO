// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/data/models/UserProfile.kt
package ai.eqo.data.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val name: String? = null,
    val age: Int? = null,
    val location: String? = null,
    val timezone: String? = null,
    val language: String? = null,
    val contacts: Map<String, String> = emptyMap(), // Nickname -> Phone
    val preferences: Map<String, String> = emptyMap(), // key -> value
    val routines: Map<String, String> = emptyMap(), // trigger -> action
    val behaviorPatterns: List<String> = emptyList(),
)
