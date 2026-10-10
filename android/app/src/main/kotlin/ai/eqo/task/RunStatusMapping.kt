package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.agent.AttachmentFailure
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.TaskDisplayText
import ai.eqo.core.llm.error.LLMError
import ai.eqo.study.FailureClass
import ai.eqo.study.RunReceipt
import ai.eqo.study.StepProgress
import ai.eqo.study.StepProgressState

/** Presentation only: never changes executor success, retries, gates or takeover. */
internal object RunStatusMapping {
    data class Text(
        val resource: Int,
        val argument: String = "",
    )

    fun progress(
        step: LoopStep?,
        progress: StepProgress,
        handoff: String?,
    ): StepProgress {
        val draft =
            progress.state == StepProgressState.DONE &&
                progress.name.lowercase() in setOf("compose_sms", "compose_email")
        val missingControl = progress.state == StepProgressState.FAILED && progress.detail == "control_not_found"
        val fileNeedsYou =
            AttachmentFailure.kind(progress.detail) in
                setOf("attachment_cancelled", "attachment_selection_required")
        val needsYou =
            draft ||
                missingControl ||
                fileNeedsYou ||
                (handoff != null && progress.state == StepProgressState.FAILED)
        return progress.copy(
            state = if (needsYou) StepProgressState.NEEDS_YOU else progress.state,
            detail = if (needsYou) handoff ?: progress.detail else progress.detail,
            targetLabel = targetLabel(step),
        )
    }

    private fun targetLabel(step: LoopStep?): String {
        val params = step?.action?.params.orEmpty()
        return params["target"] ?: params["searchText"] ?: params["viewId"] ?: params["view_id"]
            ?: params["appName"] ?: params["app_name"] ?: params["app"] ?: params["text"].orEmpty()
    }

    fun terminal(receipt: RunReceipt): String =
        if (receipt.terminal in setOf("FAILED", "COMPLETED") &&
            receipt.steps.any { it.state == StepProgressState.NEEDS_YOU } &&
            receipt.steps.none { it.state in setOf(StepProgressState.FAILED, StepProgressState.UNKNOWN) }
        ) {
            "NEEDS_YOU"
        } else {
            receipt.terminal
        }

    @Suppress("CyclomaticComplexMethod") // Explicit known reasons; arbitrary executor text is not a diagnosis.
    fun detail(original: StepProgress): Text? {
        if (original.state == StepProgressState.UNKNOWN) return null
        val step = original.copy(detail = original.detail.removePrefix("permission denied: "))
        return when {
            AttachmentFailure.message(step.detail) != null ->
                Text(R.string.run_user_action, requireNotNull(AttachmentFailure.message(step.detail)))
            step.state == StepProgressState.NEEDS_YOU -> handoff(step)
            step.detail == FailureClass.A11Y_LOST.repair ||
                step.detail in
                setOf(
                    "a11y_not_bound",
                    "a11y_disabled",
                    "Enable EQO accessibility in Settings before continuing.",
                )
            -> Text(R.string.run_accessibility_off)
            step.detail == FailureClass.BINDER_DEAD.repair -> Text(R.string.task_helper_lost)
            RunDiagnostics.failureKind(
                ai.eqo.core.agent.ExecuteResult
                    .Failure(step.detail),
            ) in
                setOf("no_eqo_screenshot_yet", "no_matching_file", "needs_all_files_access", "protected_screen") ->
                Text(R.string.run_user_action, fileFailureInstruction(step.detail))
            step.detail.contains("EQO did not save a screenshot") ->
                Text(
                    R.string.run_user_action,
                    "Open a non-protected app and check EQO accessibility is on, then try again.",
                )
            step.detail.startsWith("Android permission ") ->
                Text(
                    R.string.run_permission_missing,
                    permissionName(step.detail.substringAfter("Android permission ").substringBefore(' ')),
                )
            step.detail.endsWith("Permission was not granted; this step did not run.") ->
                Text(
                    R.string.run_permission_explanation,
                    TaskDisplayText.escape(step.detail.substringBefore(" Permission was not granted;")),
                )
            step.detail.contains("not installed", ignoreCase = true) ->
                Text(
                    R.string.run_app_missing,
                    TaskDisplayText.escape(step.targetLabel.ifBlank { appName(step.detail) }),
                )
            step.detail in setOf("a11y_node_not_found", "The requested screen element was not found.") ->
                Text(
                    R.string.run_target_missing,
                    TaskDisplayText.escape(step.targetLabel.ifBlank { "the planned button or field" }),
                )
            step.detail in
                setOf(
                    "a11y_action_rejected",
                    "Android did not accept the requested action.",
                )
            -> Text(R.string.run_action_rejected)
            step.state == StepProgressState.FAILED -> Text(R.string.run_failed_unknown)
            else -> null
        }
    }

