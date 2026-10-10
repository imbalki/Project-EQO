package ai.eqo.task

import ai.eqo.core.agent.AttachmentFailure
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
            "control_not_found",
            "a11y_action_rejected",
            "a11y_empty_observation",
            "study_action_not_applied",
        )

    fun code(result: ExecuteResult): String =
        when (result) {
            is ExecuteResult.Success -> "executor_success_not_independent_receipt"
            is ExecuteResult.Failure ->
                AttachmentFailure.kind(result.reason)
                    ?: result.reason.takeIf { it in failureCodes } ?: "execution_failed"
            is ExecuteResult.Interrupted -> "apply_interrupted_effect_unknown"
        }

    fun result(
        stepIndex: Int,
        result: ExecuteResult,
    ) {
        Log.i("EqoRun", "step=$stepIndex code=${code(result)} reason=${failureKind(result)}")
    }

    fun failureKind(result: ExecuteResult): String {
        val reason = (result as? ExecuteResult.Failure)?.reason.orEmpty()
        return when {
            AttachmentFailure.kind(reason) != null -> requireNotNull(AttachmentFailure.kind(reason))
            reason.contains("All files access", ignoreCase = true) -> "needs_all_files_access"
            reason in setOf("a11y_not_bound", "a11y_disabled") ||
                reason.contains("accessibility in Settings", ignoreCase = true) -> "accessibility_off"
            reason.contains("No EQO screenshot", ignoreCase = true) -> "no_eqo_screenshot_yet"
            reason.startsWith("No files found for ") -> "no_matching_file"
            reason.contains("EQO cannot capture its own") -> "eqo_in_foreground"
            reason in setOf("a11y_secure_window", "This is a protected screen. EQO will not read it.") ->
                "protected_screen"
            reason.contains("empty", ignoreCase = true) -> "empty"
            reason == "control_not_found" -> "control_not_found"
            reason.contains("not found", ignoreCase = true) -> "not_found"
            result is ExecuteResult.Failure -> "execution_failed"
            else -> "none"
        }
    }
}
