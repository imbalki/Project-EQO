package ai.eqo.task

import ai.eqo.actions.impl.LaunchableAppResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/** ACTION_SENDTO limits Android's resolver to mail handlers, never generic share targets. */
class EmailDraftOpener(
    private val startActivity: (Intent) -> Unit,
) {
    fun open(
        recipient: String,
        subject: String,
        body: String,
    ): Boolean {
        val uri = "mailto:${Uri.encode(recipient, "@")}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
        val intent =
            Intent(Intent.ACTION_SENDTO, uri.toUri()).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return try {
            startActivity(intent)
            true
        } catch (_: android.content.ActivityNotFoundException) {
            false
        }
    }
}

class StudyAppLauncher(
    private val context: Context,
) {
    fun open(name: String): Boolean {
        val resolver = LaunchableAppResolver(context.packageManager)
        val intent =
            (if (name.contains('.')) resolver.resolvePackage(name) else resolver.resolve(name)) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: android.content.ActivityNotFoundException) {
            false
        }
    }
}
