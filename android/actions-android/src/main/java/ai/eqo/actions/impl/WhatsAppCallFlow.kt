// Origin: EQO WhatsApp call task, shared targeted chat deep link and single-attempt call flow.
package ai.eqo.actions.impl

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.EqoAutomation
import ai.eqo.actions.base.ActionResult
import android.content.Intent
import androidx.core.net.toUri
import kotlinx.coroutines.delay
import java.net.URLEncoder

/** The SEND_WHATSAPP deep link, with no text payload for calls. Never route through a browser. */
internal fun GatedIntentLauncher.openWhatsAppChat(
    phone: String,
    message: String? = null,
) {
    val text = message?.let { "text=${URLEncoder.encode(it, "UTF-8")}" }
    val uri =
        if (phone.matches(Regex("\\+?[0-9]+"))) {
            "https://api.whatsapp.com/send?phone=$phone" + (text?.let { "&$it" } ?: "")
        } else {
            require(message != null) { "A WhatsApp call needs a resolved phone number." }
            "whatsapp://send?$text"
        }
    open(
        Intent(Intent.ACTION_VIEW, uri.toUri()).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

/** No retry after a click (even a rejected click); a missing label alone permits the voice-label fallback. */
internal class WhatsAppCallFlow(
    private val launcher: GatedIntentLauncher,
    private val automation: () -> EqoAutomation?,
) {
    suspend fun execute(
        phone: String,
        video: Boolean,
    ): ActionResult {
        launcher.openWhatsAppChat(phone)
        delay(CHAT_SETTLE_MS)
        val facade = automation() ?: return A11yResult.failure(A11yError.AccessibilityDisabled).toActionResult()
        val label = if (video) "Video call" else "Voice call"
        val first = facade.tapContentDescription(label, "com.whatsapp")
        val result =
            if (!video && first is A11yResult.Failure && first.error is A11yError.NodeNotFound) {
                facade.tapContentDescription("Call", "com.whatsapp")
            } else {
                first
            }
        return when (result) {
            is A11yResult.Success ->
                ActionResult.Success(
                    mapOf(
                        "message" to
                            "WhatsApp ${if (video) "video" else "voice"} call control pressed; " +
                            "ringing is not verified.",
                    ),
                )
            is A11yResult.Failure ->
                if (result.error is A11yError.NodeNotFound) {
                    ActionResult.UserActionRequired(
                        "WhatsApp chat opened, but the $label control was not found. " +
                            "No call was pressed; finish manually. No automatic retry.",
                    )
                } else {
                    result.toActionResult()
                }
        }
    }

    private companion object {
        const val CHAT_SETTLE_MS = 3000L
    }
}
