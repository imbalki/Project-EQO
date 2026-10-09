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
    @Test fun bareMessageAsksForChannelExactlyOnce() =
        runTest {
            val send = """{"steps":[{"action":"SEND_WHATSAPP","params":{"contact":"Example","message":"hi"}}]}"""
            val fake = Fake(listOf(send))
            val plan = TaskPlanner(fake, setOf("ASK_USER", "SEND_WHATSAPP", "SEND_SMS")).plan("message Example hi")
            assertEquals(1, fake.requests.size)
            assertEquals("ASK_USER", plan.single().action.name)
            assertTrue(
                plan
                    .single()
                    .action.params
                    .getValue("question")
                    .contains("SMS or WhatsApp"),
            )
        }

    @Test fun draftRequestsCannotGainSendingSteps() =
        runTest {
            val send = """{"steps":[{"action":"SEND_WHATSAPP","params":{"contact":"Example","message":"hi"}}]}"""
            for (request in listOf("type hi on WhatsApp", "write hi", "draft hi", "don't send hi", "only type hi")) {
                val plan = TaskPlanner(Fake(listOf(send)), setOf("SEND_WHATSAPP")).plan(request)
                assertEquals("true", plan.single().action.params["draftOnly"])
                assertTrue(TaskPlanPreview.describe(plan).contains("you press Send"))
            }
            val unsafe = """{"steps":[{"action":"CLICK_TEXT","params":{"text":"Send"}}]}"""
            try {
                TaskPlanner(Fake(listOf(unsafe)), enabled).plan("draft hi, don't send")
                error("Send tap must be rejected")
            } catch (_: IllegalArgumentException) {
            }
            val prompt = RegistryPlanVocabulary.prompt(enabled)
            assertTrue(prompt.contains("Google Keep"))
            assertTrue(prompt.contains("ADD_NOTE is only EQO internal memory"))
            assertTrue(prompt.contains("ASK_USER once for the channel"))
            assertTrue(prompt.contains("Do not ASK_USER for an unambiguous contact"))
        }

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

    @Test fun whatsappCallsAreSingleApprovedIrreversibleStepsWithStrictVideo() {
        val actions = setOf("WHATSAPP_CALL")
        val voice = """{"steps":[{"action":"WHATSAPP_CALL","params":{"contact":"Alice"}}]}"""
        val step = RegistryPlanVocabulary.parse(voice, actions).single()
        assertTrue(step.action.irreversible)
        assertTrue(SensitivityApprovalPolicy.requiresApproval(step.action))
        assertTrue(ActionSchema.isNeverAutoApprove("WHATSAPP_CALL"))
        assertFalse(AutoApprovalPolicy.isGrantable("WHATSAPP_CALL"))
        assertEquals(false, ActionSchema.applyDefaults("WHATSAPP_CALL", mapOf("contact" to "Alice"))["video"])
        val preview = TaskPlanPreview.describe(listOf(step))
        assertTrue(preview.contains("WhatsApp voice call"))
        assertTrue(preview.contains("Alice"))
        assertTrue(preview.contains("rings a real person"))
        val video = voice.replace(""""contact":"Alice"""", """"contact":"Alice","video":"true"""")
        assertTrue(TaskPlanPreview.describe(RegistryPlanVocabulary.parse(video, actions)).contains("video call"))
        assertThrows(IllegalArgumentException::class.java) {
            RegistryPlanVocabulary.parse(video.replace("true", "maybe"), actions)
        }
        val prompt = RegistryPlanVocabulary.prompt(actions)
        assertTrue(prompt.contains("use one WHATSAPP_CALL"))
        assertTrue(prompt.contains("Never auto-retry a call"))
        assertFalse(prompt.contains("then CLICK_TEXT Call"))
    }

    @Test fun whatsappCallIsNeverRetriedAfterAmbiguousFailure() =
        runTest {
            val steps =
                RegistryPlanVocabulary.parse(
                    """{"steps":[{"action":"WHATSAPP_CALL","params":{"contact":"Alice"}}]}""",
                    setOf("WHATSAPP_CALL"),
                )
            var attempts = 0
            var approvals = 0
            val loop =
                ActionLoop(
                    steps = steps,
                    approvalGate = {
                        approvals++
                        ApprovalDecision.Approved
                    },
                    execute = { _ ->
                        attempts++
                        ExecuteResult.Failure("Unknown call state", transient = true)
                    },
                    observe = { "" },
                )
            val result = loop.run()
            assertEquals(1, approvals)
            assertEquals(1, attempts)
            assertEquals(PlanTerminal.FAILED, result.terminal)
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

    @Test fun shareActionsArePlannedAndPreviewedInPlainWords() {
        val shared = setOf("SHARE_CONTACT", "SHARE_LOCATION")
        val prompt = RegistryPlanVocabulary.prompt(shared)
        assertTrue(prompt.contains("SHARE_CONTACT:") && prompt.contains("SHARE_LOCATION:"))
        val plan =
            """{"steps":[{"action":"SHARE_CONTACT","params":{"contact":"Alex","to":"Sam","via":"whatsapp"}},""" +
                """{"action":"SHARE_LOCATION","params":{"to":"Sam","via":"sms"}}]}"""
        val steps = RegistryPlanVocabulary.parse(plan, shared)
        val preview = TaskPlanPreview.describe(steps)
        val contactLine = "send the saved phone number of \"Alex\" to \"Sam\" in a WhatsApp message (EQO presses Send)"
        val locationLine =
            "send your current location (a Google Maps link) to \"Sam\" in a text-message draft; you send it"
        assertTrue(preview, preview.contains(contactLine))
        assertTrue(preview, preview.contains(locationLine))
        assertThrows(IllegalArgumentException::class.java) {
            RegistryPlanVocabulary.parse(plan.replace("\"via\":\"sms\"", "\"via\":\"fax\""), shared)
        }
    }
}
