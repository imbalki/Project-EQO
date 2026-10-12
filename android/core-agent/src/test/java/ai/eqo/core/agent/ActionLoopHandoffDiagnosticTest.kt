package ai.eqo.core.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A draft handed to the owner is still not a success (nothing runs after it, nothing is verified), but the
 * diagnostics must not call it an execution failure.
 */
class ActionLoopHandoffDiagnosticTest {
    private fun run(result: ExecuteResult): Pair<LoopReport, List<String>> {
        val lines = mutableListOf<String>()
        val report =
            runBlocking {
                ActionLoop(
                    steps = listOf(testStep("1", action = "SEND_SMS"), testStep("2", action = "WAIT")),
                    approvalGate = { ApprovalDecision.Approved },
                    execute = { result },
                    observe = { "" },
                    config = ActionLoop.Config(interStepDelayMs = 0),
                    onDiagnostic = { lines += it },
                ).run()
            }
        return report to lines
    }

    @Test
    fun `a handed-off step is labelled needs_user_action and still stops the plan`() {
        val (report, lines) = run(ExecuteResult.Failure("SMS draft opened; nothing was sent", handoff = true))
        assertTrue(lines.any { it.contains("execute=Failure code=needs_user_action") })
        assertFalse(lines.any { it.contains("code=execution_failed") })
        // The state machine is unchanged: not a success, nothing after it runs.
        assertEquals(PlanTerminal.FAILED, report.terminal)
        assertTrue(report.steps[0].outcome is StepOutcome.Failed)
        assertTrue(report.steps[1].outcome is StepOutcome.NotExecuted)
    }

    @Test
    fun `an ordinary failure keeps its execution_failed code`() {
        val (_, lines) = run(ExecuteResult.Failure("boom"))
        assertTrue(lines.any { it.contains("execute=Failure code=execution_failed") })
    }
}
