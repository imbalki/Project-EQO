// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/accessibility/GenericAppAutomator.kt
// TASK-009: return types changed from Boolean to the typed A11yResult contract;
// the retry loop retries ONLY the typed NodeNotFound (cold-start) case and aborts
// immediately on A11yError.AccessibilityDisabled - a disabled accessibility
// service surfaces as a typed error, never a silent retry.
package ai.eqo.accessibility

import kotlinx.coroutines.delay

object GenericAppAutomator {
    private const val MAX_ATTEMPTS = 18
    private const val RETRY_INTERVAL_MS = 300L
    private const val TAP_ATTEMPTS = 4
    private const val TAP_RETRY_INTERVAL_MS = 700L
    private const val SETTLE_AFTER_TYPE_MS = 700L

    /**
     * Freshly launched apps (e.g. right after OPEN_APP or a self-contained action
     * like PLAY_YOUTUBE) are often still cold-starting when the next automation step
     * runs, so their UI elements aren't laid out yet. Polls [attempt] every
     * [RETRY_INTERVAL_MS] until it succeeds or the bounded attempt budget expires, instead of
     * giving up after a single immediate try.
     *
     * TASK-009: only [A11yError.NodeNotFound] is retried (the cold-start case).
     * Every other typed failure - notably [A11yError.AccessibilityDisabled] and
     * [A11yError.TakeoverDetected] - is returned on the first attempt.
     */
    internal suspend fun retryUntilSettled(attempt: () -> A11yResult): A11yResult {
        // A hard cap also bounds host-fake waits (no Android clock). Cancellation
        // remains responsive, and rejected mutations are never repeated.
        repeat(MAX_ATTEMPTS - 1) {
            val result = attempt()
            if (result !is A11yResult.Failure || result.error !is A11yError.NodeNotFound) return result
            delay(RETRY_INTERVAL_MS)
        }
        return attempt()
    }

    /** Four missing-control probes, 2.1 seconds total waiting; never repeat an accepted/rejected mutation. */
    internal suspend fun retryTap(attempt: () -> A11yResult): A11yResult {
        repeat(TAP_ATTEMPTS - 1) {
            val result = attempt()
            if (result !is A11yResult.Failure || result.error !is A11yError.NodeNotFound) return result
            delay(TAP_RETRY_INTERVAL_MS)
        }
        return attempt()
    }

    private fun automationOrNull(): EqoAutomation? = EQOAccessibilityService.getInstance()?.automation

    /**
     * TASK-012 (SF-1): test seam. Production resolves the service's
     * takeover-gated facade over the raw [ServiceActionOps]; every action below
     * runs through [EqoAutomation.runAction] inside it.
     */
    internal var actionsProvider: () -> GatedServiceActions? = {
        EQOAccessibilityService.getInstance()?.gatedActions
    }

    private fun gated(block: (GatedServiceActions) -> A11yResult): A11yResult {
        val actions = actionsProvider() ?: return A11yResult.failure(A11yError.AccessibilityDisabled)
        return block(actions)
    }

    /** Keep's home-screen entry is already preceded by WAIT; a missing entry requires the owner. */
    internal suspend fun clickTextAttempt(
        text: String,
        attempt: () -> A11yResult,
    ): A11yResult = if (text == "Create a note") attempt() else retryTap(attempt)

    suspend fun clickText(text: String): A11yResult =
        clickTextAttempt(text) {
            automationOrNull()?.tap(text) ?: A11yResult.failure(A11yError.AccessibilityDisabled)
        }

    suspend fun clickId(viewId: String): A11yResult =
        retryTap {
            automationOrNull()?.tapById(viewId) ?: A11yResult.failure(A11yError.AccessibilityDisabled)
        }

    /**
     * Types into the matched field and confirms the text landed. Many search bars are buttons or launcher
     * fields that open a separate search screen whose own field is then focused and empty, so:
     *  - typed but the focused field is empty (or the attempt was rejected): type once more into the focused field;
     *  - no field matched: tap the matching label, wait for the new screen, then type into the focused field.
     * Typing is never repeated when the text is already there.
     */
    suspend fun typeText(
        searchText: String,
        content: String,
    ): A11yResult {
        if (searchText.startsWith("id:") || searchText.lowercase() in setOf("focused", "current")) {
            return if (searchText.startsWith("id:")) typeId(searchText, content) else typeOnce(searchText, content)
        }
        // One quick look first: the step has a short time budget, and a launcher-style search button
        // (no input exists yet) must reach the tap fallback before the cold-start retries use it up.
        val quick = automationOrNull()?.type(searchText, content) ?: A11yResult.failure(A11yError.AccessibilityDisabled)
        val error = (quick as? A11yResult.Failure)?.error
        return when {
            error is A11yError.NodeNotFound -> {
                val viaTap = tapThenTypeFocused(searchText, content, quick)
                if (viaTap.isSuccess) viaTap else typeOnce(searchText, content)
            }
            quick.isSuccess || error is A11yError.ActionRejected -> confirmOrRetype(quick, content)
            else -> quick
        }
    }

    private suspend fun typeOnce(
        target: String,
        content: String,
    ): A11yResult =
        retryUntilSettled {
            automationOrNull()?.type(target, content)
                ?: A11yResult.failure(A11yError.AccessibilityDisabled)
        }

    private suspend fun confirmOrRetype(
        first: A11yResult,
        content: String,
    ): A11yResult {
        val automation = automationOrNull() ?: return first
        delay(SETTLE_AFTER_TYPE_MS)
        val landed = automation.focusedInputHolds(content) || !automation.hasFocusedInput()
        return if (landed) first else typeOnce("focused", content)
    }

    private suspend fun tapThenTypeFocused(
        label: String,
        content: String,
        notFound: A11yResult,
    ): A11yResult {
        val tapped = automationOrNull()?.tap(label)
        if (tapped?.isSuccess != true) return notFound
        delay(SETTLE_AFTER_TYPE_MS)
        val retyped = typeOnce("focused", content)
        return if (retyped.isSuccess) retyped else notFound
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
    fun pressEnter(): A11yResult = gated { it.pressEnter() }

    fun scrapeScreen(): A11yResult {
        val automation = automationOrNull()
        return automation?.observe()
            ?: A11yResult.failure(A11yError.AccessibilityDisabled)
    }

    fun pressBack(): A11yResult = gated { it.pressBack() }

    fun pressBackInApp(): A11yResult = gated { it.pressBack(restrictToApp = true) }

    fun pressHome(): A11yResult = gated { it.pressHome() }

    fun scroll(forward: Boolean): A11yResult = gated { it.scroll(forward) }

    fun clickCoordinates(
        x: Float,
        y: Float,
    ): A11yResult = gated { it.clickCoordinates(x, y) }
}
