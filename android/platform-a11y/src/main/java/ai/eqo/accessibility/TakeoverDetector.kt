/*
 * EQO (TASK-009): takeover detection. A user touch while an agent action is in
 * flight means the user has taken control; the loop must pause.
 */
package ai.eqo.accessibility

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

    private val lock = Any()

    private var actionDepth = 0

    @Volatile
    var isPaused: Boolean = false
        private set

    /** Number of user touches that triggered a takeover (1 while paused). */
    @Volatile
    var takeoverCount: Int = 0
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
     */
    fun onTouch(source: TouchSource): Boolean =
        synchronized(lock) {
            val isUserTakeover = !isPaused && source == TouchSource.USER && actionDepth > 0
            if (isUserTakeover) {
                isPaused = true
                takeoverCount++
            }
            isUserTakeover
        }

    /** User hands control back to the agent (resume UX belongs to TASK-012). */
    fun resume() {
        synchronized(lock) { isPaused = false }
    }

    companion object {
        /**
         * The one process-wide detector, shared by the accessibility service
         * (touch reporting) and AgentLoop (pause gate) without Hilt wiring.
         */
        val shared: TakeoverDetector = TakeoverDetector()
    }
}
