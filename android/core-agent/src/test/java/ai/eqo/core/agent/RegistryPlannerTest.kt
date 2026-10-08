// Origin: EQO TASK-077 (#20), enabled vocabulary and tolerant planner tests.
package ai.eqo.core.agent

import ai.eqo.core.llm.LLMProvider
import ai.eqo.core.llm.LLMRequest
import ai.eqo.core.llm.LLMResponse
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryPlannerTest {
    private val enabled = setOf("OPEN_APP", "TYPE_TEXT", "CLICK_TEXT")
    private val valid = """{"steps":[{"action":"OPEN_APP","params":{"appName":"gmail"}}]}"""

    @Test fun wrappersAndMinorSyntaxKeepSchema() {
        listOf(
            valid,
            "```json\n$valid\n```",
            "Here is the plan: $valid trailing prose",
            valid.replace('"', '\''),
            valid.replace("}}]", "},}]"),
        ).forEach {
            assertEquals(
                "OPEN_APP",
                RegistryPlanVocabulary
                    .parse(it, enabled)
                    .single()
                    .action.name,
            )
        }
    }

    @Test fun unknownVerbAndParamStayRejected() {
        listOf(valid.replace("OPEN_APP", "PAY"), valid.replace("appName", "app"), valid.replace("gmail", "")).forEach {
            assertThrows(IllegalArgumentException::class.java) { RegistryPlanVocabulary.parse(it, enabled) }
        }
        val prompt = RegistryPlanVocabulary.prompt(enabled)
        assertTrue(prompt.contains("TYPE_TEXT"))
        assertTrue(prompt.contains("searchText"))
        assertTrue(prompt.contains("saved contact name or a phone number"))
        assertTrue(prompt.contains("SEND_EMAIL to may be a name or email"))
        assertTrue(prompt.contains("Never invent recipients or addresses"))
        assertTrue(prompt.contains("Telegram usernames must start with @"))
        assertFalse(prompt.contains("MAKE_CALL:"))
    }

    @Test fun oneRepairUsesValidatorErrorsAndOriginalRequest() =
        runTest {
            val fake = Fake(listOf(valid.replace("appName", "app"), "```json\n$valid\n```"))
            val result = TaskPlanner(fake, enabled).plan("open gmail")
            assertEquals(2, fake.requests.size)
            assertTrue(
                fake.requests
                    .last()
                    .systemPrompt
                    .contains("rejected parameter app"),
            )
            assertEquals("OPEN_APP", result.single().action.name)
        }

    @Test fun secondInvalidProposalDoesNotRetryAgain() =
        runTest {
            val fake = Fake(listOf(valid.replace("OPEN_APP", "PAY"), valid.replace("OPEN_APP", "PAY")))
            try {
                TaskPlanner(fake, enabled).plan("open gmail")
                error("Expected rejection")
            } catch (_: IllegalArgumentException) {
            }
            assertEquals(2, fake.requests.size)
        }

    @Test fun approvedRegistrySnapshotRejectsChangedBytes() {
        val steps = RegistryPlanVocabulary.parse(valid, enabled)
        val plan = ApprovedTaskPlan(steps)
        assertTrue(plan.permits(steps.single()))
        val changed = steps.single().copy(action = steps.single().action.copy(params = mapOf("appName" to "phone")))
        assertFalse(plan.permits(changed))
        assertTrue(plan.matches(steps))
        assertFalse(plan.matches(listOf(changed)))
    }

    private class Fake(
        private val outputs: List<String>,
    ) : LLMProvider {
        val requests = mutableListOf<LLMRequest>()
        override val name = "fake"
        override val availableModels = emptyList<String>()

        override suspend fun complete(request: LLMRequest): LLMResponse {
            requests += request
            return LLMResponse(outputs[requests.lastIndex], 0, "fake", name, 0)
        }

        override fun streamComplete(request: LLMRequest) = emptyFlow<String>()

        override suspend fun isAvailable() = true
    }
}
