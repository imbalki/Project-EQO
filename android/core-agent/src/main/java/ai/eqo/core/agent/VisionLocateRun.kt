// Origin: EQO vision-locate, ephemeral per-approved-run fallback policy.
package ai.eqo.core.agent

import ai.eqo.core.llm.ControlBox
import ai.eqo.core.llm.ControlLocator

/** No screen text escapes this boundary: signature is a local, ephemeral tree digest. */
data class VisionScreenState(
    val width: Int,
    val height: Int,
    val signature: String,
    val app: String,
    val targetBounds: ControlBox,
    val ownWindows: List<ControlBox> = emptyList(),
    val refusal: String? = null,
)

/** Image data is memory-only and deliberately has no generated toString/copy/serialization. */
class VisionFrame(val image: String, val width: Int, val height: Int) {
    override fun toString(): String = "<ephemeral screen image>"
}

interface VisionLocateScreen {
    fun state(): VisionScreenState

    suspend fun capture(): VisionFrame?

    /** Recheck current screen, own windows, password/secure gate and takeover immediately before dispatch. */
    fun tap(box: ControlBox, expected: VisionScreenState): Boolean

    suspend fun settledState(): VisionScreenState
}

/** One instance per run; attempts, including refusals, cannot exceed six. Never automatically retry a tap. */
class VisionLocateRun(
    private val enabled: () -> Boolean,
    private val locator: suspend () -> ControlLocator?,
    private val screen: VisionLocateScreen,
    private val diagnostic: (String) -> Unit = {},
) {
    private var attempts = 0

    @Suppress("ReturnCount") // Each privacy/geometry refusal ends this locate before any further side effect.
    suspend fun locateAndTap(approvedLabel: String): ExecuteResult {
        if (!enabled()) return failure("Screen lookup is off. EQO could not find the approved control.")
        if (attempts >= MAX_LOCATES) return failure("EQO has used all six screen lookups for this run.")
        val attempt = ++attempts
        val before = screen.state()
        before.refusal?.let { return finish(attempt, "refused", it) }
        val model = locator() ?: return finish(attempt, "refused", "Your configured AI model cannot accept screen images, or its image capability could not be checked.")
        // Model metadata lookup may suspend: refuse if the screen changed before capture.
        val ready = screen.state()
        if (ready != before) return finish(attempt, "refused", "The screen changed before EQO could look. Nothing was sent.")
        val frame = screen.capture() ?: return finish(attempt, "refused", "Android refused this screenshot. Protected screens are never sent.")
        if (screen.state() != before || frame.width != before.width || frame.height != before.height) {
            return finish(attempt, "refused", "The screen changed during capture. Nothing was sent.")
        }
        val box = model.locate(approvedLabel, frame.image, frame.width, frame.height)
        if (box == null || !allowed(box, before)) return finish(attempt, "not-found", "EQO could not safely locate the approved control on this screen.")
        val current = screen.state()
        if (current != before || !allowed(box, current) || !enabled()) {
            return finish(attempt, "refused", "The screen or screen-lookup permission changed. EQO did not tap.")
        }
        if (!screen.tap(box, before)) return finish(attempt, "refused", "EQO could not safely tap the approved control.")
        val after = screen.settledState()
        if (after.refusal != null || after.signature == before.signature || after.app != before.app) {
            return finish(attempt, "not-found", "The approved control was not confirmed: the screen stayed the same, became protected, or changed apps. EQO will not tap again.")
        }
        diagnostic("vision locate: attempt $attempt, result found")
        return ExecuteResult.Success("The approved control was tapped; the screen changed.")
    }

    private fun allowed(box: ControlBox, state: VisionScreenState): Boolean =
        state.refusal == null && box.inside(state.width, state.height) &&
            box.left >= state.targetBounds.left && box.top >= state.targetBounds.top &&
            box.right <= state.targetBounds.right && box.bottom <= state.targetBounds.bottom &&
            state.ownWindows.none(box::overlaps)

    private fun finish(attempt: Int, result: String, reason: String): ExecuteResult {
        diagnostic("vision locate: attempt $attempt, result $result")
        return failure(reason)
    }

    private fun failure(reason: String): ExecuteResult = ExecuteResult.Failure(reason, transient = false)

    companion object {
        const val MAX_LOCATES = 6
    }
}
