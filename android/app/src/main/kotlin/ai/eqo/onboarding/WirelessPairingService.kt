package ai.eqo.onboarding

import ai.eqo.R
import ai.eqo.adb.pairing.HelperHooks
import ai.eqo.adb.pairing.WirelessAdbActivationRunner
import ai.eqo.study.StudyFlowGate
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.UUID
import java.util.concurrent.Executors

/** Notification reply performs only pairing + pinned connect. AUTHORIZE stays in the activity. */
class WirelessPairingService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var discovery: WirelessAdbDiscovery? = null
    private var token = UUID.randomUUID().toString()
    private val expiry = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.wireless_notification_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        startForeground(NOTIFICATION, notification())
        if (!StudyFlowGate.permits(StudyFlowGate.StudyTransport.WIRELESS_CONNECT_PLANE)) {
            stopSelf()
            return
        }
        WirelessPairingSession.message = null
        discovery = WirelessAdbDiscovery(this, WirelessPairingSession.state, ::changed).also { it.start() }
        main.postDelayed(expiry, SESSION_MS)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            STOP -> if (!WirelessPairingSession.busy) stopSelf()
            REPLY -> acceptReply(intent)
        }
        return START_NOT_STICKY
    }

    private fun acceptReply(intent: Intent) {
        val raw = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(CODE)
        val code = notificationPairingCode(raw)
        // Consume the framework reply container; never persist or forward the code to an activity.
        intent.clipData = null
        if (intent.getStringExtra(TOKEN) != token || WirelessPairingSession.busy) return
        if (!StudyFlowGate.permits(StudyFlowGate.StudyTransport.WIRELESS_CONNECT_PLANE)) return
        val state = WirelessPairingSession.state
        val endpoints = state.endpoints()
        if (code == null || endpoints == null) {
            WirelessPairingSession.message = getString(R.string.wireless_reply_invalid)
            changed()
            return
        }
        val network = state.networkId
        WirelessPairingSession.busy = true
        token = UUID.randomUUID().toString() // a second delivery of this PendingIntent cannot pair again
        WirelessPairingSession.message = getString(R.string.wireless_adb_working)
        changed()
        worker.execute {
            val success =
                runCatching {
                    val runner = WirelessAdbActivationRunner(StudySetup.keyStore(applicationContext), NoHelperConsent)
                    runner.pair(endpoints, code)
                    check(state.networkId == network) { "Wi-Fi changed" }
                    runner.connect(endpoints) // same runner consumes the enrollment; no TOFU reconnect
                    check(state.networkId == network) { "Wi-Fi changed" }
                }.isSuccess
            main.post {
                WirelessPairingSession.busy = false
                WirelessPairingSession.message =
                    getString(if (success) R.string.wireless_reply_paired else R.string.wireless_reply_failed)
                changed()
            }
        }
    }

    private fun changed() {
        WirelessPairingSession.changed()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
    }

    private fun notification(): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, WirelessAdbSetupActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, WirelessPairingService::class.java).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val ready = WirelessPairingSession.state.endpoints() != null && !WirelessPairingSession.busy
        val builder =
            Notification
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(if (ready) R.string.wireless_notification_ready else R.string.wireless_notification_title))
                .setContentText(WirelessPairingSession.message ?: getString(R.string.wireless_notification_waiting))
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(null, getString(R.string.wireless_discovery_stop), stop).build())
        if (ready) {
            val reply =
                PendingIntent.getService(
                    this,
                    2,
                    Intent(this, WirelessPairingService::class.java).setAction(REPLY).putExtra(TOKEN, token),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val action =
                Notification.Action
                    .Builder(null, getString(R.string.wireless_notification_reply), reply)
                    .addRemoteInput(RemoteInput.Builder(CODE).setLabel(getString(R.string.wireless_adb_pairing_code_hint)).build())
                    .setAllowGeneratedReplies(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) action.setAuthenticationRequired(true)
            builder.addAction(action.build())
        }
        return builder.build()
    }

    override fun onDestroy() {
        main.removeCallbacks(expiry)
        discovery?.close()
        worker.shutdownNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private object NoHelperConsent : HelperHooks {
        override fun startHelper(): Unit = error("Return to EQO")

        override fun authorizeHelper(): Unit = error("Human consent required")

        override fun checkBinder(): Unit = error("Return to EQO")
    }

    companion object {
        private const val CHANNEL = "eqo_wireless_pairing"
        private const val NOTIFICATION = 80
        private const val SESSION_MS = 300_000L
        private const val TOKEN = "session"
        private const val CODE = "pairing_code"
        private const val REPLY = "ai.eqo.onboarding.PAIRING_REPLY"
        const val STOP = "ai.eqo.onboarding.PAIRING_STOP"
    }
}
