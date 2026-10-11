// Origin: EQO intent v1 owner addendum; local arithmetic and data-reference boundaries, no network.
package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanArithmeticOutputsTest {
    private val calculate = LoopStep("task-1", ExecutedAction("CALCULATE", mapOf("expression" to "238 times 8")))
    private val send =
        LoopStep(
            "task-2",
            ExecutedAction(
                "SEND_SMS",
                mapOf("contact" to "Example", "message" to "Answer: {{step:1:result}}"),
            ),
        )

    @Test fun plainWordsPercentAndDecimalMathHaveLiteralPreviewAnswers() {
        val cases =
            mapOf(
                "238 times 8" to "1904",
                "238 x 8" to "1904",
                "238 × 8" to "1904",
                "7 plus 3" to "10",
                "7 minus 3" to "4",
                "12 divided by 4" to "3",
                "10 percent of 250" to "25",
                "10 % of 250" to "25",
                "0.1 + 0.2" to "0.3",
                "-2 * 3" to "-6",
            )
        cases.forEach { (expression, result) ->
            assertEquals(expression, result, PlainArithmetic.evaluate(expression)?.result)
        }
        assertTrue(TaskPlanPreview.describe(listOf(calculate)).contains("= 1904 (local preview answer)"))
        assertEquals("1904", PlainArithmetic.fromRequest("calculate 238 times 8 and let me know")!!.result)
        assertEquals("1904", PlainArithmetic.fromRequest("238 times 8 calculate and let me know")!!.result)
    }

    @Test fun arithmeticIsBoundedAndNeverStripsAnAdditionalSendInstruction() {
        for (input in listOf("1/0", "1+2+3", "NaN", "1e999999999", "foo", "9".repeat(257))) {
            assertNull(input, PlainArithmetic.evaluate(input))
        }
        assertNull(PlainArithmetic.fromRequest("calculate 238 times 8 and send it to Example"))
        assertNull(PlainArithmetic.fromRequest("238 times 8 and open Gmail"))
    }

    @Test fun plainArithmeticSkipsNetworkButCompoundRequestsStillNeedUnderstanding() =
        runTest {
            val fake = IntentFixtureProvider(emptyList())
            val intent = IntentUnderstanding(fake, emptyList()).understand("calculate 238 times 8 and let me know")
            assertEquals("calculate", intent.action)
            assertEquals("238*8", intent.params["expression"])
            assertTrue(fake.requests.isEmpty())
        }

    @Test fun payloadReferencesResolveOnlySuccessfulRecordedBackwardResults() {
        val plan = ApprovedTaskPlan(listOf(calculate, send))
        val outputs = ApprovedPlanOutputs(plan.steps())
        assertThrows(IllegalStateException::class.java) { outputs.resolve(send) }
        outputs.record("task-1", mapOf("result" to "1904", "message" to "238 times 8 = 1904"))
        assertEquals("Answer: 1904", outputs.resolve(send)["message"])
        assertEquals("Example", outputs.resolve(send)["contact"])
        assertEquals("Answer: {{step:1:result}}", plan.steps()[1].action.params["message"])
        assertTrue(plan.matches(listOf(calculate, send)))
        assertTrue(TaskPlanPreview.describe(plan.steps()).contains("result of step 1"))
    }

    @Test fun forwardMalformedRecipientAndControlReferencesFailBeforeApproval() {
        val bad =
            listOf(
                send.copy(action = send.action.copy(params = send.action.params + ("message" to "{{step:2:result}}"))),
                send.copy(action = send.action.copy(params = send.action.params + ("message" to "{{step:x:result}}"))),
                send.copy(action = send.action.copy(params = send.action.params + ("contact" to "{{step:1:result}}"))),
                LoopStep("task-2", ExecutedAction("CLICK_TEXT", mapOf("text" to "{{step:1:message}}"))),
            )
        bad.forEach { step ->
            assertFalse(PlanOutputReferences.errors(listOf(calculate, step)).isEmpty())
            assertThrows(IllegalArgumentException::class.java) { ApprovedTaskPlan(listOf(calculate, step)) }
        }
    }

    @Test fun outputTextIsNotRecursivelySubstitutedAndCannotLeakAcrossRuns() {
        val outputs = ApprovedPlanOutputs(listOf(calculate, send))
        outputs.record("task-1", mapOf("result" to "{{step:2:result}}"))
        assertEquals("Answer: {{step:2:result}}", outputs.resolve(send)["message"])
        val nextRun = ApprovedPlanOutputs(listOf(calculate, send))
        assertThrows(IllegalStateException::class.java) { nextRun.resolve(send) }
        outputs.record("task-1", mapOf("result" to "x".repeat(8001)))
        assertThrows(IllegalArgumentException::class.java) { outputs.resolve(send) }
    }

    @Test fun calculateThenSendLiteralAnswersRemainUnchangedAndFreePlannerGetsLocalMath() =
        runTest {
            val json = """{"steps":[{"action":"CALCULATE","params":{"expression":"238 times 8"}},
                {"action":"SEND_WHATSAPP","params":{"contact":"Example","message":"1904","draftOnly":"false"}}]}"""
            val fake = IntentFixtureProvider(listOf(json))
            val intent =
                TaskIntent(
                    "Calculate then send",
                    action = "other",
                    params = mapOf("expression" to "238 times 8"),
                    channel = "whatsapp",
                    send = "yes",
                    confidence = 0.95,
                )
            val steps =
                TaskPlanner(fake, setOf("CALCULATE", "SEND_WHATSAPP")).plan(
                    "calculate 238 times 8 and send the answer to Example on WhatsApp",
                    intent = intent,
                )
            assertEquals("1904", steps[1].action.params["message"])
            assertTrue(
                fake.requests
                    .single()
                    .messages
                    .single()
                    .text
                    .contains("\"result\":\"1904\""),
            )
            assertTrue(ApprovedTaskPlan(steps).matches(steps))
        }
}
