// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/accessibility/WhatsAppAutomator.kt
package ai.eqo.accessibility

import android.util.Log
import kotlinx.coroutines.delay

object WhatsAppAutomator {
    suspend fun automateSend(message: String): Boolean {
        // TASK-012 (SF-1): every action goes through the takeover-gated facade.
        val service = EQOAccessibilityService.getInstance() ?: return false
        val actions = service.gatedActions

        // Wait for WhatsApp chat screen to fully load
        // WhatsApp can be slow to render especially on first launch or when opening via deep link
        delay(3000)
        if (service.rootInActiveWindow?.packageName?.toString() != "com.whatsapp") return false

        // Verify we're actually on a WhatsApp chat screen by checking for the input field
        var inputFieldFound = false
        val inputIds = listOf("com.whatsapp:id/entry", "com.whatsapp:id/text_entry")
        for (id in inputIds) {
            val rootNode = service.rootInActiveWindow ?: continue
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                inputFieldFound = true
                nodes.forEach { it.recycle() }
                break
            }
        }

        if (!inputFieldFound) {
            // We may not be on the chat screen yet — wait more and retry
            delay(2000)
            for (id in inputIds) {
                val rootNode = service.rootInActiveWindow ?: continue
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (nodes.isNotEmpty()) {
                    inputFieldFound = true
                    nodes.forEach { it.recycle() }
                    break
                }
            }
            if (!inputFieldFound) {
                Log.w("WhatsAppAutomator", "WhatsApp chat input field not found — not on chat screen")
                return false
            }
        }

        // Try to type the message (in case it wasn't pre-filled by the URI)
        var typed = false
        for (id in inputIds) {
            if (actions.findAndTypeById(id, message).isSuccess) {
                typed = true
                break
            }
        }
        if (!typed) {
            typed = actions.findAndType("Type a message", message).isSuccess
        }

        if (!typed) return false
        delay(800)
        if (service.rootInActiveWindow?.packageName?.toString() != "com.whatsapp") return false

        // WhatsApp send button IDs
        val sendButtonIds =
            listOf(
                "com.whatsapp:id/send",
                "com.whatsapp:id/send_button",
                "com.whatsapp:id/button_send",
            )

        var sendClicked = false
        for (id in sendButtonIds) {
            if (actions.findAndClickById(id).isSuccess) {
                Log.d("WhatsAppAutomator", "Successfully clicked send button by ID: $id")
                sendClicked = true
                break
            }
        }

        if (!sendClicked) {
            // Fallback to clicking Send by text / content description
            sendClicked = actions.findAndClick("Send").isSuccess ||
                actions.findAndClick("send").isSuccess ||
                actions.findAndClick("SEND").isSuccess
            if (sendClicked) {
                Log.d("WhatsAppAutomator", "Successfully clicked send button by text label")
            }
        }

        if (!sendClicked) {
            Log.w("WhatsAppAutomator", "Could not click send button automatically")
            return false
        }

        // Post-send verification: check that the input field is now empty
        // If we can't find the input field, trust the click (optimistic)
        delay(500)
        for (id in inputIds) {
            val rootNode = service.rootInActiveWindow ?: continue
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                val text = node.text?.toString() ?: ""
                node.recycle()
                if (text.isNotBlank() && text != "Type a message" && text != "Message") {
                    // Input field still has text — message likely wasn't sent
                    Log.w("WhatsAppAutomator", "Post-send check: input field is not empty; verify manually")
                    return false
                }
            }
        }

        // Either input field is empty (confirmed sent) or we couldn't find it (trust the click)
        Log.d("WhatsAppAutomator", "Post-send verification passed — message appears to be sent")
        return true
    }
}
