// Origin: EQO files-attachments, builds the file, attachment and screenshot collaborators for the registry.
package ai.eqo.actions.impl

import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.EqoAutomation
import android.content.Context
import android.os.Environment
import java.io.File

internal class SharedStorageServices(
    val browser: SharedFileBrowser,
    val attachments: AttachmentShare,
    val screenshots: ScreenshotCapture,
) {
    companion object {
        /** Fails closed: if Android cannot answer, All files access counts as not granted. */
        private val platformAccess: () -> Boolean = {
            try {
                Environment.isExternalStorageManager()
            } catch (_: RuntimeException) {
                false
            }
        }

        private val serviceWriter =
            ScreenshotWriter { target -> EQOAccessibilityService.getInstance()?.takeScreenshotToFile(target) == true }

        fun create(
            context: Context,
            automation: () -> EqoAutomation?,
            options: RegistryOptions,
        ): SharedStorageServices {
            val accessGranted = options.allFilesAccess ?: platformAccess
            val ownFiles = context.getExternalFilesDir(null)
            val stagingRoot = EqoSharedFileProvider.stagingRoot(context)
            val root = options.storageRoot ?: Environment.getExternalStorageDirectory()
            val layout = SharedStorageLayout(root, listOfNotNull(ownFiles), listOf(stagingRoot))
            val lastScreenshot = options.lastScreenshotStore ?: PrefsLastScreenshotStore(context)
            val writer = options.screenshotWriter ?: serviceWriter
            val pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            val fallbackFolder = File(pictures, "EQO")
            return SharedStorageServices(
                browser = SharedFileBrowser(layout, accessGranted),
                attachments =
                    AttachmentShare(layout, lastScreenshot, ShareStaging(stagingRoot), accessGranted) {
                        options.shareUri?.invoke(it) ?: EqoSharedFileProvider.uriFor(context, it)
                    },
                screenshots =
                    ScreenshotCapture(
                        layout,
                        fallbackFolder,
                        accessGranted,
                        lastScreenshot,
                        writer,
                        stillAllowed = { automation()?.screenshotGate()?.isSuccess == true },
                    ),
            )
        }
    }
}
