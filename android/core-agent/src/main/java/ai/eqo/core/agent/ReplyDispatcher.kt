// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/core/agent/ReplyDispatcher.kt
package ai.eqo.core.agent

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches auto-replies through WhatsApp (inline reply), SMS, and Email.
 * Uses notification RemoteInput actions for WhatsApp direct reply without opening the app.
 */
@Singleton
class ReplyDispatcher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "ReplyDispatcher"
        }

        /**
         * Reply to a WhatsApp notification using its inline reply RemoteInput action.
         * This sends the reply directly without opening the WhatsApp UI.
         */
        fun replyViaNotificationAction(
            sbn: StatusBarNotification,
            replyText: String,
        ): Boolean {
            try {
                val notification = sbn.notification ?: return false
                val actions = notification.actions ?: return false

                for (action in actions) {
                    val remoteInputs = action.remoteInputs ?: continue
                    if (remoteInputs.isEmpty()) continue

                    // Found a reply action with remote input
                    val intent = Intent()
                    val bundle = Bundle()
                    for (remoteInput in remoteInputs) {
                        bundle.putCharSequence(remoteInput.resultKey, replyText)
                    }
                    RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

                    try {
                        action.actionIntent.send(context, 0, intent)
                        Log.d(TAG, "Successfully sent reply via notification action: ${replyText.take(30)}...")
                        return true
                    } catch (e: PendingIntent.CanceledException) {
                        Log.e(TAG, "PendingIntent cancelled: ${e.message}")
                    }
                }
                Log.w(TAG, "No reply action found in notification")
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reply via notification: ${e.message}")
                return false
            }
        }

        /**
         * Compose an SMS draft for the user (TASK-014, issue #19).
         *
         * EQO never sends directly: [android.telephony.SmsManager] is not used
         * anywhere ([SmsComposePolicy.DIRECT_SEND_ALLOWED] is false), the
         * recipient and content are required before a draft opens, and the
         * actual send happens only when the user taps Send in their messaging
         * app. `ACTION_SENDTO` with the `smsto:` scheme opens the default SMS
         * app in compose mode without any SMS permission.
         */
        fun replyViaSms(
            phoneNumber: String,
            replyText: String,
            context: Context,
        ): Boolean {
            if (!SmsComposePolicy.canCompose(phoneNumber, replyText)) {
                Log.w(TAG, "SMS compose skipped: recipient and content are both required before opening the draft")
                return false
            }
            return try {
                val smsUri = "smsto:$phoneNumber".toUri()
                val intent =
                    Intent(Intent.ACTION_SENDTO, smsUri).apply {
                        // The user confirms both recipient and content by tapping
                        // Send in their messaging app; we only pre-fill the draft.
                        putExtra("sms_body", replyText)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(intent)
                Log.d(TAG, "SMS compose opened for $phoneNumber (send left to the user)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open SMS compose: ${e.message}")
                false
            }
        }

        /**
         * Open email compose with pre-filled reply.
         * For Gmail, we attempt to use the notification's reply action first.
         */
        fun replyViaEmail(
            sbn: StatusBarNotification?,
            to: String,
            subject: String,
            replyText: String,
            context: Context,
        ): Boolean {
            // First try notification inline reply (works for Gmail)
            if (sbn != null) {
                val replied = replyViaNotificationAction(sbn, replyText)
                if (replied) return true
            }

            // Fallback: open email compose intent
            return try {
                val intent =
                    Intent(Intent.ACTION_SENDTO).apply {
                        data = "mailto:".toUri()
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                        putExtra(Intent.EXTRA_SUBJECT, "Re: $subject")
                        putExtra(Intent.EXTRA_TEXT, replyText)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(intent)
                Log.d(TAG, "Email reply compose opened for $to")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open email reply: ${e.message}")
                false
            }
        }
    }
