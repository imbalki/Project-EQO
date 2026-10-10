// Origin: EQO screenshot resolver repair, allowlisted redacted attachment diagnoses.
package ai.eqo.core.agent

/** Only these fixed diagnoses cross the run/log boundary; never interpolate a filename, URI or exception. */
object AttachmentFailure {
    private val messages =
        mapOf(
            "attachment_invalid" to "The attachment reference is invalid. Nothing was sent.",
            "no_matching_file" to "No matching file was found. Choose a file from the phone picker.",
            "folder_not_readable" to "The search folder could not be read. Choose a file from the phone picker.",
            "search_incomplete" to "The search could not finish safely. Choose a file from the phone picker.",
            "too_many_matches" to "Too many files match. Choose a file from the phone picker.",
            "attachment_selection_required" to "Needs you: return to EQO and choose the file. Nothing was sent.",
            "attachment_cancelled" to "Needs you: file selection or confirmation was cancelled. Nothing was sent.",
            "attachment_unavailable" to "The selected file is no longer available. Nothing was sent.",
            "attachment_not_allowed" to "That file cannot be shared safely. Nothing was sent.",
            "needs_all_files_access" to "EQO needs All files access to search shared storage. Choose a file instead.",
            "attachment_too_large" to "The attachments exceed the 25 MB limit. Nothing was sent.",
            "staging_failed" to "EQO could not copy the attachment. Nothing was sent.",
            "provider_failed" to "The file provider could not prepare the attachment. Nothing was sent.",
            "send_requires_user" to
                "The attachment draft is open, but EQO could not press Send. Finish manually; sending is not verified.",
            "send_route_failed" to "The destination app could not open the attachment. Nothing was sent.",
        )

    fun reason(kind: String): String = "$kind: ${messages.getValue(kind)}"

    fun kind(reason: String): String? {
        val kind = reason.substringBefore(": ")
        return kind.takeIf { it in messages && reason == it + ": " + messages[it] }
    }

    fun message(reason: String): String? = kind(reason)?.let(messages::get)
}
