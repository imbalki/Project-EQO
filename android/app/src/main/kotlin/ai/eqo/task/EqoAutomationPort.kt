/*
 * EQO (TASK-015, issue #20): the study automation port over TASK-009's single
 * accessibility service (`EqoAutomation`, reached through `EQOAccessibilityService`).
 *
 * Every method reports only what really happened: no service bound = the action did not
 * apply. Compose-only SMS: the draft is opened in the messaging app and nothing is sent
 * (REQ-SMS-01).
 */
package ai.eqo.task

import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.EqoAutomation
import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri

/**
 * [StudyAutomationPort] over the one EQO accessibility service.
 *
 * @param automation resolves the live automation (null when the service is not bound).
 */
class EqoAutomationPort(
    private val automation: () -> EqoAutomation?,
    private val composeDraft: (recipient: String, body: String) -> Boolean,
) : StudyAutomationPort {
    override fun observe(): String =
        when (val result = automation()?.observe()) {
            is A11yResult.Success -> result.detail
            is A11yResult.Failure -> ""
            null -> ""
        }

    override fun tap(text: String): Boolean = automation()?.tap(text)?.isSuccess == true

    override fun tapById(viewId: String): Boolean = automation()?.tapById(viewId)?.isSuccess == true

    override fun typeText(text: String): Boolean =
        automation()
            ?.type(searchText = text, content = text)
            ?.isSuccess == true

    override fun scroll(direction: String): Boolean =
        automation()?.scroll(forward = !direction.equals("up", ignoreCase = true))?.isSuccess == true

    override fun back(): Boolean = globalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    override fun home(): Boolean = globalAction(AccessibilityService.GLOBAL_ACTION_HOME)

    override fun composeSmsDraft(
        recipient: String,
        body: String,
    ): Boolean = composeDraft(recipient, body)

    private fun globalAction(action: Int): Boolean {
        val service = EQOAccessibilityService.getInstance() ?: return false
        return when (action) {
            AccessibilityService.GLOBAL_ACTION_BACK -> service.gatedActions.pressBack().isSuccess
            AccessibilityService.GLOBAL_ACTION_HOME -> service.gatedActions.pressHome().isSuccess
            else -> false
        }
    }
}

/**
 * Compose-first SMS (UF-13, REQ-SMS-01): opens the messaging app with the recipient and
 * body filled in. This builds an ACTION_SENDTO intent and hands the final send to the
 * user — EQO never sends a message itself. No chooser is introduced, even when
 * the recipient is empty. A supplied default SMS package targets that app directly.
 *
 * @param defaultSmsPackage resolves the default messaging app, or null for implicit routing.
 * @param onNoSmsApp reports that no activity handled the draft; other failures do not call it.
 * @param startActivity launches the draft, kept last for existing trailing-lambda callers.
 */
class SmsDraftOpener(
    private val defaultSmsPackage: () -> String? = { null },
    private val onNoSmsApp: () -> Unit = {},
    private val startActivity: (Intent) -> Unit,
) {
    fun open(
        recipient: String,
        body: String,
    ): Boolean =
        try {
            val intent =
                Intent(Intent.ACTION_SENDTO, "smsto:$recipient".toUri()).apply {
                    putExtra("sms_body", body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    defaultSmsPackage()?.let { setPackage(it) }
                }
            startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            onNoSmsApp()
            false
        } catch (_: Exception) {
            false
        }
}
