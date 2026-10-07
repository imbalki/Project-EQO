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

    // Only short identifier-like reasons are logged; free text could carry user content.
    private val SAFE_REASON = Regex("[A-Za-z0-9_ .:'-]{1,80}")

    fun result(
        stepIndex: Int,
        result: ExecuteResult,
    ) {
        val code =
            when (result) {
                is ExecuteResult.Success -> "executor_success_not_independent_receipt"
                is ExecuteResult.Failure -> result.reason.takeIf { it in failureCodes } ?: "execution_failed"
                is ExecuteResult.Interrupted -> "apply_interrupted_effect_unknown"
            }
        val kind =
            when (result) {
                is ExecuteResult.Success -> "Success"
                is ExecuteResult.Failure -> "Failure"
                is ExecuteResult.Interrupted -> "Interrupted"
            }
        val detail =
            (result as? ExecuteResult.Failure)
                ?.reason
                ?.takeIf { SAFE_REASON.matches(it) }
                ?.let { " reason=$it" }
                .orEmpty()
        Log.i("EqoRun", "step=$stepIndex execute=$kind code=$code$detail")
    }
}
