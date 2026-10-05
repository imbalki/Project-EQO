package ai.eqo.task

import ai.eqo.core.agent.AliasResolver
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
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = pm.queryIntentActivities(query, 0)
        val alias = AliasResolver.appPackage(name)
        val candidates =
            matches
                .filter {
                    it.activityInfo.packageName == alias || it.loadLabel(pm).toString().equals(name, ignoreCase = true)
                }.map { it.activityInfo.packageName }
                .distinct()
        val intent = candidates.singleOrNull()?.let { pm.getLaunchIntentForPackage(it) } ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: android.content.ActivityNotFoundException) {
            false
        }
    }
}
