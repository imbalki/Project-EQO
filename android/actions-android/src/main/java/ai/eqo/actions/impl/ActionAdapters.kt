// Origin: EQO TASK-069 (#20), adapters to the existing takeover-gated accessibility facade.
package ai.eqo.actions.impl

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.EqoAutomation
import ai.eqo.actions.base.ActionResult
import android.content.Context
import android.content.Intent

internal fun A11yResult.toActionResult(): ActionResult =
    when (this) {
        is A11yResult.Success -> ActionResult.Success(mapOf("message" to detail))
        is A11yResult.Failure ->
            ActionResult.Failure(
                when (error) {
                    A11yError.AccessibilityDisabled -> "Enable EQO accessibility in Settings before continuing."
                    A11yError.TakeoverDetected -> "You took control. Resume the task explicitly before continuing."
                    A11yError.SecureWindow -> "This is a protected screen. EQO will not read it."
                    is A11yError.NodeNotFound -> "The requested screen element was not found."
                    is A11yError.ActionRejected -> "Android did not accept the requested action."
                },
            )
    }

internal class GatedIntentLauncher(
    val context: Context,
    private val automation: () -> EqoAutomation?,
) {
    fun open(intent: Intent) {
        val result =
            automation()?.runAction {
                context.startActivity(intent)
                A11yResult.success("App opened.")
            } ?: A11yResult.failure(A11yError.AccessibilityDisabled)
        if (!result.isSuccess) throw IllegalStateException(result.toActionResult().error)
    }
}
