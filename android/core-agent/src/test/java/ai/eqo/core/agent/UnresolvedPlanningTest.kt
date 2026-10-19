// Origin: EQO unresolved-plan regressions; no network or personal destinations.
package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnresolvedPlanningTest {
    private val enabled = ActionSchema.ALL_ACTIONS.map { it.name }.toSet()

    @Test fun emailQuestionIsNotRejectedAsAnEmailUiChain() =
        runTest {
            val question = listOf(LoopStep("ask", ExecutedAction("ASK_USER", mapOf("question" to "Which recipient?"))))
            val intent = TaskIntent("Email", action = "send_message", channel = "email", send = "no")
            assertEquals(question, IntentPlanProtection.validate(intent, question))
            assertNotNull(RegistryPlanVocabulary.clarification(question))
        }

    @Test fun literalAddressWithOrWithoutContactBecomesEmailDestination() =
        runTest {
            val address = "synthetic" + '@' + "example.invalid"
            for (recipient in listOf(address, "Unknown <$address>")) {
                val intent = EmailIntents.from("send an email to $recipient")!!
                val plan = TaskPlanner(IntentFixtureProvider(emptyList()), enabled).plan("email", intent = intent)
                assertEquals(address, plan.single().action.params["to"])
                assertEquals("SEND_EMAIL", plan.single().action.name)
            }
        }

    @Test fun malformedLiteralIsNeverRewrittenToAnotherDestination() {
        val address = "synthetic" + '@' + "example.invalid"
        for (supplied in listOf("wrong@@$address", "$address@extra")) {
            assertEquals(supplied, EmailIntents.from("send an email to $supplied")!!.params["to"])
        }
    }

    @Test fun malformedUnderstandingSharesTheTwoQuestionBudget() =
        runTest {
            val fake = IntentFixtureProvider(List(5) { "{}" })
            var questions = 0
            val result =
                IntentPlanningSession(IntentUnderstanding(fake, emptyList()), TaskPlanner(fake, enabled))
                    .prepare("do the synthetic task") {
                        questions++
                        "Use the synthetic tool"
                    }!!
            assertEquals(2, questions)
            assertTrue(result.bestGuess)
            assertTrue(result.steps.isEmpty())
            assertTrue(result.notes.isNotEmpty())
        }

    @Test fun unresolvedContactStillDisclosesSafeBestGuessAfterSharedBudget() =
        runTest {
            val missing = TaskIntent("Call a contact", action = "call", confidence = 1.0)
            val fake = IntentFixtureProvider(List(3) { Json.encodeToString(missing) })
            var questions = 0
            val result =
                IntentPlanningSession(IntentUnderstanding(fake, emptyList()), TaskPlanner(fake, enabled))
                    .prepare("call a contact") {
                        questions++
                        "I do not know"
                    }!!
            assertEquals(IntentPlanningSession.MAX_ROUNDS, questions)
            assertEquals(questions, result.clarificationRounds)
            assertTrue(result.bestGuess)
            assertTrue(result.steps.isEmpty())
            assertTrue(result.notes.single().contains("nothing will run"))
        }

    @Test fun repeatedInvalidPlansAskAtMostTwiceThenDiscloseSafeBestGuess() =
        runTest {
            val understood = Json.encodeToString(TaskIntent("Do a synthetic task", confidence = 1.0))
            val fake = IntentFixtureProvider(List(3) { listOf(understood, "{}", "{}") }.flatten())
            var questions = 0
            val result =
                IntentPlanningSession(IntentUnderstanding(fake, emptyList()), TaskPlanner(fake, enabled))
                    .prepare("do the synthetic task") {
                        questions++
                        "Use the synthetic tool"
                    }!!
            assertEquals(2, questions)
            assertEquals(2, result.clarificationRounds)
            assertTrue(result.bestGuess)
            assertTrue(result.steps.isEmpty())
            assertTrue(result.notes.single().contains("nothing will run"))
        }
}
