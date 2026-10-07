/*
 * EQO (TASK-009): takeover detection. A user touch while an agent action is in
 * flight means the user has taken control; the loop must pause.
 */
package ai.eqo.accessibility

import ai.eqo.core.agent.UserResumeConfirmation

/**
 * Detects the user taking control while EQO is mid-action.
 *
 * Inputs:
 *  - [onAgentActionStarted] / [onAgentActionFinished] bracket each agent action
 *    (see [EqoAutomation], which wraps every observe/tap/scroll/type in them).
 *  - [onTouch] reports every screen touch seen by the service's touch probe.
 *    A touch is classified as [TouchSource.AGENT_GESTURE] only while EQO's own
 *    `dispatchGesture` is in flight; everything else is [TouchSource.USER].
 *
 * Rule: a USER touch while at least one agent action is in flight pauses the
 * loop ([isPaused] latches true until [resume]). Touches while EQO is idle are
 * free user interaction and never pause anything.
 *
 * Known limitation (recorded in the evidence file): while a `dispatchGesture`
 * is in flight the probe cannot tell a second concurrent finger from the
 * agent's own stroke, so such touches are attributed to the agent. Touches
 * outside gesture dispatch (node actions, observe, typing, retry waits) are
 * always attributed correctly.
 */
class TakeoverDetector {
    enum class TouchSource {
        USER,
        AGENT_GESTURE,
    }

    /** Why a takeover latched (TASK-012, security note N-3). */
    enum class TakeoverCause {
        /** A real user touch during an agent action. */
        USER,

        /**
         * Latched within the attribution window right after one of EQO's own
         * `dispatchGesture` strokes completed: most likely our own stroke's
         * touch event seen late (the documented mis-attribution window). The
         * takeover still latches (safe), but the loop pauses visibly with
         * `SELF_GESTURE_TAKEOVER_SUSPECTED` instead of dying silently.
         */
        SELF_GESTURE_SUSPECTED,
    }

    private val lock = Any()

    private var actionDepth = 0

    private var lastAgentNodeActionFinishedAtMs: Long = NO_TIME

    private var gracedTouches = 0

    @Volatile
    private var controlTouchExclusion: ((Int, Int) -> Boolean)? = null

    /** Only explicit visible control hitboxes are excluded, never a whole window. */
    fun setControlTouchExclusion(exclusion: ((Int, Int) -> Boolean)?) {
        controlTouchExclusion = exclusion
    }

    fun isControlTouch(
        x: Int,
        y: Int,
    ): Boolean = controlTouchExclusion?.invoke(x, y) == true

    private var lastSelfGestureFinishedAtMs: Long = NO_TIME

    private var lastResumeConfirmation: UserResumeConfirmation? = null

    @Volatile
    var isPaused: Boolean = false
        private set

    /** Number of user touches that triggered a takeover (1 while paused). */
    @Volatile
    var takeoverCount: Int = 0
        private set

    /** Cause of the most recent takeover latch. */
    @Volatile
    var lastTakeoverCause: TakeoverCause = TakeoverCause.USER
        private set

    fun onAgentActionStarted() {
        synchronized(lock) { actionDepth++ }
    }

    fun onAgentActionFinished() {
        synchronized(lock) {
            if (actionDepth > 0) actionDepth--
        }
    }

    /** True when at least one agent action is currently in flight. */
    fun isAgentActionInFlight(): Boolean = synchronized(lock) { actionDepth > 0 }

    /**
     * Reports one screen touch. Returns true when THIS touch triggered the
     * takeover (exactly once per takeover; later touches return false).
     *
     * TASK-012 (N-3): [nowMs] timestamps the touch (elapsed realtime). A
     * touch attributed to the user that arrives within
     * [SELF_GESTURE_ATTRIBUTION_WINDOW_MS] of EQO's own stroke completing is
     * latched with [TakeoverCause.SELF_GESTURE_SUSPECTED] — still a takeover,
     * but reported as a probable self-gesture mis-attribution.
     */
    fun onTouch(
        source: TouchSource,
        nowMs: Long = 0L,
    ): Boolean =
        synchronized(lock) {
            val withinAgentActionGrace =
                source == TouchSource.USER &&
                    gracedTouches < MAX_GRACED_TOUCHES_PER_RUN &&
                    lastAgentNodeActionFinishedAtMs != NO_TIME &&
                    nowMs - lastAgentNodeActionFinishedAtMs in 0..AGENT_ACTION_TOUCH_GRACE_MS
            if (withinAgentActionGrace && !isPaused && actionDepth > 0) {
                // Single use: the touch is attributed to EQO and the window is spent. A second touch needs a
                // new node action, and the per-run cap bounds how many touches this can ever absorb.
                gracedTouches++
                lastAgentNodeActionFinishedAtMs = NO_TIME
            }
            val isUserTakeover =
                !isPaused &&
                    source == TouchSource.USER &&
                    actionDepth > 0 &&
                    !withinAgentActionGrace
            if (isUserTakeover) {
                isPaused = true
                takeoverCount++
                val withinSelfGestureWindow =
                    lastSelfGestureFinishedAtMs != NO_TIME &&
                        nowMs - lastSelfGestureFinishedAtMs in 0..SELF_GESTURE_ATTRIBUTION_WINDOW_MS
                lastTakeoverCause =
                    if (withinSelfGestureWindow) {
                        TakeoverCause.SELF_GESTURE_SUSPECTED
                    } else {
                        TakeoverCause.USER
                    }
            }
            isUserTakeover
        }

    /**
     * Records that one of EQO's own node actions (click, set text, paste) just finished. Android can
     * report a touch-interaction event for the screen change such an action causes; a user-classified
     * touch inside [AGENT_ACTION_TOUCH_GRACE_MS] of it is attributed to EQO, not the user. Any later
     * touch still pauses the run.
     */
    fun onAgentNodeActionFinished(nowMs: Long) {
        synchronized(lock) { lastAgentNodeActionFinishedAtMs = nowMs }
    }

    /** Records the completion of one of EQO's own `dispatchGesture` strokes. */
    fun onSelfGestureFinished(nowMs: Long) {
        synchronized(lock) { lastSelfGestureFinishedAtMs = nowMs }
    }

    /**
     * User hands control back to the agent. TASK-012 (SF-4, spec criterion 4):
     * user-initiated only — requires a [UserResumeConfirmation] minted by the
     * user-facing control surface for an explicit user gesture. No
     * agent-reachable code path may clear a latched takeover
     * (`TakeoverResumeUserOnlyTest`).
     */
    fun resume(confirmation: UserResumeConfirmation) {
        synchronized(lock) {
            lastResumeConfirmation = confirmation
            gracedTouches = 0
            isPaused = false
        }
    }

    companion object {
        /**
         * The one process-wide detector, shared by the accessibility service
         * (touch reporting) and AgentLoop (pause gate) without Hilt wiring.
         */
        val shared: TakeoverDetector = TakeoverDetector()

        /**
         * Window after EQO's own stroke completes in which a user-classified
         * touch is treated as a suspected self-gesture mis-attribution (N-3).
         */
        const val SELF_GESTURE_ATTRIBUTION_WINDOW_MS: Long = 400L

        /** Touch signals this soon after EQO's own node action are its own screen change, not the user. */
        const val AGENT_ACTION_TOUCH_GRACE_MS: Long = 600L

        /** At most this many touches per run can be attributed to EQO's own actions; the next one pauses. */
        const val MAX_GRACED_TOUCHES_PER_RUN: Int = 8

        private const val NO_TIME = Long.MIN_VALUE
    }
}
