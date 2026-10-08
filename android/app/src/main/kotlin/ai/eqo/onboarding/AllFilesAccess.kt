// Origin: EQO files-attachments, the guided "All files access" setup row.
package ai.eqo.onboarding

import ai.eqo.study.CapabilityState
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Android's "All files access" (MANAGE_EXTERNAL_STORAGE). Only the owner can switch it on, in Android's
 * own Settings page; EQO never grants it to itself. It lets EQO look through shared storage (FIND_FILES,
 * LIST_FILES) and read the files a task attaches. It is optional: without it EQO can still attach the
 * screenshots it took itself.
 */
object AllFilesAccess {
    /** Fails closed: if Android cannot answer, the row reads as not granted. */
    fun isGranted(check: () -> Boolean = { Environment.isExternalStorageManager() }): Boolean =
        try {
            check()
        } catch (_: RuntimeException) {
            false
        }

    /** The row shows ready when granted, otherwise not set up. It is never "failed": nothing is broken. */
    fun state(granted: Boolean): CapabilityState = if (granted) CapabilityState.READY else CapabilityState.NOT_STARTED

    /** Opens EQO's own entry on the All files access page. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The list of all apps, for phones that do not open the per-app page. */
    fun fallbackIntent(): Intent {
        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
