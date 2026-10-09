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
        val source = intent.getStringExtra("entry_source") ?: "notification"
        android.util.Log.i("EqoExplain", "entry source=$source")
        finish()
        Handler(Looper.getMainLooper()).postDelayed({
            val service = EQOAccessibilityService.getInstance()
            if (service == null) {
                android.util.Log.i("EqoExplain", "no-service source=$source")
                Toast.makeText(applicationContext, R.string.explain_no_accessibility, Toast.LENGTH_LONG).show()
            } else {
                ExplainOverlay.open(service)
            }
        }, ENTRY_DELAY_MS)
    }

    companion object {
        private const val ENTRY_DELAY_MS = 900L
        private const val ENTRY_REQUEST = 701

        fun launch(
            context: Context,
            source: String,
        ) {
            context.startActivity(entryIntent(context, source))
        }

        fun entryIntent(
            context: Context,
            source: String,
        ): Intent =
            Intent(context, ExplainEntryActivity::class.java)
                .putExtra("entry_source", source)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun pending(
            context: Context,
            source: String = "notification",
        ): PendingIntent =
            PendingIntent.getActivity(
                context,
                if (source == "tile") ENTRY_REQUEST + 1 else ENTRY_REQUEST,
                entryIntent(context, source),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

class ExplainTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = android.service.quicksettings.Tile.STATE_ACTIVE
            updateTile()
        }
    }

    // The PendingIntent overload exists only on API 34+. Older devices require the guarded Intent path.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(ExplainEntryActivity.pending(this, "tile"))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(
                    ExplainEntryActivity.entryIntent(this, "tile"),
                )
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
        startForeground(NOTIFICATION, notification(this))
        if (!ExplainSettings.notification(this)) stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "explain_screen"
        private const val NOTIFICATION = 701

        private fun notification(context: Context): Notification {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.explain_title),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            val action = ExplainEntryActivity.pending(context)
            return Notification
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(context.getString(R.string.explain_title))
                .setContentText(context.getString(R.string.explain_notification_hint))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(action)
                .addAction(Notification.Action.Builder(null, context.getString(R.string.explain_title), action).build())
                .build()
        }

        /**
         * Repost without a background FGS, including after OEM service death.
         * User preference and notification grant are both checked.
         */
        @android.annotation.SuppressLint("MissingPermission")
        fun refresh(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (ExplainSettings.notification(context) && manager.areNotificationsEnabled()) {
                manager.notify(NOTIFICATION, notification(context))
            }
        }

        fun update(context: Context) {
            val intent = Intent(context, ExplainNotificationService::class.java)
            if (ExplainSettings.notification(context)) {
                context.startForegroundService(intent)
            } else {
                context.stopService(intent)
                context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
            }
        }
    }
}
