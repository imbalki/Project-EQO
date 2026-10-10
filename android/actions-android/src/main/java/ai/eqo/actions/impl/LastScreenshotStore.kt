// Origin: EQO files-attachments, remembers the file that "last_screenshot" refers to.
package ai.eqo.actions.impl

import android.content.Context
import androidx.core.content.edit
import java.io.File

/** The most recent EQO capture. AttachmentShare applies its recency limit and disclosed gallery fallback. */
internal interface LastScreenshotStore {
    /** The recorded file, or null when none was recorded or it has been deleted since. */
    fun get(): File?

    fun record(file: File)
}

internal class PrefsLastScreenshotStore(
    context: Context,
) : LastScreenshotStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun get(): File? = prefs.getString(KEY, null)?.let(::File)?.takeIf { it.isFile }

    override fun record(file: File) {
        prefs.edit { putString(KEY, file.absolutePath) }
    }

    private companion object {
        const val PREFS = "eqo_last_screenshot"
        const val KEY = "path"
    }
}