    private fun fileFailureInstruction(reason: String): String =
        when (
            RunDiagnostics.failureKind(
                ai.eqo.core.agent.ExecuteResult
                    .Failure(reason),
            )
        ) {
            "no_eqo_screenshot_yet" ->
                "No recent EQO screenshot or matching gallery screenshot. Take one and try again."
            "no_matching_file" -> "No matching file. Check the name, type or date and try again."
            "needs_all_files_access" -> "Turn on All files access for EQO, then start the task again."
            else -> "This screen is protected. EQO cannot capture it."
        }

    private fun handoff(step: StepProgress): Text =
        if (step.detail == "control_not_found") {
            Text(R.string.run_control_not_found)
        } else if (!step.detail.contains("draft opened", ignoreCase = true)) {
            Text(R.string.run_user_action, TaskDisplayText.escape(step.detail))
        } else {
            when (step.name.lowercase()) {
                "compose_sms", "send_sms" -> Text(R.string.run_sms_draft)
                "compose_email", "send_email" -> Text(R.string.run_email_draft)
                "send_whatsapp" -> Text(R.string.run_chat_draft, "WhatsApp")
                "send_telegram" -> Text(R.string.run_chat_draft, "Telegram")
                else -> Text(R.string.run_user_action, TaskDisplayText.escape(step.detail))
            }
        }

    private fun appName(reason: String): String =
        Regex("App '([^']+)' not installed", RegexOption.IGNORE_CASE).find(reason)?.groupValues?.get(1)
            ?: Regex("^(.+?) (?:is )?not installed", RegexOption.IGNORE_CASE).find(reason)?.groupValues?.get(1)
            ?: "The requested app"

    fun permissionName(name: String): String =
        when (name.substringAfterLast('.')) {
            "READ_CONTACTS" -> "Contacts"
            "CALL_PHONE" -> "Phone"
            "SEND_SMS" -> "SMS"
            "RECORD_AUDIO" -> "Microphone"
            "CAMERA" -> "Camera"
            "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION" -> "Location"
            "READ_CALENDAR", "WRITE_CALENDAR" -> "Calendar"
            "POST_NOTIFICATIONS" -> "Notifications"
            "MANAGE_OVERLAY_PERMISSION" -> "Display over other apps"
            "ACTION_NOTIFICATION_LISTENER_SETTINGS" -> "Notification access"
            "MANAGE_APP_ALL_FILES_ACCESS_PERMISSION" -> "All files access"
            else -> name.substringAfterLast('.').replace('_', ' ').lowercase()
        }

    fun permissionInstruction(name: String): String =
        if (name == android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION) {
            "Turn on All files access for EQO, then return here."
        } else {
            "Tap Allow for ${permissionName(name)}."
        }

    fun planning(
        error: LLMError,
        timedOut: Boolean = false,
    ): Int =
        if (timedOut) {
            R.string.run_model_slow
        } else {
            when (error) {
                LLMError.AuthMissing -> R.string.task_key_needed
                LLMError.AuthInvalid -> R.string.model_error_auth
                LLMError.RateLimited -> R.string.model_error_rate
                LLMError.QuotaExhausted -> R.string.model_error_credit
                LLMError.ModelUnavailable -> R.string.model_error_model
                LLMError.Network -> R.string.task_call_failed
                else -> R.string.task_plan_invalid
            }
        }
}
