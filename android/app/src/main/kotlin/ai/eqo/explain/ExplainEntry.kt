package ai.eqo.explain

import ai.eqo.R
import ai.eqo.accessibility.EQOAccessibilityService
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.edit

object ExplainSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("explain_screen", Context.MODE_PRIVATE)

    fun allowed(context: Context): Boolean = prefs(context).getBoolean("allow_provider", false)

    fun autoRead(context: Context): Boolean = prefs(context).getBoolean("auto_read", false)

    fun notification(context: Context): Boolean = prefs(context).getBoolean("notification", false)

    fun set(
        context: Context,
        key: String,
        enabled: Boolean,
    ) {
        require(key in setOf("allow_provider", "auto_read", "notification"))
        prefs(context).edit { putBoolean(key, enabled) }
    }
}

/** Finishes before reading: neither entry route opens EQO's main/task UI. */
class ExplainEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
        Handler(Looper.getMainLooper()).postDelayed({
            val service = EQOAccessibilityService.getInstance()
            if (service == null) {
                Toast.makeText(applicationContext, R.string.explain_no_accessibility, Toast.LENGTH_LONG).show()
            } else {
                ExplainOverlay.open(service)
            }
        }, ENTRY_DELAY_MS)
    }

    companion object {
        private const val ENTRY_DELAY_MS = 900L

        fun pending(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                701,
                Intent(context, ExplainEntryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

class ExplainTileService : TileService() {
    override fun onClick() {
        super.onClick()
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(ExplainEntryActivity.pending(this))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(Intent(this, ExplainEntryActivity::class.java))
            }
        }
    }
}

/** User-enabled ongoing shortcut. No observation happens until its action is tapped. */
class ExplainNotificationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.explain_title), NotificationManager.IMPORTANCE_LOW),
        )
        val action = ExplainEntryActivity.pending(this)
        val notification =
            Notification
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.explain_title))
                .setContentText(getString(R.string.explain_notification_hint))
                .setOngoing(true)
                .setContentIntent(action)
                .addAction(Notification.Action.Builder(null, getString(R.string.explain_title), action).build())
                .build()
        startForeground(NOTIFICATION, notification)
        if (!ExplainSettings.notification(this)) stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "explain_screen"
        private const val NOTIFICATION = 701

        fun update(context: Context) {
            val intent = Intent(context, ExplainNotificationService::class.java)
            if (ExplainSettings.notification(context)) context.startForegroundService(intent) else context.stopService(intent)
        }
    }
}
