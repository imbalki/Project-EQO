// Origin: EQO files-attachments, read-only FIND_FILES and LIST_FILES over shared storage.
package ai.eqo.actions.impl

import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import android.content.Context

/**
 * LIST_FILES and FIND_FILES never change anything and never return file contents. A folder such as
 * "Downloads" or a full path is read from shared storage (needs All files access). A short relative path
 * like "out/docs" keeps the earlier behaviour of listing inside EQO's own workspace.
 */
internal class FileActions(
    private val browser: SharedFileBrowser,
) {
    fun getActions(): List<Action> = listOf(ListFilesAction(), FindFilesAction())

    private fun show(result: BrowseResult): ActionResult =
        when (result) {
            is BrowseResult.Listing -> ActionResult.Success(mapOf("message" to result.text))
            is BrowseResult.Refused -> ActionResult.Failure(result.message)
        }

    private inner class ListFilesAction : Action {
        override val name: String = "LIST_FILES"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val folder = (params["folder"] ?: params["path"]).orEmpty()
            return if (usesWorkspace(folder)) {
                StorageWorkspaceProvider.listFiles(context, folder)
            } else {
                show(browser.list(folder))
            }
        }
    }

    private inner class FindFilesAction : Action {
        override val name: String = "FIND_FILES"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            return show(browser.find(params["query"].orEmpty(), params["folder"]))
        }
    }

    private companion object {
        private val SHARED_NAMES =
            setOf(
                "download",
                "downloads",
                "document",
                "documents",
                "picture",
                "pictures",
                "photos",
                "dcim",
                "camera",
                "screenshots",
                "eqo",
                "movies",
                "music",
            )

        /** Blank or a plain relative path that is not a well-known shared folder stays in EQO's workspace. */
        fun usesWorkspace(folder: String): Boolean {
            val trimmed = folder.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("/")) return trimmed.isEmpty()
            return trimmed.substringBefore('/').lowercase() !in SHARED_NAMES
        }
    }
}
