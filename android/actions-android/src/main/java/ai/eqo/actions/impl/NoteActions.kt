// Origin: EQO note saving through the notes app's own share-to-note screen; nothing is typed into its editor.
package ai.eqo.actions.impl

import ai.eqo.accessibility.GenericAppAutomator
import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.delay

/**
 * Keep ignores text that an accessibility service sets inside its editor (the note is saved empty), but its
 * share screen saves reliably: the text is handed over as a normal share and one tap on Save finishes it.
 */
internal class NoteActions(
    private val launcher: GatedIntentLauncher,
) {
    fun getActions(): List<Action> = listOf(CreateNoteAction())

    private inner class CreateNoteAction : Action {
        override val name: String = "CREATE_NOTE"

        @Suppress("ReturnCount") // One fixed refusal per missing prerequisite.
        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val title = params["title"].orEmpty().trim()
            val body = params["body"].orEmpty().trim().ifBlank { title }
            if (body.isBlank()) return ActionResult.Failure("There is nothing to put in the note.")
            val resolver = LaunchableAppResolver(context.packageManager)
            val target =
                params["packageName"]?.let(resolver::resolvePackage)
                    ?: resolver.resolve(params["appName"]?.ifBlank { null } ?: DEFAULT_APP)
            val notesPackage =
                target?.component?.packageName ?: target?.`package`
                    ?: return ActionResult.Failure("The notes app is not installed or cannot be opened.")
            val share =
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .setPackage(notesPackage)
                    .putExtra(Intent.EXTRA_TEXT, body)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (title.isNotBlank() && title != body) share.putExtra(Intent.EXTRA_SUBJECT, title)
            launcher.open(share)
            delay(SHARE_SCREEN_SETTLE_MS)
            val saved = GenericAppAutomator.clickText(SAVE_LABEL)
            if (!saved.isSuccess) return saved.toControlResult()
            delay(SAVE_SETTLE_MS)
            return ActionResult.Success(mapOf("message" to "Note saved."))
        }
    }

    private companion object {
        const val DEFAULT_APP = "Keep"
        const val SAVE_LABEL = "Save"
        const val SHARE_SCREEN_SETTLE_MS = 1500L
        const val SAVE_SETTLE_MS = 800L
    }
}
