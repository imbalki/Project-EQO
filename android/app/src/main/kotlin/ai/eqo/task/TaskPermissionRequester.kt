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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Owned by one TaskActivity. No grants are requested at setup/startup. */
@Suppress("TooManyFunctions") // One activity-owned permission request plus its lifecycle and cancellation callbacks.
internal class TaskPermissionRequester(
    private val activity: Activity,
    private val prepareDialog: (AlertDialog) -> Unit = {},
    private val onWaiting: (String?) -> Unit = {},
    private val canRequest: () -> Boolean = { true },
) : PermissionRequester {
    private val mutex = Mutex()

    @Volatile
    private var pending: CancellableContinuation<Boolean>? = null
    private var requested: ActionPermission? = null
    private var leftForSettings = false
    private var settingsLaunched = false
    private var instructions: AlertDialog? = null
    private var nextRequestCode = REQUEST_CODE
    private var activeRequestCode: Int? = null

    override suspend fun request(permission: ActionPermission): Boolean =
        withContext(Dispatchers.Main.immediate) {
            mutex.withLock {
                if (granted(permission)) return@withLock true
                if (!canRequest()) return@withLock false
                if (activity.isFinishing || activity.isDestroyed) return@withLock false
                withTimeoutOrNull(PERMISSION_WAIT_TIMEOUT_MS) {
                    suspendCancellableCoroutine { continuation ->
                        pending = continuation
                        requested = permission
                        onWaiting(
                            when (permission) {
                                is ActionPermission.Runtime -> permission.name
                                is ActionPermission.SpecialAccess -> permission.settingsAction
                            },
                        )
                        continuation.invokeOnCancellation {
                            activity.runOnUiThread { if (pending === continuation) settle(false) }
                        }
                        when (permission) {
                            is ActionPermission.Runtime -> {
                                showInstructions(permission.explanation, "Allow now") {
                                    launchRuntime(permission)
                                }
                            }
                            is ActionPermission.SpecialAccess ->
                                showInstructions(permission.explanation) {
                                    val intent = Intent(permission.settingsAction)
                                    if (permission.packageScoped) {
                                        intent.data = "package:${activity.packageName}".toUri()
                                    }
                                    try {
                                        settingsLaunched = true
                                        activity.startActivity(intent)
                                    } catch (_: Exception) {
                                        settle(false)
                                    }
                                }
                        }
                    }
                } ?: false
            }
        }

    fun onRequestPermissionsResult(requestCode: Int): Boolean {
        if (requestCode != activeRequestCode) return false
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

    override fun isWaiting(): Boolean = pending != null

    override fun cancelWaiting() {
        val current = pending
        activity.runOnUiThread { if (pending === current) settle(false) }
    }

    private fun launchRuntime(permission: ActionPermission.Runtime) {
        try {
            val code = nextRequestCode++
            activeRequestCode = code
            val names =
                if (permission.name == android.Manifest.permission.ACCESS_FINE_LOCATION &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                ) {
                    arrayOf(permission.name, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                } else {
                    arrayOf(permission.name)
                }
            activity.requestPermissions(names, code)
        } catch (_: RuntimeException) {
            settle(false)
        }
    }

    private fun granted(permission: ActionPermission): Boolean =
        when (permission) {
            is ActionPermission.Runtime -> activity.checkSelfPermission(permission.name) == PackageManager.PERMISSION_GRANTED
            is ActionPermission.SpecialAccess -> permission.isGranted()
        }

    private fun showInstructions(
        message: String,
        positiveLabel: String = activity.getString(android.R.string.ok),
        proceed: () -> Unit,
    ) {
        val owner = pending
        val dialog =
            AlertDialog
                .Builder(activity)
                .setTitle("Android access needed")
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton(positiveLabel, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        dialog.setOnCancelListener { if (pending === owner) settle(false) }
        instructions = dialog
        dialog.show()
        prepareDialog(dialog)
        // Direct button listeners, wired synchronously right after show: no AlertDialog
        // internal handler dispatch sits between the owner's tap and the outcome.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (pending === owner && owner?.isActive == true && canRequest()) {
                runCatching { dialog.dismiss() }
                proceed()
            } else if (pending === owner) {
                settle(false)
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { if (pending === owner) settle(false) }
    }

    private fun settle(allowed: Boolean) {
        val continuation = pending
        pending = null
        requested = null
        activeRequestCode = null

        onWaiting(null)
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
        private const val PERMISSION_WAIT_TIMEOUT_MS = 120_000L
    }
}
