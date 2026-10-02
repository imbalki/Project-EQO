// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/accessibility/SmsAutomator.kt
package ai.eqo.accessibility

import android.util.Log
import kotlinx.coroutines.delay

object SmsAutomator {
    suspend fun automateSend(): Boolean {
        val service = EQOAccessibilityService.getInstance() ?: return false

        // Wait for screen transition
        delay(1500)

        // Common SMS app send button IDs
        val sendButtonIds =
            listOf(
                "com.google.android.apps.messaging:id/send_message_button",
                "com.google.android.apps.messaging:id/send_message_button_icon",
                "com.samsung.android.messaging:id/send_button",
                "com.android.mms:id/send_button",
                "com.google.android.apps.messaging:id/send_button",
                "com.android.messaging:id/send_message_button",
            )

        for (id in sendButtonIds) {
            if (service.findAndClickById(id)) {
                Log.d("SmsAutomator", "Successfully clicked SMS send button by ID: $id")
                return true
            }
        }

        // Try clicking by text/content description "Send", "SMS" or similar
        val clicked =
            service.findAndClick("Send") ||
                service.findAndClick("send") ||
                service.findAndClick("SEND") ||
                service.findAndClick("SMS")

        if (clicked) {
            Log.d("SmsAutomator", "Successfully clicked SMS send button by text label")
            return true
        }

        Log.w("SmsAutomator", "Could not click SMS send button automatically")
        return false
    }
}
