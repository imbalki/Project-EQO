// Origin: EQO Files v2, local-only chooser and pre-send disclosure boundary.
package ai.eqo.actions.impl

/** Display metadata only: no path or URI crosses into the chooser. Never persist or log this data. */
data class AttachmentChoice(
    val name: String,
    val modifiedMillis: Long,
    val bytes: Long,
)

interface AttachmentSelection {
    /** Null means cancelled or no foreground UI. The returned index must belong to this offered list. */
    suspend fun choose(
        search: String,
        files: List<AttachmentChoice>,
    ): Int?

    /** Returns only after the selected exact names were shown, before any compose/send is attempted. */
    suspend fun showResolved(files: List<AttachmentChoice>): Boolean

    suspend fun confirm(files: List<AttachmentChoice>): AttachmentDecision =
        if (showResolved(files)) AttachmentDecision.SEND else AttachmentDecision.CANCEL

    /** One content document, never a folder grant. Caller closes it immediately after staging. */
    suspend fun pick(initialFolder: String): PickedAttachment? = null
}

enum class AttachmentDecision { SEND, CANCEL, DIFFERENT }

/** Process-only provider boundary. Do not persist or log its display metadata. */
class PickedAttachment(
    val choice: AttachmentChoice,
    val mime: String,
    val open: () -> java.io.InputStream,
    private val release: () -> Unit,
) : java.io.Closeable {
    override fun close() = release()

    override fun toString(): String = "PickedAttachment(redacted)"
}
