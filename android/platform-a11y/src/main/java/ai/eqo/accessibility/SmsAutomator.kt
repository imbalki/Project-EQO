// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/accessibility/SmsAutomator.kt
package ai.eqo.accessibility

import android.util.Log
import kotlinx.coroutines.delay

object SmsAutomator {
    /**
     * TASK-012 (SF-1): test seam. Production resolves the service's
     * takeover-gated facade; every action runs through it.
     */
    internal var actionsProvider: () -> GatedServiceActions? = {
        EQOAccessibilityService.getInstance()?.gatedActions
    }

    suspend fun automateSend(): Boolean {
        val actions = actionsProvider() ?: return false

        // Wait for screen transition
        delay(1500)

        // Common SMS app send button IDs
        val sendButtonIds =
            listOf(
                // Current Google Messages (Compose UI): the send control has no package-qualified id.
                "Compose:Draft:Send",
                "com.google.android.apps.messaging:id/send_message_button",
                "com.google.android.apps.messaging:id/send_message_button_icon",
                "com.samsung.android.messaging:id/send_button",
                "com.android.mms:id/send_button",
                "com.google.android.apps.messaging:id/send_button",
                "com.android.messaging:id/send_message_button",
            )

        for (id in sendButtonIds) {
            if (actions.findAndClickById(id).isSuccess) {
                Log.d("SmsAutomator", "Successfully clicked SMS send button by ID: $id")
                return true
            }
        }

        // Try clicking by text/content description "Send", "SMS" or similar
        val clicked =
            actions.findAndClick("Send").isSuccess ||
                actions.findAndClick("send").isSuccess ||
                actions.findAndClick("SEND").isSuccess ||
                actions.findAndClick("SMS").isSuccess

        if (clicked) {
            Log.d("SmsAutomator", "Successfully clicked SMS send button by text label")
            return true
        }

        Log.w("SmsAutomator", "Could not click SMS send button automatically")
        return false
    }
}
