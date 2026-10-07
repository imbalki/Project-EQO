package ai.eqo.onboarding

import ai.eqo.R
import ai.eqo.task.protectConfirmationDialog
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/** The server asks over the attached Binder; only this foreground setup screen renders consent. */
internal class HelperPermissionPrompt(
    private val activity: Activity,
    private val transport: ExecutorService = consentTransport,
) : HelperPermissionPort {
    private val main = Handler(Looper.getMainLooper())
    private val code = codes.incrementAndGet()
    private var dialog: AlertDialog? = null

    @Volatile
    private var closed = false

    @Volatile
    private var resultListener: Shizuku.OnRequestPermissionResultListener? = null
    private var resultCallback: ((Boolean) -> Unit)? = null
    private var cleanup: Future<*>? = null

    override fun isGranted(): Boolean = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    @Synchronized
    override fun request(result: (Boolean) -> Unit) {
        if (closed) {
            result(false)
            return
        }
        resultCallback = result
        val listener =
            Shizuku.OnRequestPermissionResultListener { requestCode, grant ->
                if (!closed && requestCode == code) result(grant == PackageManager.PERMISSION_GRANTED)
            }
        resultListener = listener
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.setPermissionConfirmationListener { requestCode ->
            if (requestCode == code && !closed) show()
        }
        // Synchronous Binder preserves the authenticated PID; never block the UI thread.
        transport.execute {
            runCatching { Shizuku.requestPermission(code) }.onFailure { result(false) }
        }
    }

    private fun show() {
        if (activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus()) {
            reply(false)
            return
        }
        if (dialog != null) return
        val confirmation =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.helper_permission_title)
                .setPositiveButton(R.string.helper_permission_allow) { _, _ -> reply(true) }
                .setNegativeButton(R.string.helper_permission_deny) { _, _ -> reply(false) }
                .setOnCancelListener { reply(false) }
                .create()
        dialog = confirmation
        confirmation.show()
        protectConfirmationDialog(confirmation)
    }

    @Synchronized
    private fun reply(allowed: Boolean) {
        if (!closed) {
            transport.execute {
                runCatching { Shizuku.confirmPermission(code, allowed) }.onFailure { resultCallback?.invoke(false) }
            }
        }
    }

    override fun close(authorized: Boolean) {
        val pendingCleanup =
            synchronized(this) {
                if (!closed) {
                    closed = true
                    resultListener?.let { Shizuku.removeRequestPermissionResultListener(it) }
                    Shizuku.setPermissionConfirmationListener(null)
                    // Queue order covers even a request not yet delivered; no oneway PID assumption.
                    cleanup = transport.submit { if (!authorized) Shizuku.revokeOwnPermission() }
                    if (!authorized) resultCallback?.invoke(false)
                    main.post {
                        dialog?.dismiss()
                        dialog = null
                    }
                }
                cleanup
            }
        // Lifecycle close must not block UI. The authorization worker awaits acknowledgement,
        // even when lifecycle close already queued cleanup. Interruption cannot skip revocation.
        if (Looper.myLooper() != Looper.getMainLooper()) awaitCleanup(pendingCleanup)
    }

    private fun awaitCleanup(pending: Future<*>?) {
        var interrupted = false
        try {
            while (pending != null) {
                try {
                    pending.get()
                    return
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }

    private companion object {
        // Shared queue also orders cancellation before an immediate retry from a new prompt.
        val consentTransport: ExecutorService = Executors.newSingleThreadExecutor()
        val codes = AtomicInteger(10_000)
    }
}
