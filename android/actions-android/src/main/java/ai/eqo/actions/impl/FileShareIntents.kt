// Origin: EQO files-attachments, ACTION_SEND / ACTION_SEND_MULTIPLE intents that carry file URIs to one app.
package ai.eqo.actions.impl

import android.content.ClipData
import android.content.Intent
import android.net.Uri

internal object FileShareIntents {
    /**
     * Builds the share for [files], aimed at [targetPackage] (WhatsApp, Gmail, the default messaging app)
     * and carrying a read grant for exactly these URIs. One file uses ACTION_SEND, several use
     * ACTION_SEND_MULTIPLE. The caller adds the app-specific extras (recipient, subject, text).
     */
    fun build(
        files: List<ShareFile>,
        targetPackage: String?,
    ): Intent {
        require(files.isNotEmpty()) { "No files to share" }
        val uris = files.map { it.uri }
        val intent =
            if (files.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE)
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
            }
        intent.type = commonType(files.map { it.mimeType })
        intent.clipData =
            ClipData.newRawUri("EQO attachment", uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (targetPackage != null) intent.setPackage(targetPackage)
        return intent
    }

    internal fun commonType(types: List<String>): String =
        when {
            types.distinct().size == 1 -> types.first()
            types.map { it.substringBefore('/') }.distinct().size == 1 -> types.first().substringBefore('/') + "/*"
            else -> "*/*"
        }
}
