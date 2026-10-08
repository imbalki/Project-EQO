// Origin: EQO files-attachments, short-lived copies that other apps may read, and their cleanup.
package ai.eqo.actions.impl

import java.io.File
import java.util.UUID

/**
 * Attachments are copied into one folder that EQO's non-exported file provider serves, so the receiving
 * app never gets a handle on the owner's real folders. Each share lives in its own sub-folder and is
 * removed after [DEFAULT_MAX_AGE_MS], when sending fails, or when [discard] is called.
 */
internal class ShareStaging(
    val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun stage(source: File): File {
        val dir = File(root, UUID.randomUUID().toString())
        check(dir.mkdirs()) { "Could not create the share folder" }
        val target = File(dir, safeName(source.name))
        source.copyTo(target)
        dir.setLastModified(clock())
        return target
    }

    /** Deletes the share folders of these staged files. */
    fun discard(staged: List<File>) {
        staged
            .mapNotNull { it.parentFile }
            .filter { it.parentFile?.path == root.path }
            .distinct()
            .forEach { it.deleteRecursively() }
    }

    /** Deletes share folders older than [maxAgeMs]; returns how many were removed. */
    fun sweep(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Int {
        val cutoff = clock() - maxAgeMs
        val stale = root.listFiles().orEmpty().filter { it.lastModified() <= cutoff }
        stale.forEach { it.deleteRecursively() }
        return stale.size
    }

    companion object {
        const val DEFAULT_MAX_AGE_MS = 60 * 60 * 1000L
        private const val MAX_NAME = 100
        private const val MAX_EXTENSION = 10
        private val unsafe = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")

        fun safeName(name: String): String {
            val cleaned = name.replace(unsafe, "_").trim().trim('.')
            return when {
                cleaned.isEmpty() -> "file"
                cleaned.length <= MAX_NAME -> cleaned
                else -> shorten(cleaned)
            }
        }

        private fun shorten(name: String): String {
            val extension = name.substringAfterLast('.', "").take(MAX_EXTENSION)
            if (extension.isEmpty()) return name.take(MAX_NAME)
            return name.substringBeforeLast('.').take(MAX_NAME - extension.length - 1) + "." + extension
        }
    }
}
