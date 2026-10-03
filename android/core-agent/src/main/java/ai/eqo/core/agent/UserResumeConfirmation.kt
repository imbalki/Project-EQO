/*
 * EQO (TASK-012, issue #17): the explicit-user-confirmation token required by
 * the spec's criterion 4 ("Resume only after explicit user confirmation") and
 * by security follow-up SF-4 ("TakeoverDetector resume must be
 * user-initiated only").
 */
package ai.eqo.core.agent

/**
 * Caller assertion of an explicit resume gesture; NOT an attested proof of a
 * human gesture. No production confirmation UI is wired yet.
 *
 * There is deliberately no parameterless resume path anywhere in the loop or
 * the takeover detector: both take this token, and the token can only be minted
 * through [forExplicitUserConfirmation]. Agent-reachable code must never mint
 * one — `TakeoverResumeUserOnlyTest` enforces that no file under
 * app, core-agent, core-llm, core-security or platform-a11y main source calls
 * the mint function, so no planning/execution code path can clear a latched
 * takeover or resume a paused loop on its own.
 *
 * @param confirmedAtMs wall-clock time of the confirming user gesture, for the
 *   audit trail ("the user always knows what did and did not happen").
 */
class UserResumeConfirmation
    private constructor(
        val confirmedAtMs: Long,
    ) {
        companion object {
            /**
             * Mints a confirmation for one explicit user gesture. ONLY user-facing
             * UI code (a resume button / confirm dialog handler) may call this;
             * never agent, loop, recovery or automation code.
             */
            fun forExplicitUserConfirmation(confirmedAtMs: Long) = UserResumeConfirmation(confirmedAtMs)
        }
    }
