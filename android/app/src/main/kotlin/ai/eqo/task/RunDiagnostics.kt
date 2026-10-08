package ai.eqo.task

import ai.eqo.core.agent.ExecuteResult
import android.util.Log

/** Allowlisted codes, never arbitrary executor detail, parameters or exception messages. */
internal object RunDiagnostics {
    private val failureCodes =
        setOf(
            "a11y_not_bound",
            "a11y_disabled",
            "a11y_takeover",
            "a11y_secure_window",
            "a11y_no_active_root",
            "a11y_no_scrollable_node",
            "a11y_node_not_found",
            "a11y_action_rejected",
            "a11y_empty_observation",
            "study_action_not_applied",
        )

    fun code(result: ExecuteResult): String =
        when (result) {
            is ExecuteResult.Success -> "executor_success_not_independent_receipt"
            is ExecuteResult.Failure -> result.reason.takeIf { it in failureCodes } ?: "execution_failed"
            is ExecuteResult.Interrupted -> "apply_interrupted_effect_unknown"
        }

    fun result(
        stepIndex: Int,
        result: ExecuteResult,
    ) {
        Log.i("EqoRun", "step=$stepIndex code=${code(result)}")
    }
}
