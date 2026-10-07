// Origin: EQO TASK-077 (#20), non-secret plan approval preference.
package ai.eqo.task

import android.content.Context
import androidx.core.content.edit

object PlanApprovalSettings {
    fun required(context: Context): Boolean =
        context.getSharedPreferences("eqo_plan_settings", Context.MODE_PRIVATE).getBoolean("require_approval", true)

    fun setRequired(
        context: Context,
        required: Boolean,
    ) {
        context.getSharedPreferences("eqo_plan_settings", Context.MODE_PRIVATE).edit {
            putBoolean("require_approval", required)
        }
    }
}
