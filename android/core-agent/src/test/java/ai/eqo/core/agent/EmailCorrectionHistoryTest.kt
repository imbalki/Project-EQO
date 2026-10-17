// Origin: EQO clarification-history regressions; synthetic labels, no network or device.
package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EmailCorrectionHistoryTest {
    private val enabled = setOf("SEND_EMAIL", "SEND_WHATSAPP", "CALCULATE")
    private val whatsapp =
        TaskIntent(
            "Draft a message",
            action = "send_message",
            channel = "whatsapp",
            send = "no",
            confidence = 1.0,
        )

    @Test fun channelCorrectionSurvivesRecipientAndContentOnlySecondAnswer() =
        runTest {
            val complete = whatsapp.copy(params = mapOf("contact" to "Example", "message" to "Supplied"))
            val result =
                prepare(
                    listOf(whatsapp, complete),
                    listOf("Actually use WhatsApp instead", "Example, saying Supplied"),
                )
            assertEquals("whatsapp", result.intent.channel)
            assertEquals("send_message", result.intent.action)
            val action = result.steps.single().action
            assertEquals("SEND_WHATSAPP", action.name)
            assertEquals("Example", action.params["contact"])
            assertEquals("Supplied", action.params["message"])
            assertEquals("true", action.params["draftOnly"])
            assertFalse(result.steps.any { it.action.name == "SEND_EMAIL" })
        }

    @Test fun taskCorrectionSurvivesParameterOnlySecondAnswer() =
        runTest {
            val calculate = TaskIntent("Calculate", action = "calculate", send = "no", confidence = 1.0)
            val result =
                prepare(
                    listOf(calculate, calculate.copy(params = mapOf("expression" to "2+2"))),
                    listOf("Actually calculate instead", "2+2"),
                )
            assertEquals("calculate", result.intent.action)
            assertEquals(null, result.intent.channel)
            val action = result.steps.single().action
            assertEquals("CALCULATE", action.name)
            assertEquals("2+2", action.params["expression"])
            assertFalse(result.steps.any { it.action.name == "SEND_EMAIL" })
        }

    @Test fun laterExplicitCorrectionCanReturnToEmail() =
        runTest {
            val email =
                whatsapp.copy(
                    channel = "email",
                    params = mapOf("to" to "Example", "body" to "Supplied", "attachment" to AttachmentSpec.PICKER),
                )
            val result =
                prepare(
                    listOf(whatsapp, email),
                    listOf("Actually use WhatsApp instead", "Actually draft an email to Example with an attachment"),
                )
            assertEquals("email", result.intent.channel)
            val action = result.steps.single().action
            assertEquals("SEND_EMAIL", action.name)
            assertEquals("true", action.params["draftOnly"])
            assertEquals(AttachmentSpec.PICKER, action.params["attachment"])
        }

    private suspend fun prepare(
        replies: List<TaskIntent>,
        answers: List<String>,
    ): IntentPlanningSession.Result {
        val fake = IntentFixtureProvider(replies.map { Json.encodeToString(it) })
        val planner = TaskPlanner(fake, enabled)
        var rounds = 0
        val result =
            IntentPlanningSession(planner.understanding(emptyList()), planner)
                .prepare("draft an email with an attachment") { answers[rounds++] }!!
        assertEquals(2, rounds)
        assertEquals(2, fake.requests.size)
        assertFalse(result.bestGuess)
        return result
    }
}
