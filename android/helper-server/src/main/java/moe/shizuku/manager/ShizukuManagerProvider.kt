// Origin: RikkaApps/Shizuku @ b844bc491f1790c72328e1a8e5b2349f8978f0ea,
//   path: manager/src/main/java/moe/shizuku/manager/ShizukuManagerProvider.kt
// TASK-007 (issue #12) fork changes vs upstream, all deliberate for the S1 spike:
//   1. the binder extra key is renamed with the application id (D-004 lockstep rename);
//   2. upstream's moe.shizuku.manager.utils.Logger facade (manager module, not forked) is
//      replaced with android.util.Log calls carrying the same messages.
// Class and package names are kept per D-004.
package moe.shizuku.manager

import android.os.Bundle
import android.util.Log
import androidx.core.os.bundleOf
import moe.shizuku.api.BinderContainer
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants.USER_SERVICE_ARG_TOKEN
import rikka.shizuku.ShizukuProvider
import rikka.shizuku.server.ktx.workerHandler
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ShizukuManagerProvider : ShizukuProvider() {
    companion object {
        private const val TAG = "EqoHelperManagerProvider"

        private const val EXTRA_BINDER = "ai.eqo.app.helper.intent.extra.BINDER"
        private const val METHOD_SEND_USER_SERVICE = "sendUserService"
        private const val BINDER_RECEIVE_TIMEOUT_SECONDS = 5L
    }

    override fun onCreate(): Boolean {
        disableAutomaticSuiInitialization()
        return super.onCreate()
    }

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        if (method != METHOD_SEND_USER_SERVICE) {
            return super.call(method, arg, extras)
        }
        return extras?.let {
            // runCatching at the IPC boundary keeps the same Throwable coverage the upstream
            // catch (Throwable) had, without a too-generic catch clause.
            runCatching { sendUserService(it) }
                .onFailure { e -> Log.e(TAG, "sendUserService", e) }
                .getOrNull()
        }
    }

    private fun sendUserService(extras: Bundle): Bundle? {
        extras.classLoader = BinderContainer::class.java.classLoader

        val token = extras.getString(USER_SERVICE_ARG_TOKEN)
        val binder = extras.getParcelable<BinderContainer>(EXTRA_BINDER)?.binder
        if (token == null || binder == null) {
            return null
        }

        val countDownLatch = CountDownLatch(1)
        var reply: Bundle? = Bundle()

        val listener =
            object : Shizuku.OnBinderReceivedListener {
                override fun onBinderReceived() {
                    runCatching {
                        Shizuku.attachUserService(
                            binder,
                            bundleOf(
                                USER_SERVICE_ARG_TOKEN to token,
                            ),
                        )
                        reply!!.putParcelable(EXTRA_BINDER, BinderContainer(Shizuku.getBinder()))
                    }.onFailure { e ->
                        Log.e(TAG, "attachUserService $token", e)
                        reply = null
                    }

                    Shizuku.removeBinderReceivedListener(this)

                    countDownLatch.countDown()
                }
            }

        Shizuku.addBinderReceivedListenerSticky(listener, workerHandler)

        return try {
            countDownLatch.await(BINDER_RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            reply
        } catch (e: TimeoutException) {
            Log.e(TAG, "Binder not received in ${BINDER_RECEIVE_TIMEOUT_SECONDS}s", e)
            null
        }
    }
}
