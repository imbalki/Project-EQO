// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: path: app/src/main/java/com/opendroid/ai/actions/NotificationActions.kt; EQO TASK-078 port.
package ai.eqo.actions.impl

import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import ai.eqo.data.db.entities.NotificationEntity
import ai.eqo.data.models.AutoReplyConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saved-notification history, auto-reply preferences and history clearing. These read and write only EQO's own Room
 * table and settings; they never touch the live status bar, and nothing here sends a message.
 */
internal class NotificationActions(
    private val daos: AutomationDaos,
    private val autoReply: AutoReplyConfigStore,
) {
    fun getActions(): List<Action> =
        listOf(
            RegisteredExecutor("READ_NOTIFICATIONS") { params, _ -> read(params) },
            RegisteredExecutor("AUTO_REPLY_TOGGLE") { params, _ -> toggleAutoReply(params) },
            RegisteredExecutor("DISMISS_NOTIFICATION") { params, _ -> dismiss(params) },
        )

    private suspend fun read(params: Map<String, String>): ActionResult {
        val app = params["app"]?.trim().orEmpty()
        val count = (params["count"]?.trim()?.toIntOrNull() ?: DEFAULT_COUNT).coerceIn(1, MAX_COUNT)
        val rows =
            if (app.isEmpty()) {
                daos.notifications.getRecentNotifications(count)
            } else {
                daos.notifications.getNotificationsByApp(notificationPackageForApp(app), count)
            }
        val text =
            if (rows.isEmpty()) {
                "No notifications found."
            } else {
                "Here are your recent notifications:\n\n${format(rows)}"
            }
        return ActionResult.Success(mapOf("message" to text))
    }

    private fun format(rows: List<NotificationEntity>): String {
        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return rows.joinToString("\n\n") { row ->
            val replyText = row.autoReplyText?.take(REPLY_PREVIEW_CHARS)
            val replied = if (row.isAutoReplied) "\n  Auto-replied: $replyText" else ""
            "${row.appName} - ${dateFormat.format(Date(row.timestamp))}\n" +
                "  From: ${row.contactName ?: row.title}\n  ${row.text.take(TEXT_PREVIEW_CHARS)}$replied"
        }
    }

    private suspend fun toggleAutoReply(params: Map<String, String>): ActionResult {
        val state = params["state"]?.lowercase(Locale.ROOT)?.trim()
        val app = params["app"]?.lowercase(Locale.ROOT)?.trim().orEmpty()
        val current = autoReply.current()
        val updated =
            when (app) {
                "" -> current.copy(globalEnabled = flip(current.globalEnabled, state))
                "whatsapp" -> current.copy(whatsappEnabled = flip(current.whatsappEnabled, state))
                "sms" -> current.copy(smsEnabled = flip(current.smsEnabled, state))
                "email" -> current.copy(emailEnabled = flip(current.emailEnabled, state))
                else -> null
            } ?: return ActionResult.Failure("Auto-reply can be changed for WhatsApp, SMS, email, or for all apps.")
        autoReply.update(updated)
        return ActionResult.Success(mapOf("message" to describe(updated)))
    }

    private fun flip(
        current: Boolean,
        state: String?,
    ): Boolean = state == "on" || (state != "off" && !current)

    private fun describe(config: AutoReplyConfig): String =
        buildString {
            append("Auto-reply is now ${if (config.globalEnabled) "ON" else "OFF"}")
            if (config.globalEnabled) {
                val apps =
                    listOfNotNull(
                        "WhatsApp".takeIf { config.whatsappEnabled },
                        "SMS".takeIf { config.smsEnabled },
                        "Email".takeIf { config.emailEnabled },
                    )
                if (apps.isNotEmpty()) append(" for ${apps.joinToString(", ")}")
                append(". Reply delay: ${config.replyDelayMinutes} minutes.")
            }
        }

    private suspend fun dismiss(params: Map<String, String>): ActionResult {
        val app = params["app"]?.trim()?.takeIf { it.isNotEmpty() }
        val idText = params["notificationId"]?.trim()?.takeIf { it.isNotEmpty() }
        val id = idText?.toLongOrNull()
        return when {
            app != null && idText != null ->
                ActionResult.Failure("Provide either an app or a notification ID, not both.")
            idText != null && id == null -> ActionResult.Failure("Notification ID must be a number.")
            else -> {
                val deleted =
                    when {
                        id != null -> daos.notifications.deleteNotificationById(id)
                        app != null -> daos.notifications.deleteNotificationsByApp(notificationPackageForApp(app))
                        else -> daos.notifications.clearAll()
                    }
                ActionResult.Success(mapOf("message" to dismissMessage(deleted)))
            }
        }
    }

    private fun dismissMessage(deleted: Int): String =
        when (deleted) {
            0 -> "No matching notifications found."
            1 -> "Dismissed 1 notification."
            else -> "Dismissed $deleted notifications."
        }
}

private const val DEFAULT_COUNT = 10
private const val MAX_COUNT = 50
private const val REPLY_PREVIEW_CHARS = 50
private const val TEXT_PREVIEW_CHARS = 120

/** Maps the app names accepted by notification actions to stored package names. */
internal fun notificationPackageForApp(app: String): String =
    when (val normalized = app.trim().lowercase(Locale.ROOT)) {
        "whatsapp" -> "com.whatsapp"
        "sms", "messages", "messaging" -> "com.google.android.apps.messaging"
        "gmail", "email" -> "com.google.android.gm"
        "instagram" -> "com.instagram.android"
        "telegram" -> "org.telegram.messenger"
        "twitter", "x" -> "com.twitter.android"
        else -> normalized
    }
