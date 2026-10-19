// Origin: EQO foreground-only recipient recovery, before immutable plan approval.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.actions.impl.RecipientPreparationException
import ai.eqo.core.agent.Contact
import ai.eqo.core.agent.ContactResolution
import ai.eqo.core.agent.ContactResolver
import ai.eqo.core.agent.TaskIntent
import ai.eqo.core.agent.isLiteralEmail
import ai.eqo.core.agent.isLiteralPhone
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TaskRecipientRecovery(
    private val context: Context,
    private val ask: suspend (TaskIntent) -> String?,
    private val memory: RememberedRecipients = RememberedRecipients(context),
    private val contacts: ContactResolver = ContactResolver(context),
) : ContactResolver(context) {
    override suspend fun resolveWithDisambiguation(input: String): ContactResolution = resolve(input, email = false)

    override suspend fun resolveEmailWithDisambiguation(input: String): ContactResolution = resolve(input, email = true)

    private suspend fun lookup(
        input: String,
        email: Boolean,
    ): ContactResolution =
        if (email) {
            contacts.resolveEmailWithDisambiguation(input)
        } else {
            contacts.resolveWithDisambiguation(input)
        }

    private suspend fun resolve(
        input: String,
        email: Boolean,
    ): ContactResolution {
        val remembered = memory.destination(input, email)
        val result = lookup(remembered ?: input, email)
        return if (result is ContactResolution.Found) {
            if (remembered == null) result else ContactResolution.Found(result.contact.copy(name = input))
        } else {
            withContext(Dispatchers.Main) { recover(input, email, result) }
        }
    }

    private suspend fun question(
        title: String,
        options: List<String> = emptyList(),
    ): String? = ask(TaskIntent(goal = "Resolve recipient before approval", question = title, options = options))

    private suspend fun recover(
        input: String,
        email: Boolean,
        result: ContactResolution,
    ): ContactResolution {
        val type = context.getString(if (email) R.string.recipient_type_email else R.string.recipient_type_phone)
        val pick = context.getString(R.string.recipient_pick_contact)
        val title = context.getString(R.string.recipient_missing, type, input)
        val answer = question(title, listOf(context.getString(R.string.recipient_type_now), pick)) ?: cancelled()
        val destination =
            when {
                valid(answer, email) -> answer
                answer == pick -> pickContact(email, result)
                else -> typeDestination(type, email)
            } ?: cancelled()
        val always = context.getString(R.string.app_chooser_always)
        val once = context.getString(R.string.app_chooser_this_time)
        val scope = question(context.getString(R.string.recipient_remember, input, destination), listOf(always, once))
        if (scope !in listOf(always, once)) cancelled()
        if (scope == always) memory.remember(input, email, destination)
        return ContactResolution.Found(Contact(input, destination, source = "owner_choice"))
    }

    private suspend fun typeDestination(
        type: String,
        email: Boolean,
    ): String? {
        repeat(MAX_ATTEMPTS) {
            val value = question(context.getString(R.string.recipient_enter, type)) ?: return null
            if (valid(value, email)) return value
        }
        // Never guess a destination, even when the general planning question budget is exhausted.
        throw RecipientPreparationException(context.getString(R.string.recipient_invalid))
    }

    private suspend fun pickContact(
        email: Boolean,
        original: ContactResolution,
    ): String? {
        val result =
            if (original is ContactResolution.Ambiguous) original else withContext(Dispatchers.IO) { lookup("", email) }
        val matches =
            when (result) {
                is ContactResolution.Ambiguous -> result.matches
                is ContactResolution.Found -> listOf(result.contact)
                else -> emptyList()
            }.filter { valid(it.phoneNumber, email) }
        val rows = matches.map { "${it.name}: ${it.phoneNumber}" }
        val answer = question(context.getString(R.string.recipient_pick_contact), rows) ?: return null
        return matches.getOrNull(rows.indexOf(answer))?.phoneNumber
    }

    private fun valid(
        value: String,
        email: Boolean,
    ): Boolean = if (email) isLiteralEmail(value) else isLiteralPhone(value)

    private fun cancelled(): Nothing {
        val message = context.getString(R.string.recipient_cancelled)
        throw RecipientPreparationException(message)
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}
