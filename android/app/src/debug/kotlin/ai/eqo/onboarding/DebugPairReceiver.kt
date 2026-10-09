package ai.eqo.onboarding

import ai.eqo.BuildConfig
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Lab seam compiled only into debug. The manifest requires the shell's DUMP permission. */
class DebugPairReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        try {
            if (!BuildConfig.DEBUG || intent.action != ACTION) return
            val code = intent.getStringExtra("code") ?: return
            val pairingPort = intent.getIntExtra("pairing_port", 0)
            val connectionPort = intent.getIntExtra("connection_port", 0)
            val raw = "$code $pairingPort $connectionPort"
            if (WirelessPairingReply.parse(raw)?.request(null, null) == null) return
            context.startForegroundService(
                Intent(context, WirelessPairingService::class.java)
                    .setAction(WirelessPairingService.SUBMIT)
                    .putExtra(WirelessPairingService.CODE, raw),
            )
        } finally {
            intent.removeExtra("code")
            intent.removeExtra("pairing_port")
            intent.removeExtra("connection_port")
        }
    }

    companion object {
        const val ACTION = "ai.eqo.debug.PAIR"
    }
}
