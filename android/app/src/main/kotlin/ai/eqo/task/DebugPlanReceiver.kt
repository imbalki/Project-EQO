// EQO debug-only test seam: hands the app a finished plan so executors can be tested without a model key.
package ai.eqo.task

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.util.Base64

/**
 * Debuggable builds only (never active in a release build). A sender holding the DUMP permission (the adb
 * shell) supplies a base64 plan; it still goes through the same plan parser/validator and the same on-screen
 * "Review the whole plan" approval as a model-made plan. It cannot approve or run anything by itself.
 */
internal class DebugPlanReceiver(
    private val onPlan: (String) -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val encoded = intent.getStringExtra(EXTRA_PLAN_B64) ?: return
        val json = runCatching { String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull() ?: return
        onPlan(json)
    }

    companion object {
        const val ACTION = "ai.eqo.debug.PLAN"
        const val EXTRA_PLAN_B64 = "plan_b64"
        private const val SENDER_PERMISSION = "android.permission.DUMP"

        fun register(
            context: Context,
            handler: Handler,
            receiver: DebugPlanReceiver,
        ): Boolean {
            val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            if (!debuggable) return false
            val filter = IntentFilter(ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, SENDER_PERMISSION, handler, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag") // pre-33 has no flag parameter
                context.registerReceiver(receiver, filter, SENDER_PERMISSION, handler)
            }
            return true
        }
    }
}
