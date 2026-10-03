/*
 * EQO (TASK-012, issue #17): shared fakes for the action-loop tests.
 */
package ai.eqo.core.agent

import java.util.concurrent.atomic.AtomicInteger

/** Build a [LoopStep] with sensible test defaults. */
@Suppress("LongParameterList") // test fixture: named defaults, one builder
internal fun testStep(
    id: String,
    action: String = "TAP",
    irreversible: Boolean = false,
    postconditions: List<String> = emptyList(),
    plannerClaim: String = "",
    params: Map<String, String> = emptyMap(),
    permission: String? = null,
): LoopStep =
    LoopStep(
        stepId = id,
        action =
            ExecutedAction(
                name = action,
                params = params,
                irreversible = irreversible,
                expectedPostconditions = postconditions,
                plannerClaim = plannerClaim,
            ),
        requiredPermission = permission,
    )

/**
 * Recording executor. `started`/`finished` bracket every apply so tests can
 * assert "no action mid-flight at settle" (started == finished at settle).
 */
internal class RecordingExecutor(
    private val applyDelayMs: Long = 0,
    private val behavior: (LoopStep, Int) -> ExecuteResult = { _, _ -> ExecuteResult.Success("ok") },
) {
    val started = AtomicInteger(0)

    val finished = AtomicInteger(0)

    val calls = mutableListOf<String>()

    val dispatchStates = mutableListOf<LoopState>()

    val dispatchAfterTerminal = AtomicInteger(0)

    suspend fun execute(step: LoopStep): ExecuteResult {
        started.incrementAndGet()
        try {
            synchronized(calls) { calls += step.stepId }
            val attempt = synchronized(calls) { calls.count { it == step.stepId } }
            if (applyDelayMs > 0) kotlinx.coroutines.delay(applyDelayMs)
            return behavior(step, attempt)
        } finally {
            finished.incrementAndGet()
        }
    }
}
