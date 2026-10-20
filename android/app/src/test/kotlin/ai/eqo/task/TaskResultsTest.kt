// Origin: EQO intent v1 owner addendum; real result line plus fake speech and registry chaining.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.study.StepProgress
import ai.eqo.study.StepProgressState
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskResultsTest {
    private val answer = StepProgress("task-1", "CALCULATE", StepProgressState.DONE, detail = "238 * 8 = 1904")

    @After fun clearSession() {
        TaskRunSession.progress.clear()
        TaskRunSession.readAloud = false
        TaskRunSession.spokenResultIds.clear()
        TaskRunSession.receipt = null
    }

    @Test fun completedAnswerIsVisibleInTheActualRunScreenResultLine() {
        clearSession()
        TaskRunSession.progress[answer.stepId] = answer
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val line = lifecycle.get().findViewById<TextView>(R.id.task_result)
        assertEquals(View.VISIBLE, line.visibility)
        assertTrue(line.text.toString().contains("238 * 8 = 1904"))
        assertTrue(line.text.toString().contains("CALCULATE"))
        lifecycle.pause().stop().destroy()
    }

    @Test fun voiceResultsReadOnceTypedAndFailedResultsNeverReadAsAnswers() {
        val spoken = mutableListOf<String>()
        val ids = mutableSetOf<String>()
        val readout = TaskResultReadout(ids, spoken::add)
        readout.update(listOf(answer), voiceRequest = false)
        assertTrue(spoken.isEmpty())
        readout.update(listOf(answer.copy(state = StepProgressState.FAILED)), voiceRequest = true)
        assertTrue(spoken.isEmpty())
        readout.update(listOf(answer), voiceRequest = true)
        readout.update(listOf(answer), voiceRequest = true)
        assertEquals(listOf("238 * 8 = 1904"), spoken)
        TaskResultReadout(ids, spoken::add).update(listOf(answer), voiceRequest = true)
        assertEquals(1, spoken.size) // UI recreation does not repeat a process-local delivered result.
        ids.clear()
        readout.update(listOf(answer), voiceRequest = true)
        assertEquals(2, spoken.size)
    }

    @Test fun calendarHandoffScreenShowsNeedsYouAndThePlainInstructionNotCouldNotFinish() {
        clearSession()
        val step =
            StepProgress(
                "calendar",
                "CREATE_CALENDAR_EVENT",
                StepProgressState.NEEDS_YOU,
                detail = "Calendar editor opened. Review it and tap Save; nothing was saved by EQO.",
            )
        TaskRunSession.progress[step.stepId] = step
        TaskRunSession.receipt = ai.eqo.study.RunReceipt(listOf(step), "COMPLETED")
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        assertEquals("Needs you", activity.findViewById<TextView>(R.id.task_state).text.toString())
        val resultText = activity.findViewById<TextView>(R.id.task_result).text.toString()
        assertTrue(resultText.contains("Calendar editor opened"))
        lifecycle.pause().stop().destroy()
    }

    @Test fun registryResultsChainToTheApprovedPayloadWithoutChangingItsSnapshot() =
        runTest {
            val steps = steps()
            val plan = ApprovedTaskPlan(steps)
            val received = mutableListOf<Map<String, String>>()
            val executor =
                StudyActionExecutor(port(), plan) { name, params ->
                    received += params
                    if (name == "CALCULATE") {
                        ActionResult.Success(mapOf("message" to answer.detail, "result" to "1904"))
                    } else {
                        ActionResult.UserActionRequired("SMS draft opened; user sends it")
                    }
                }
            val first = executor.execute(steps[0]) as ExecuteResult.Success
            assertEquals(answer.detail, first.detail)
            val draft = executor.execute(steps[1]) as ExecuteResult.Success
            assertTrue(draft.handedToOwner) // Only the handoff completed, not sending.
            assertEquals("The answer is 1904", received[1]["message"])
            assertEquals("Example", received[1]["contact"])
            assertTrue(plan.matches(steps))
            assertEquals("The answer is {{step:1:result}}", plan.steps()[1].action.params["message"])
            assertTrue(
                executor.execute(
                    steps[1].copy(
                        action =
                            steps[1].action.copy(
                                params =
                                    mapOf("contact" to "Other", "message" to "1904"),
                            ),
                    ),
                ) is ExecuteResult.Failure,
            )
            assertEquals(2, received.size)
        }

    @Test fun missingOrFailedPriorOutputNeverDispatchesALaterSend() =
        runTest {
            val steps = steps()
            var effects = 0
            val executor =
                StudyActionExecutor(port(), ApprovedTaskPlan(steps)) { _, _ ->
                    effects++
                    ActionResult.Failure("synthetic failure")
                }
            assertTrue(executor.execute(steps[1]) is ExecuteResult.Failure)
            assertEquals(0, effects)
            assertTrue(executor.execute(steps[0]) is ExecuteResult.Failure)
            assertTrue(executor.execute(steps[1]) is ExecuteResult.Failure)
            assertEquals(1, effects)
            assertFalse(
                TaskResultPresentation
                    .lines(listOf(answer.copy(state = StepProgressState.UNKNOWN)))
                    .contains("1904"),
            )
        }

    private fun steps(): List<LoopStep> =
        listOf(
            LoopStep("task-1", ExecutedAction("CALCULATE", mapOf("expression" to "238*8"))),
            LoopStep(
                "task-2",
                ExecutedAction(
                    "SEND_SMS",
                    mapOf(
                        "contact" to "Example",
                        "message" to "The answer is {{step:1:result}}",
                    ),
                ),
            ),
        )

    private fun port(): StudyAutomationPort =
        object : StudyAutomationPort {
            override fun observe() = ""

            override fun tap(text: String) = false

            override fun tapById(viewId: String) = false

            override fun typeText(text: String) = false

            override fun scroll(direction: String) = false

            override fun back() = false

            override fun home() = false

            override fun composeSmsDraft(
                recipient: String,
                body: String,
            ) = false
        }
}
