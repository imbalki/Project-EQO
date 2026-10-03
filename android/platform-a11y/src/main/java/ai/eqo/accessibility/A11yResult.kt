/*
 * EQO (TASK-009): typed result/error contract for accessibility automation.
 * Accessibility being disabled mid-task must surface as a typed error and must
 * never be silently retried (see EqoAutomation / GenericAppAutomator).
 */
package ai.eqo.accessibility

/** Typed failure reasons for an accessibility action. */
sealed class A11yError {
    /**
     * The accessibility service is gone: the user turned EQO's accessibility
     * service off (or Android revoked it) while the task was running. Callers
     * must surface this to the user and must NOT retry.
     */
    data object AccessibilityDisabled : A11yError()

    /**
     * The service is connected but no root node is available for the active
     * window (e.g. the window is still settling). Retrying is allowed.
     */
    data class NodeNotFound(
        val target: String,
    ) : A11yError()

    /** The target exists but the platform rejected the action. */
    data class ActionRejected(
        val target: String,
    ) : A11yError()

    /** The user touched the screen during an agent action: the loop is paused. */
    data object TakeoverDetected : A11yError()
}

/** Typed result of one accessibility action. */
sealed class A11yResult {
    data class Success(
        val detail: String,
    ) : A11yResult()

    data class Failure(
        val error: A11yError,
    ) : A11yResult()

    val isSuccess: Boolean get() = this is Success

    companion object {
        fun success(detail: String): A11yResult = Success(detail)

        fun failure(error: A11yError): A11yResult = Failure(error)
    }
}
