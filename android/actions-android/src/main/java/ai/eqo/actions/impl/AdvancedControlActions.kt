// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/actions/AdvancedControlActions.kt
package ai.eqo.actions.impl

import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.GenericAppAutomator
import ai.eqo.accessibility.UntrustedScreenText
import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.delay

internal class AdvancedControlActions {
    companion object {
        private const val MAX_WAIT_MS = 10_000L
    }

    fun getActions(): List<Action> =
        listOf(
            ListFilesAction(),
            ReadFileAction(),
            WriteFileAction(),
            DeleteFileAction(),
            CreateDirectoryAction(),
            CopyFileAction(),
            MoveFileAction(),
            ZipFilesAction(),
            UnzipFileAction(),
            ListInstalledAppsAction(),
            ClickTextAction(),
            ClickIdAction(),
            TypeTextAction(),
            TypeIdAction(),
            ScrollAction(),
            GetScreenTextAction(),
            ClickCoordinatesAction(),
            PressEnterAction(),
            WaitAction(),
        )

    private class ListFilesAction : Action {
        override val name: String = "LIST_FILES"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val pathStr = params["path"]
            return StorageWorkspaceProvider.listFiles(context, pathStr)
        }
    }

    private class ReadFileAction : Action {
        override val name: String = "READ_FILE"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val filePath = params["filePath"] ?: return ActionResult(false, null, "filePath parameter is missing")
            return StorageWorkspaceProvider.readFile(context, filePath)
        }
    }

    private class WriteFileAction : Action {
        override val name: String = "WRITE_FILE"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val filePath = params["filePath"] ?: return ActionResult(false, null, "filePath parameter is missing")
            val content = params["content"] ?: ""
            return StorageWorkspaceProvider.writeFile(context, filePath, content)
        }
    }

    private class DeleteFileAction : Action {
        override val name: String = "DELETE_FILE"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val filePath = params["filePath"] ?: return ActionResult(false, null, "filePath parameter is missing")
            return StorageWorkspaceProvider.deleteFile(context, filePath)
        }
    }

    private class CreateDirectoryAction : Action {
        override val name: String = "CREATE_DIRECTORY"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val pathStr = params["path"] ?: return ActionResult(false, null, "path parameter is missing")
            return StorageWorkspaceProvider.createDirectory(context, pathStr)
        }
    }

    private class CopyFileAction : Action {
        override val name: String = "COPY_FILE"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val srcPath = params["sourcePath"] ?: return ActionResult(false, null, "sourcePath parameter is missing")
            val destPath = params["destPath"] ?: return ActionResult(false, null, "destPath parameter is missing")
            return StorageWorkspaceProvider.copyFile(context, srcPath, destPath)
        }
    }

    private class MoveFileAction : Action {
        override val name: String = "MOVE_FILE"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val srcPath = params["sourcePath"] ?: return ActionResult(false, null, "sourcePath parameter is missing")
            val destPath = params["destPath"] ?: return ActionResult(false, null, "destPath parameter is missing")
            return StorageWorkspaceProvider.moveFile(context, srcPath, destPath)
        }
    }

    private class ZipFilesAction : Action {
        override val name: String = "ZIP_FILES"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val srcPath = params["sourcePath"] ?: return ActionResult(false, null, "sourcePath parameter is missing")
            val zipFilePath = params["zipFilePath"] ?: return ActionResult(false, null, "zipFilePath parameter is missing")
            return StorageWorkspaceProvider.zipFiles(context, srcPath, zipFilePath)
        }
    }

    private class UnzipFileAction : Action {
        override val name: String = "UNZIP_FILE"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val zipFilePath = params["zipFilePath"] ?: return ActionResult(false, null, "zipFilePath parameter is missing")
            val destDirPath = params["destDirPath"] ?: return ActionResult(false, null, "destDirPath parameter is missing")
            return StorageWorkspaceProvider.unzipFile(context, zipFilePath, destDirPath)
        }
    }

    private class ListInstalledAppsAction : Action {
        override val name: String = "LIST_INSTALLED_APPS"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult =
            requireRegistryExecution() ?: try {
                val pm = context.packageManager

                // Honest package-visibility limit: only packages Android exposes are
                // listed; no QUERY_ALL_PACKAGES permission is requested (TASK-069
                // evidence). Lint's blanket requirement is therefore not applicable.
                @SuppressLint("QueryPermissionsNeeded")
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val appList =
                    apps
                        .map { app ->
                            val label = pm.getApplicationLabel(app).toString()
                            val packageName = app.packageName
                            "$label ($packageName)"
                        }.sorted()
                        .joinToString("\n")
                ActionResult(true, appList, null)
            } catch (e: Exception) {
                ActionResult(false, null, "Couldn't list the apps right now.")
            }
    }

    private class ClickTextAction : Action {
        override val name: String = "CLICK_TEXT"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val text = params["text"] ?: return ActionResult(false, null, "text parameter is missing")
            val outcome = GenericAppAutomator.clickText(text)
            val success = outcome.isSuccess
            if (!success) return outcome.toActionResult()
            return ActionResult(success, if (success) "Tapped on '$text'!" else "Couldn't find '$text' to tap on.", null)
        }
    }

    private class ClickIdAction : Action {
        override val name: String = "CLICK_ID"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val viewId = params["viewId"] ?: return ActionResult(false, null, "viewId parameter is missing")
            val outcome = GenericAppAutomator.clickId(viewId)
            val success = outcome.isSuccess
            if (!success) return outcome.toActionResult()
            return ActionResult(success, if (success) "Tapped the element!" else "Couldn't find that element.", null)
        }
    }

    private class TypeTextAction : Action {
        override val name: String = "TYPE_TEXT"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val searchText = params["searchText"] ?: return ActionResult(false, null, "searchText parameter is missing")
            val content = params["content"] ?: ""
            val outcome = GenericAppAutomator.typeText(searchText, content)
            val success = outcome.isSuccess
            if (!success) return outcome.toActionResult()
            return ActionResult(success, if (success) "Typed it in!" else "Couldn't find that text field.", null)
        }
    }

    private class TypeIdAction : Action {
        override val name: String = "TYPE_ID"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val viewId = params["viewId"] ?: return ActionResult(false, null, "viewId parameter is missing")
            val content = params["content"] ?: ""
            val outcome = GenericAppAutomator.typeId(viewId, content)
            val success = outcome.isSuccess
            if (!success) return outcome.toActionResult()
            return ActionResult(success, if (success) "Typed it in!" else "Couldn't find that field.", null)
        }
    }

    private class ScrollAction : Action {
        override val name: String = "SCROLL"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val direction = params["direction"] ?: "forward"
            val forward = direction.lowercase() == "forward"
            val outcome = GenericAppAutomator.scroll(forward)
            val success = outcome.isSuccess
            if (!success) return outcome.toActionResult()
            return ActionResult(success, if (success) "Scrolled $direction!" else "Can't scroll here.", null)
        }
    }

    private class GetScreenTextAction : Action {
        override val name: String = "GET_SCREEN_TEXT"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val outcome = GenericAppAutomator.scrapeScreen()
            return when (outcome) {
                is A11yResult.Success ->
                    ActionResult(
                        true,
                        UntrustedScreenText.wrap(outcome.detail.ifEmpty { "No text visible on screen" }),
                        null,
                    )
                is A11yResult.Failure -> outcome.toActionResult()
            }
        }
    }

    private class ClickCoordinatesAction : Action {
        override val name: String = "CLICK_COORDINATES"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val x = params["x"]?.toFloatOrNull() ?: return ActionResult(false, null, "x coordinate is missing or invalid")
            val y = params["y"]?.toFloatOrNull() ?: return ActionResult(false, null, "y coordinate is missing or invalid")
            if (!x.isFinite() ||
                !y.isFinite() ||
                x < 0 ||
                y < 0
            ) {
                return ActionResult.Failure("Coordinates must be finite and non-negative.")
            }
            val outcome = GenericAppAutomator.clickCoordinates(x, y)
            val success = outcome.isSuccess
            if (!success) return outcome.toActionResult()
            return ActionResult(success, if (success) "Tapped there!" else "Couldn't tap at that spot.", null)
        }
    }

    private class PressEnterAction : Action {
        override val name: String = "PRESS_ENTER"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val outcome = GenericAppAutomator.pressEnter()
            val success = outcome.isSuccess
            if (!success) return outcome.toActionResult()
            return ActionResult(success, if (success) "Submitted!" else "Couldn't find a focused field to submit.", null)
        }
    }

    private class WaitAction : Action {
        override val name: String = "WAIT"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val requestedMs = params["durationMs"]?.toLongOrNull() ?: 2000L
            val clampedMs = requestedMs.coerceIn(0L, MAX_WAIT_MS)
            delay(clampedMs)
            return ActionResult(true, "Waited ${clampedMs}ms.", null)
        }
    }
}
