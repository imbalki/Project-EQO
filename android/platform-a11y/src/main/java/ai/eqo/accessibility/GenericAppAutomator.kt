// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/accessibility/GenericAppAutomator.kt
// TASK-009: return types changed from Boolean to the typed A11yResult contract;
// the retry loop retries ONLY the typed NodeNotFound (cold-start) case and aborts
// immediately on A11yError.AccessibilityDisabled - a disabled accessibility
// service surfaces as a typed error, never a silent retry.
package ai.eqo.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import kotlinx.coroutines.delay

object GenericAppAutomator {
    private const val RETRY_TIMEOUT_MS = 5000L
    private const val RETRY_INTERVAL_MS = 300L

    /**
     * Freshly launched apps (e.g. right after OPEN_APP or a self-contained action
     * like PLAY_YOUTUBE) are often still cold-starting when the next automation step
     * runs, so their UI elements aren't laid out yet. Polls [attempt] every
     * [RETRY_INTERVAL_MS] until it succeeds or [RETRY_TIMEOUT_MS] elapses, instead of
     * giving up after a single immediate try.
     *
     * TASK-009: only [A11yError.NodeNotFound] is retried (the cold-start case).
     * Every other typed failure - notably [A11yError.AccessibilityDisabled] and
     * [A11yError.TakeoverDetected] - is returned on the first attempt.
     */
    private suspend fun retryUntilSettled(attempt: () -> A11yResult): A11yResult {
        val deadline = SystemClock.elapsedRealtime() + RETRY_TIMEOUT_MS
        while (true) {
            val result = attempt()
            val retryable = result is A11yResult.Failure && result.error is A11yError.NodeNotFound
            if (!retryable || SystemClock.elapsedRealtime() >= deadline) {
                return result
            }
            delay(RETRY_INTERVAL_MS)
        }
    }

    private fun automationOrNull(): EqoAutomation? = EQOAccessibilityService.getInstance()?.automation

    suspend fun clickText(text: String): A11yResult =
        retryUntilSettled {
            automationOrNull()?.tap(text) ?: A11yResult.failure(A11yError.AccessibilityDisabled)
        }

    suspend fun clickId(viewId: String): A11yResult =
        retryUntilSettled {
            automationOrNull()?.tapById(viewId) ?: A11yResult.failure(A11yError.AccessibilityDisabled)
        }

    suspend fun typeText(
        searchText: String,
        content: String,
    ): A11yResult =
        retryUntilSettled {
            automationOrNull()?.type(searchText, content)
                ?: A11yResult.failure(A11yError.AccessibilityDisabled)
        }

    suspend fun typeId(
        viewId: String,
        content: String,
    ): A11yResult =
        retryUntilSettled {
            automationOrNull()?.typeById(viewId, content)
                ?: A11yResult.failure(A11yError.AccessibilityDisabled)
        }

    /**
     * Performs the IME 'enter/search/go' action on the currently focused editable
     * field — submits a search box after [typeText]/[typeId] fills it in. No polling
     * here: it's meant to run immediately after a successful type, once the field is
     * already known to exist.
     */
    fun pressEnter(): A11yResult {
        val service =
            EQOAccessibilityService.getInstance()
                ?: return A11yResult.failure(A11yError.AccessibilityDisabled)
        return if (service.performImeEnter()) {
            A11yResult.success("ime enter")
        } else {
            A11yResult.failure(A11yError.ActionRejected("enter"))
        }
    }

    fun scrapeScreen(): A11yResult {
        val automation = automationOrNull()
        return automation?.observe()
            ?: A11yResult.failure(A11yError.AccessibilityDisabled)
    }

    fun pressBack(): A11yResult =
        globalAction("back") {
            it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }

    fun pressHome(): A11yResult =
        globalAction("home") {
            it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }

    private fun globalAction(
        name: String,
        action: (EQOAccessibilityService) -> Boolean,
    ): A11yResult {
        val service =
            EQOAccessibilityService.getInstance()
                ?: return A11yResult.failure(A11yError.AccessibilityDisabled)
        return if (action(service)) {
            A11yResult.success(name)
        } else {
            A11yResult.failure(A11yError.ActionRejected(name))
        }
    }

    fun scroll(forward: Boolean): A11yResult {
        val service =
            EQOAccessibilityService.getInstance()
                ?: return A11yResult.failure(A11yError.AccessibilityDisabled)
        return if (service.performScroll(forward)) {
            A11yResult.success("scrolled ${if (forward) "forward" else "backward"}")
        } else {
            A11yResult.failure(A11yError.NodeNotFound("scrollable node"))
        }
    }

    fun clickCoordinates(
        x: Float,
        y: Float,
    ): A11yResult {
        val service =
            EQOAccessibilityService.getInstance()
                ?: return A11yResult.failure(A11yError.AccessibilityDisabled)
        return if (service.clickCoordinates(x, y)) {
            A11yResult.success("gesture tap ($x,$y)")
        } else {
            A11yResult.failure(A11yError.ActionRejected("gesture tap"))
        }
    }
}
