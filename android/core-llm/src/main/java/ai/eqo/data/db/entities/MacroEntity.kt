// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: path: app/src/main/java/com/opendroid/ai/data/db/entities/MacroEntity.kt
package ai.eqo.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey val id: String,
    val name: String,
    val trigger: String,
    // Serialized List<PlanStep>
    val stepsJson: String,
    val isSystem: Boolean,
    val isEnabled: Boolean,
)
