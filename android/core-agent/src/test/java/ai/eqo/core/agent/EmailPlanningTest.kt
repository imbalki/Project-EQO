// Origin: EQO native email regression tests; synthetic contact labels, no network or device.
package ai.eqo.core.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailPlanningTest {
    private val enabled = setOf("SEND_EMAIL", "OPEN_APP", "WAIT", "CLICK_TEXT", "TYPE_TEXT")

    @Test fun attachmentPhrasingsUseNativeEmailForSendAndDraftWithoutModelPlanning() =
        runTest {
            val cases =
                mapOf(
                    "with an attachment" to AttachmentSpec.PICKER,
                    "with an attachment, choose the file myself" to AttachmentSpec.PICKER,
                    "with the file called report.pdf" to "find:report.pdf",
                    "attach a photo" to "find:type=photo",
                    "with attachment: find:report.pdf" to "find:report.pdf",
                )
            for (verb in listOf("send", "draft")) {
                for ((suffix, attachment) in cases) {
                    val fake = IntentFixtureProvider(emptyList())
                    val planner = TaskPlanner(fake, enabled)
                    val result =
                        IntentPlanningSession(planner.understanding(listOf("Gmail")), planner)
                            .prepare("$verb an email to Example $suffix") { error("No clarification needed") }!!
                    val action = result.steps.single().action
                    assertEquals("email", result.intent.channel)
                    assertEquals(if (verb == "send") "yes" else "no", result.intent.send)
                    assertEquals("SEND_EMAIL", action.name)
                    assertEquals("Example", action.params["to"])
                    assertEquals("", action.params["subject"])
                    assertEquals("", action.params["body"])
                    assertEquals(attachment, action.params["attachment"])
                    assertEquals((verb == "draft").toString(), action.params["draftOnly"])
                    assertFalse(result.bestGuess)
                    assertTrue(fake.requests.isEmpty())
                    assertTrue(TaskPlanPreview.describe(result.steps).contains("attach"))
                }
            }
        }

    @Test fun suppliedSubjectBodyAndQuotedSendWordsStayExact() =
        runTest {
            val request =
                "send an email to Example with subject \"Report\" body \"don't send on WhatsApp\" " +
                    "with the file called report.pdf"
            val intent = IntentUnderstanding(IntentFixtureProvider(emptyList()), emptyList()).understand(request)
            val action = IntentSkills.plan(intent, enabled)!!.single().action
            assertEquals("Report", action.params["subject"])
            assertEquals("don't send on WhatsApp", action.params["body"])
            assertEquals("false", action.params["draftOnly"])
            assertEquals("find:report.pdf", action.params["attachment"])
        }

    @Test fun explicitDoNotSendAndUnknownSendAlwaysDraft() =
        runTest {
            val intent =
                IntentUnderstanding(IntentFixtureProvider(emptyList()), emptyList())
                    .understand("send an email to Example with an attachment but do not send")
            assertEquals("no", intent.send)
            for (send in listOf("no", "unknown")) {
                val steps = IntentSkills.plan(intent.copy(send = send), enabled)!!
                assertEquals("true", steps.single().action.params["draftOnly"])
                assertEquals(steps, IntentPlanProtection.validate(intent.copy(send = send), steps))
                assertTrue(TaskPlanPreview.describe(steps).contains("you press Send"))
            }
        }

    @Test fun modelEmailSlotsAndLegacyContactMessageBothUseNativeTemplate() =
        runTest {
            for (params in listOf(
                mapOf("to" to "Example", "body" to "Supplied", "subject" to "Report"),
                mapOf("contact" to "Example", "message" to "Supplied", "subject" to "Report"),
            )) {
                val intent =
                    TaskIntent(
                        "Email",
                        action = "send_message",
                        channel = "email",
                        send = "yes",
                        params = params + ("attachment" to "find:report"),
                        confidence = 1.0,
                    )
                val fake = IntentFixtureProvider(listOf(Json.encodeToString(intent)))
                val planner = TaskPlanner(fake, enabled)
                val result =
                    IntentPlanningSession(planner.understanding(emptyList()), planner)
                        .prepare("email the report to Example") { error("Complete email") }!!
                assertEquals(1, fake.requests.size)
                assertEquals(
                    "SEND_EMAIL",
                    result.steps
                        .single()
                        .action.name,
                )
                assertEquals(
                    "Supplied",
                    result.steps
                        .single()
                        .action.params["body"],
                )
            }
        }

    @Test fun clarificationIsShortStopsAtTwoAndDisclosesGuess() =
        runTest {
            val unsure =
                TaskIntent(
                    "Email",
                    action = "send_message",
                    channel = "email",
                    send = "no",
                    params = mapOf("to" to "Example", "attachment" to AttachmentSpec.PICKER),
                    confidence = 0.2,
                    question = "A long model question we should not show",
                )
            val fake = IntentFixtureProvider(List(3) { Json.encodeToString(unsure) })
            val planner = TaskPlanner(fake, enabled)
            var rounds = 0
            val result =
                IntentPlanningSession(planner.understanding(emptyList()), planner)
                    .prepare("email a file to Example") {
                        assertEquals("What should I change?", it.clarificationQuestion())
                        rounds++
                        "choose the file myself"
                    }!!
            assertEquals(2, rounds)
            assertEquals(3, fake.requests.size)
            assertTrue(result.bestGuess)
            assertEquals(
                "SEND_EMAIL",
                result.steps
                    .single()
                    .action.name,
            )
        }

    @Test fun missingRecipientNeverGuessesOrFallsBackToGmailUiAfterTwoAnswers() =
        runTest {
            val unsure =
                TaskIntent(
                    "Email",
                    action = "send_message",
                    channel = "email",
                    params = mapOf("attachment" to AttachmentSpec.PICKER),
                    confidence = 1.0,
                )
            val fake = IntentFixtureProvider(List(2) { Json.encodeToString(unsure) })
            val planner = TaskPlanner(fake, enabled)
            var rounds = 0
            val result =
                IntentPlanningSession(planner.understanding(emptyList()), planner)
                    .prepare("send an email with an attachment") {
                        assertEquals("Who is the email for?", it.clarificationQuestion())
                        rounds++
                        "not sure"
                    }!!
            assertEquals(2, rounds)
            assertTrue(result.bestGuess)
            assertTrue(result.steps.isEmpty())
            assertTrue(result.notes.single().contains("recipient"))
        }

    @Test fun uiChainAndMissingDraftFlagAreRejected() {
        val intent = TaskIntent("Email", action = "send_message", channel = "email", send = "no")
        assertThrows(IllegalArgumentException::class.java) {
            IntentPlanProtection.validate(intent, listOf(LoopStep("task-1", ExecutedAction("OPEN_APP"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IntentPlanProtection.validate(intent, listOf(LoopStep("task-1", ExecutedAction("SEND_EMAIL"))))
        }
    }

    @Test fun multipleActionsAndUnquotedFreeFormContentUseModelUnderstanding() {
        assertNull(EmailIntents.from("send an email to Example then call Other"))
        assertNull(EmailIntents.from("send an email to Example with the file called report.pdf and email Other too"))
        assertNull(
            EmailIntents.from("send an email to Example with the file called report.pdf and delete the original"),
        )
        assertNull(EmailIntents.from("send an email to Example with the file called report.pdf and upload it publicly"))
        assertNull(EmailIntents.from("send an email to Example with subject Report body Please read"))
    }

    @Test fun modelCannotDropAttachmentFromFreeFormEmailOrDowngradeToUiPlanning() =
        runTest {
            val intent =
                TaskIntent(
                    "Email",
                    action = "other",
                    params =
                        mapOf(
                            "to" to "Example",
                            "subject" to "Report",
                            "body" to "Supplied",
                        ),
                    send = "yes",
                    confidence = 1.0,
                )
            val fake = IntentFixtureProvider(listOf(Json.encodeToString(intent)))
            val planner = TaskPlanner(fake, enabled)
            val request =
                "send an email to Example with subject Report body Supplied " +
                    "with the file called report.pdf"
            val result =
                IntentPlanningSession(planner.understanding(emptyList()), planner)
                    .prepare(request) {
                        error("Complete email")
                    }!!
            assertEquals(
                "SEND_EMAIL",
                result.steps
                    .single()
                    .action.name,
            )
            assertEquals(
                "find:report.pdf",
                result.steps
                    .single()
                    .action.params["attachment"],
            )
            assertEquals(1, fake.requests.size)
        }

    @Test fun recipientOnlyClarificationCannotLoseOriginalAttachmentSubjectOrBody() =
        runTest {
            val updated = TaskIntent("Email", action = "other", params = mapOf("to" to "Example"), confidence = 1.0)
            val fake = IntentFixtureProvider(listOf(Json.encodeToString(updated)))
            val planner = TaskPlanner(fake, enabled)
            val result =
                IntentPlanningSession(planner.understanding(emptyList()), planner)
                    .prepare("draft an email with subject \"Report\" body \"Supplied\" with an attachment") {
                        "Example"
                    }!!
            val params =
                result.steps
                    .single()
                    .action.params
            assertEquals("Example", params["to"])
            assertEquals("Report", params["subject"])
            assertEquals("Supplied", params["body"])
            assertEquals(AttachmentSpec.PICKER, params["attachment"])
            assertEquals("true", params["draftOnly"])
        }

    @Test fun explicitCorrectionsCanChangeChannelSendAndLegacySlots() =
        runTest {
            val previous =
                TaskIntent(
                    "Email",
                    action = "send_message",
                    channel = "email",
                    send = "no",
                    params = mapOf("to" to "Original", "body" to "Original", "attachment" to AttachmentSpec.PICKER),
                )
            val corrected = previous.copy(params = mapOf("contact" to "Example", "message" to "Updated"), send = "yes")
            val merged = EmailIntents.merge(previous, corrected, "send it")
            assertEquals("Example", merged.params["to"])
            assertEquals("Updated", merged.params["body"])
            assertEquals(AttachmentSpec.PICKER, merged.params["attachment"])
            val enriched = "draft an email with an attachment\nUser clarification 1: send it to Example"
            assertEquals("yes", EmailIntents.normalize(enriched, merged).send)
            val sms = corrected.copy(channel = "sms")
            assertEquals(sms, EmailIntents.normalize(enriched.replace("send it", "text instead"), sms))
            assertEquals(sms, EmailIntents.merge(previous, sms, "text instead"))
            assertEquals("no", EmailIntents.merge(previous, corrected, "Example").send)
        }
}
