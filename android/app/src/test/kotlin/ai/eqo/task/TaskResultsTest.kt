// Origin: EQO intent v1 owner addendum; real result line plus fake speech and registry chaining.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.study.RunReceipt
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
        TaskRunSession.runSteps = emptyList()
    }

    @Test fun theFinishedRunShowsOnePlainSentenceOnTheActualRunScreenResultLine() {
        clearSession()
        TaskRunSession.runSteps = steps().take(1)
        TaskRunSession.progress[answer.stepId] = answer
        TaskRunSession.receipt = RunReceipt(listOf(answer), "COMPLETED")
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val line = lifecycle.get().findViewById<TextView>(R.id.task_result)
        assertEquals(View.VISIBLE, line.visibility)
        assertEquals("Result: 238 times 8 is 1904.", line.text.toString())
        assertFalse(line.text.toString().contains("CALCULATE"))
        lifecycle.pause().stop().destroy()
    }

    @Test fun aRunStillInProgressHasNoResultLineYet() {
        clearSession()
        TaskRunSession.runSteps = steps().take(1)
        TaskRunSession.progress[answer.stepId] = answer
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val line = lifecycle.get().findViewById<TextView>(R.id.task_result)
        assertEquals(View.GONE, line.visibility)
        lifecycle.pause().stop().destroy()
    }

    @Test fun aHandoffRunEndsAsNeedsYouWithThePlainInstructionAndNeverAsFailed() {
        clearSession()
        val steps = steps()
        val sms =
            StepProgress("task-2", "SEND_SMS", StepProgressState.NEEDS_YOU, detail = "SMS draft opened; user sends it")
        TaskRunSession.runSteps = steps
        TaskRunSession.status = ai.eqo.data.models.PlanStatus.FAILED
        TaskRunSession.progress[answer.stepId] = answer
        TaskRunSession.progress[sms.stepId] = sms
        TaskRunSession.receipt = RunReceipt(listOf(answer, sms), "FAILED")
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        val state = activity.findViewById<TextView>(R.id.task_state).text.toString()
        assertEquals(activity.getString(R.string.task_needs_you), state)
        assertEquals(
            "Result: 238 times 8 is 1904. Text message ready; press Send.",
            activity.findViewById<TextView>(R.id.task_result).text.toString(),
        )
        lifecycle.pause().stop().destroy()
        TaskRunSession.status = ai.eqo.data.models.PlanStatus.PENDING
    }

    @Test fun theSummaryIsSpokenOncePerRunOnlyForVoiceRequests() {
        val spoken = mutableListOf<String>()
        val ids = mutableSetOf<String>()
        val readout = TaskResultReadout(ids, spoken::add)
        val sentence = "238 times 8 is 1904."
        readout.update(sentence, voiceRequest = false)
        assertTrue(spoken.isEmpty())
        readout.update(null, voiceRequest = true)
        readout.update(" ", voiceRequest = true)
        assertTrue(spoken.isEmpty())
        readout.update(sentence, voiceRequest = true)
        readout.update(sentence, voiceRequest = true)
        assertEquals(listOf(sentence), spoken)
        TaskResultReadout(ids, spoken::add).update(sentence, voiceRequest = true)
        assertEquals(1, spoken.size) // UI recreation does not repeat a process-local delivered result.
        ids.clear()
        readout.update(sentence, voiceRequest = true)
        assertEquals(2, spoken.size)
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
            assertTrue(executor.execute(steps[1]) is ExecuteResult.Failure) // Draft handoff stays honest.
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
            val unknown = RunSummary.steps(steps, listOf(answer.copy(state = StepProgressState.UNKNOWN)))
            val sentences = RunSummary.sentences(unknown, "FAILED")
            assertEquals(listOf(RunSentence(R.string.run_say_unknown)), sentences)
            val ordinary = executor.execute(steps[1]) as ExecuteResult.Failure
            assertFalse(ordinary.handoff) // A plain failure is not a hand-off.
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
