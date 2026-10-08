package ai.eqo.onboarding

import ai.eqo.R
import ai.eqo.adb.pairing.HelperHooks
import ai.eqo.adb.pairing.PairingInput
import ai.eqo.adb.pairing.WirelessAdbActivationRunner
import ai.eqo.study.StudyFlowGate
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Intent
import android.net.Uri
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

    @Volatile
    private var closed = false
    private val expiry = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.wireless_notification_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
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
            else -> changed()
        }
        return START_NOT_STICKY
    }

    private fun acceptReply(intent: Intent) {
        val code = consumeNotificationCode(intent)
        val permitted = StudyFlowGate.permits(StudyFlowGate.StudyTransport.WIRELESS_CONNECT_PLANE)
        if (intent.getStringExtra(TOKEN) != token || WirelessPairingSession.busy || !permitted) return
        val state = WirelessPairingSession.state
        val endpoints = state.endpoints()
        if (code == null || endpoints == null) {
            WirelessPairingSession.message = getString(R.string.wireless_reply_invalid)
            changed()
            return
        }
        val revision = state.revision
        StudySetup.wirelessReport = null
        WirelessPairingSession.busy = true
        token = UUID.randomUUID().toString() // a second delivery of this PendingIntent cannot pair again
        WirelessPairingSession.message = getString(R.string.wireless_adb_working)
        changed()
        worker.execute {
            val report =
                runCatching {
                    val runner = WirelessAdbActivationRunner(StudySetup.keyStore(applicationContext), NoHelperConsent)
                    pairNotificationReply(runner, PairingInput(endpoints, code)) {
                        !closed && state.revision == revision
                    }
                }.getOrNull()
            main.post {
                WirelessPairingSession.busy = false
                if (closed) return@post
                StudySetup.wirelessReport = report
                WirelessPairingSession.message =
                    getString(if (report != null) R.string.wireless_reply_paired else R.string.wireless_reply_failed)
                changed()
            }
        }
    }

    private fun changed() {
        WirelessPairingSession.changed()
        if (!closed) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
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
        val ready = WirelessPairingSession.state.pairingPort != null && !WirelessPairingSession.busy
        val title = if (ready) R.string.wireless_notification_ready else R.string.wireless_notification_title
        val builder =
            Notification
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(title))
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
                    Intent(this, WirelessPairingService::class.java)
                        .setAction(REPLY)
                        .setData(Uri.parse("eqo-pairing://reply/$token"))
                        .putExtra(TOKEN, token),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val action =
                Notification.Action
                    .Builder(null, getString(R.string.wireless_notification_reply), reply)
                    .addRemoteInput(
                        RemoteInput.Builder(CODE).setLabel(getString(R.string.wireless_adb_pairing_code_hint)).build(),
                    ).setAllowGeneratedReplies(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) action.setAuthenticationRequired(true)
            builder.addAction(action.build())
        }
        return builder.build()
    }

    override fun onDestroy() {
        closed = true
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
        internal const val CODE = "pairing_code"
        private const val REPLY = "ai.eqo.onboarding.PAIRING_REPLY"
        const val STOP = "ai.eqo.onboarding.PAIRING_STOP"
    }
}

/** The RemoteInput envelope is consumed in place, including malformed replies. No persistence. */
internal fun consumeNotificationCode(intent: Intent): ai.eqo.adb.pairing.AdbPairingCode? =
    try {
        notificationPairingCode(RemoteInput.getResultsFromIntent(intent)?.getCharSequence(WirelessPairingService.CODE))
    } finally {
        intent.clipData = null
    }
