// Origin: EQO files-attachments, where TAKE_SCREENSHOT saves its pictures and how "last_screenshot" is recorded.
package ai.eqo.actions.impl

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Writes the current screen to a file. The Android version asks the accessibility service. */
internal fun interface ScreenshotWriter {
    suspend fun write(target: File): Boolean
}

/**
 * EQO's own screenshots go to Pictures/EQO when All files access is on, otherwise to EQO's private
 * folder on external storage, which needs no permission. Either way the newest one is recorded as
 * `last_screenshot`. Protected windows are refused before this is called.
 */
internal class ScreenshotCapture(
    private val layout: SharedStorageLayout,
    private val fallbackFolder: File,
    private val accessGranted: () -> Boolean,
    private val store: LastScreenshotStore,
    private val writer: ScreenshotWriter,
    /** Asked after the picture is written: false means the window became protected, so the picture is dropped. */
    private val stillAllowed: () -> Boolean = { true },
) {
    fun folder(): File {
        val shared = File(layout.root, SharedStorageLayout.SCREENSHOT_FOLDER)
        return if (accessGranted()) shared else fallbackFolder
    }

    /** Takes the picture and records it. Returns the saved file, or null when nothing was saved. */
    suspend fun capture(): File? {
        val dir = folder()
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val target = File(dir, "eqo-screenshot-${FORMAT.format(Instant.now())}.png")
        val saved = writer.write(target) && target.length() > 0L && stillAllowed()
        return if (saved) {
            target.also(store::record)
        } else {
            target.delete()
            null
        }
    }

    private companion object {
        val FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault())
    }
}
