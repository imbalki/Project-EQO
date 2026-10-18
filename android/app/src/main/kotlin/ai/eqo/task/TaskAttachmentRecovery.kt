// Origin: EQO pre-approval attachment recovery; no Keep access or note extraction.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.actions.impl.RecipientPreparationException
import ai.eqo.core.agent.AttachmentSpec
import ai.eqo.core.agent.IntentPlanningSession
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.TaskIntent
import android.content.Context

internal class TaskAttachmentRecovery(
    private val context: Context,
    private val ask: suspend (TaskIntent) -> String?,
) {
    suspend fun prepare(
        steps: List<LoopStep>,
        missing: Set<String>,
        request: String,
    ): List<LoopStep> =
        steps.map { step ->
            val references = AttachmentSpec.parse(step.action.params[AttachmentSpec.PARAM])
            // Availability wins over names: an exported Keep note is an ordinary file.
            val requestedKeep =
                step.action.name == "SEND_EMAIL" &&
                    references.all { it == AttachmentSpec.PICKER || it in missing } &&
                    KEEP_ATTACHMENT.containsMatchIn(QUOTED.replace(request, ""))
            val unresolved = references.filter { it in missing }
            val keep = unresolved.any { KEEP.matches(it.removePrefix("find:")) } || requestedKeep
            if ((!keep && unresolved.isEmpty()) || step.action.name !in AttachmentSpec.ACTIONS) {
                step
            } else {
                val remaining = references - unresolved.toSet()
                val retained = if (requestedKeep) remaining - AttachmentSpec.PICKER else remaining
                recover(step, keep, retained)
            }
        }

    private suspend fun recover(
        step: LoopStep,
        keep: Boolean,
        retained: List<String>,
    ): LoopStep {
        val file = context.getString(R.string.attachment_choose_file)
        val body = context.getString(R.string.attachment_add_text)
        val title = context.getString(if (keep) R.string.attachment_keep_help else R.string.attachment_missing_help)
        val email = step.action.name == "SEND_EMAIL"
        val options = if (email) listOf(file, body) else listOf(file)
        val answer = ask(TaskIntent(goal = title, question = title, options = options))
        val params =
            when (answer) {
                file -> {
                    val references = (retained + AttachmentSpec.PICKER).distinct().joinToString("|")
                    step.action.params + (AttachmentSpec.PARAM to references)
                }
                body -> {
                    val question = context.getString(R.string.attachment_paste_text)
                    val text =
                        ask(TaskIntent(goal = question, question = question))?.takeIf { it.isNotBlank() }
                            ?: cancelled()
                    val previous = step.action.params["body"].orEmpty()
                    val attachments =
                        if (retained.isEmpty()) {
                            emptyMap()
                        } else {
                            mapOf(AttachmentSpec.PARAM to retained.joinToString("|"))
                        }
                    (step.action.params - AttachmentSpec.PARAM) + attachments +
                        ("body" to listOf(previous, text).filter { it.isNotBlank() }.joinToString("\n\n"))
                }
                else -> cancelled()
            }
        return step.copy(action = step.action.copy(params = params))
    }

    private fun cancelled(): Nothing {
        val message = context.getString(R.string.recipient_cancelled)
        throw RecipientPreparationException(message)
    }

    companion object {
        // After clarification, only the final attachment slot may infer an omitted attachment.
        // Goals can paraphrase message content, and the original request may have been superseded.
        fun requestFor(
            request: String,
            understood: IntentPlanningSession.Result,
        ): String =
            if (understood.clarificationRounds == 0 && !request.contains("\nUser clarification")) {
                request
            } else {
                val attachment =
                    understood.intent.params[AttachmentSpec.PARAM]
                        .orEmpty()
                        .removePrefix("find:")
                if (KEEP.matches(attachment)) "attach $attachment" else ""
            }

        private val QUOTED = Regex("\"(?:\\\\.|[^\"\\\\])*\"|“[^”]*”|'[^'\\n]*'")
        private val KEEP = Regex("\\b(?:google\\s+)?keep\\s+notes?\\b", RegexOption.IGNORE_CASE)
        private val KEEP_ATTACHMENT =
            Regex("\\battach\\w*\\s+(?:(?:my|the|a|an)\\s+)?(?:google\\s+)?keep\\s+notes?\\b", RegexOption.IGNORE_CASE)
    }
}
