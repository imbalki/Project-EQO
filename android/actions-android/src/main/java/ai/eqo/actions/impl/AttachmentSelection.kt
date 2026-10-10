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
}
