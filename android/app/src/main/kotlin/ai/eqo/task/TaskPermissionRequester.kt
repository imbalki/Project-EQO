// Origin: EQO TASK-069 (#20), task-screen Android permission dialog/settings adapter.
package ai.eqo.task

import ai.eqo.actions.impl.ActionPermission
import ai.eqo.actions.impl.PermissionRequester
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Owned by one TaskActivity. No grants are requested at setup/startup. */
internal class TaskPermissionRequester(
    private val activity: Activity,
) : PermissionRequester {
    private val mutex = Mutex()
    private var pending: CancellableContinuation<Boolean>? = null
    private var requested: ActionPermission? = null
    private var leftForSettings = false
    private var settingsLaunched = false
    private var instructions: AlertDialog? = null

    override suspend fun request(permission: ActionPermission): Boolean =
        withContext(Dispatchers.Main.immediate) {
            mutex.withLock {
                if (granted(permission)) return@withLock true
                if (activity.isFinishing || activity.isDestroyed) return@withLock false
                suspendCancellableCoroutine { continuation ->
                    pending = continuation
                    requested = permission
                    continuation.invokeOnCancellation {
                        activity.runOnUiThread { settle(false) }
                    }
                    when (permission) {
                        is ActionPermission.Runtime -> {
                            if (activity.shouldShowRequestPermissionRationale(permission.name)) {
                                showInstructions(permission.explanation) {
                                    activity.requestPermissions(arrayOf(permission.name), REQUEST_CODE)
                                }
                            } else {
                                activity.requestPermissions(arrayOf(permission.name), REQUEST_CODE)
                            }
                        }
                        is ActionPermission.SpecialAccess ->
                            showInstructions(permission.explanation) {
                                val intent = Intent(permission.settingsAction)
                                if (permission.packageScoped) intent.data = "package:${activity.packageName}".toUri()
                                try {
                                    settingsLaunched = true
                                    activity.startActivity(intent)
                                } catch (_: Exception) {
                                    settle(false)
                                }
                            }
                    }
                }
            }
        }

    fun onRequestPermissionsResult(requestCode: Int): Boolean {
        if (requestCode != REQUEST_CODE) return false
        settle(requested?.let(::granted) == true)
        return true
    }

    fun onPause() {
        if (settingsLaunched) leftForSettings = true
    }

    fun onResume() {
        if (leftForSettings) settle(requested?.let(::granted) == true)
    }

    fun close() = settle(false)

    private fun granted(permission: ActionPermission): Boolean =
        when (permission) {
            is ActionPermission.Runtime -> activity.checkSelfPermission(permission.name) == PackageManager.PERMISSION_GRANTED
            is ActionPermission.SpecialAccess -> permission.isGranted()
        }

    private fun showInstructions(
        message: String,
        proceed: () -> Unit,
    ) {
        val dialog =
            AlertDialog
                .Builder(activity)
                .setTitle("Android access needed")
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        dialog.setOnCancelListener { settle(false) }
        instructions = dialog
        dialog.show()
        // Direct button listeners, wired synchronously right after show: no AlertDialog
        // internal handler dispatch sits between the owner's tap and the outcome.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            runCatching { dialog.dismiss() }
            proceed()
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { settle(false) }
    }

    private fun settle(allowed: Boolean) {
        val continuation = pending
        pending = null
        requested = null
        leftForSettings = false
        settingsLaunched = false
        // Cleanup must never swallow the user's answer (and tests may settle from a
        // thread that did not create the dialog).
        runCatching { instructions?.dismiss() }
        instructions = null
        if (continuation?.isActive == true) continuation.resume(allowed)
    }

    companion object {
        const val REQUEST_CODE = 6901
    }
}
