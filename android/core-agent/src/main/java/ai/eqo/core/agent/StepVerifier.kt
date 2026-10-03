/*
 * EQO (TASK-012, issue #17): postcondition verification. The verifier confirms
 * postconditions or records a typed partial-apply result — it never silently
 * claims an action applied.
 */
package ai.eqo.core.agent

/**
 * Verifies one executed action against what is observably on screen afterwards.
 *
 * Screen matches are attacker-controllable and SPOOFABLE, not independent proof
 * of an outward effect. Confirmed means executor success plus matching rendered
 * text, not a trusted receipt. Callers must not use it as authorization.
 *
 * Outcomes are typed and honest:
 *  - [Outcome.Confirmed]: every declared postcondition is observed (or none was
 *    declared and the action reported success).
 *  - [Outcome.Partial]: the effect is only partly observable — some
 *    postconditions hold, others do not, or an action was cancelled/timed out
 *    mid-apply. [PartialApply] names exactly what did and did not happen.
 *  - [Outcome.Failed]: the action failed and no declared postcondition holds.
 */
class StepVerifier {
    companion object {
        const val SCREEN_EVIDENCE_WARNING = "Screen postconditions are untrusted and spoofable; not a trusted receipt"
    }

    /** Typed partial-apply record: what applied and what did not. */
    data class PartialApply(
        val applied: List<String>,
        val notApplied: List<String>,
        val note: String,
        val evidenceWarning: String = SCREEN_EVIDENCE_WARNING,
    )

    sealed class Outcome {
        data object Confirmed : Outcome()

        data class Partial(
            val detail: PartialApply,
        ) : Outcome()

        data class Failed(
            val reason: String,
            /** True when the executor flagged the failure as retryable (reversible steps only). */
            val transient: Boolean = false,
        ) : Outcome()
    }

    /**
     * @param action the executed action (its declared postconditions).
     * @param result what the executor reported.
     * @param observedScreen screen text observed AFTER the action (untrusted
     *   data; only used to look for declared postconditions).
     */
    fun verify(
        action: ExecutedAction,
        result: ExecuteResult,
        observedScreen: String,
    ): Outcome {
        val declared = action.expectedPostconditions
        val observed = observedScreen.lowercase()
        val present = declared.filter { observed.contains(it.lowercase()) }
        val missing = declared - present.toSet()
        val failure = result as? ExecuteResult.Failure
        return when {
            result is ExecuteResult.Success && declared.isEmpty() -> Outcome.Confirmed
            result is ExecuteResult.Success && missing.isEmpty() -> Outcome.Confirmed
            result is ExecuteResult.Success ->
                Outcome.Partial(
                    PartialApply(
                        applied = present,
                        notApplied = missing,
                        note =
                            "action reported success but ${missing.size} of ${declared.size} " +
                                "postconditions are not observable",
                    ),
                )
            present.isNotEmpty() ->
                Outcome.Partial(
                    PartialApply(
                        applied = present,
                        notApplied = missing,
                        note = failure?.reason ?: "apply interrupted",
                    ),
                )
            else ->
                Outcome.Failed(
                    reason = failure?.reason ?: "action did not apply",
                    transient = failure?.transient ?: false,
                )
        }
    }

    /** Typed record for an apply that was cancelled or timed out mid-flight. */
    fun interruptedApply(
        action: ExecutedAction,
        note: String,
    ): Outcome.Partial =
        Outcome.Partial(
            PartialApply(
                applied = emptyList(),
                notApplied = action.expectedPostconditions,
                note = note,
            ),
        )
}
