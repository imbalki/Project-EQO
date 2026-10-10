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
    private val launchApp: (String) -> Boolean = { false },
    private val composeEmail: (String, String, String) -> Boolean = { _, _, _ -> false },
) : StudyAutomationPort,
    StudyNavigationPort by EqoNavigationPort(automation) {
    override fun openApp(app: String): Boolean = launchApp(app)

    override fun typeTarget(
        target: String,
        text: String,
    ): Boolean = automation()?.type(target, text)?.isSuccess == true

    override fun enter(): Boolean =
        ai.eqo.accessibility.GenericAppAutomator
            .pressEnter()
            .isSuccess

    override suspend fun sendChat(
        app: String,
        body: String,
    ): Boolean {
        val packageName =
            EQOAccessibilityService
                .getInstance()
                ?.rootInActiveWindow
                ?.packageName
                ?.toString()
        return when {
            app == "whatsapp" && packageName == "com.whatsapp" ->
                ai.eqo.accessibility.WhatsAppAutomator
                    .automateSend(body)
            app == "telegram" && packageName == "org.telegram.messenger" ->
                ai.eqo.accessibility.TelegramAutomator
                    .automateSend(body)
            else -> false
        }
    }

    override fun composeEmailDraft(
        recipient: String,
        subject: String,
        body: String,
    ): Boolean = composeEmail(recipient, subject, body)

    override fun composeSmsDraft(
        recipient: String,
        body: String,
    ): Boolean = composeDraft(recipient, body)
}

private class EqoNavigationPort(
    private val automation: () -> EqoAutomation?,
) : StudyNavigationPort {
    private var failure: ai.eqo.core.agent.ExecuteResult.Failure? = null

    override val lastFailure: ai.eqo.core.agent.ExecuteResult.Failure?
        get() = failure

    override fun observe(): String {
        val result = automation()?.observe()
        record(result)
        val text = (result as? A11yResult.Success)?.detail.orEmpty()
        if (result is A11yResult.Success && text.isBlank()) {
            failure =
                ai.eqo.core.agent.ExecuteResult
                    .Failure("a11y_empty_observation", transient = false)
            android.util.Log.i("EqoRun", "a11y=a11y_empty_observation")
        }
        return text
    }

    override fun tap(text: String): Boolean = recordTap(automation()?.tap(text))

    override fun tapById(viewId: String): Boolean = recordTap(automation()?.tapById(viewId))

    override fun typeText(text: String): Boolean = record(automation()?.type(searchText = text, content = text))

    override fun scroll(direction: String): Boolean {
        val forward = !direction.equals("up", ignoreCase = true)
        return record(automation()?.scroll(forward = forward))
    }

    private fun recordTap(result: A11yResult?): Boolean {
        val success = record(result)
        if ((result as? A11yResult.Failure)?.error is ai.eqo.accessibility.A11yError.NodeNotFound) {
            failure =
                ai.eqo.core.agent.ExecuteResult
                    .Failure("control_not_found", transient = false)
        }
        return success
    }

    /** Fixed codes only: target strings and screen contents never enter diagnostics. */
    private fun record(result: A11yResult?): Boolean {
        val error = (result as? A11yResult.Failure)?.error
        val code =
            when {
                result == null -> "a11y_not_bound"
                error == ai.eqo.accessibility.A11yError.AccessibilityDisabled -> "a11y_disabled"
                error == ai.eqo.accessibility.A11yError.TakeoverDetected -> "a11y_takeover"
                error == ai.eqo.accessibility.A11yError.SecureWindow -> "a11y_secure_window"
                error is ai.eqo.accessibility.A11yError.NodeNotFound ->
                    when (error.target) {
                        "active window" -> "a11y_no_active_root"
                        "scrollable node" -> "a11y_no_scrollable_node"
                        else -> "a11y_node_not_found"
                    }
                error is ai.eqo.accessibility.A11yError.ActionRejected -> "a11y_action_rejected"
                else -> null
            }
        failure =
            code?.let {
                ai.eqo.core.agent.ExecuteResult
                    .Failure(it, transient = error is ai.eqo.accessibility.A11yError.NodeNotFound)
            }
        android.util.Log.i("EqoRun", "a11y=${code ?: "success"}")
        return result is A11yResult.Success
    }

    override fun back(): Boolean {
        failure = null
        return serviceGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    override fun home(): Boolean {
        failure = null
        return serviceGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    override fun enter(): Boolean =
        ai.eqo.accessibility.GenericAppAutomator
            .pressEnter()
            .isSuccess
}

private fun serviceGlobalAction(action: Int): Boolean {
    val service = EQOAccessibilityService.getInstance() ?: return false
    return when (action) {
        AccessibilityService.GLOBAL_ACTION_BACK -> service.gatedActions.pressBack().isSuccess
        AccessibilityService.GLOBAL_ACTION_HOME -> service.gatedActions.pressHome().isSuccess
        else -> false
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
            val uri = "smsto:${android.net.Uri.encode(recipient, "+")}".toUri()
            val intent =
                Intent(Intent.ACTION_SENDTO, uri).apply {
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
