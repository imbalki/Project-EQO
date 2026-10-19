// Origin: EQO attachment recovery; fake questions, no files or note contents read.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.actions.impl.RecipientPreparationException
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.IntentPlanningSession
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.TaskIntent
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AttachmentRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun step(reference: String) =
        listOf(
            LoopStep(
                "email",
                ExecutedAction(
                    "SEND_EMAIL",
                    mapOf(
                        "to" to "Synthetic",
                        "subject" to "",
                        "body" to "Original",
                        "attachment" to reference,
                    ),
                ),
            ),
        )

    @Test fun missingAttachmentOffersChoiceBeforeUsingExistingPicker(): Unit =
        runBlocking {
            var asked = 0
            val result =
                TaskAttachmentRecovery(context) {
                    asked++
                    assertTrue(it.options.contains(context.getString(R.string.attachment_add_text)))
                    context.getString(R.string.attachment_choose_file)
                }.prepare(step("find:missing invoice"), setOf("find:missing invoice"), "email a file")
            assertEquals(1, asked)
            assertEquals("picker", result.single().action.params["attachment"])
            assertEquals(result, ApprovedTaskPlan(result).steps())
        }

    @Test fun keepNoteExplainsLimitationAndAppendsOwnerTextOnly(): Unit =
        runBlocking {
            val answers = ArrayDeque(listOf(context.getString(R.string.attachment_add_text), "Pasted note"))
            val questions = mutableListOf<String>()
            val result =
                TaskAttachmentRecovery(context) {
                    questions += it.question.orEmpty()
                    answers.removeFirst()
                }.prepare(step("picker"), emptySet(), "send email and attach my Keep note")
            assertTrue(questions.first().contains("Keep notes are not files"))
            assertFalse(
                result
                    .single()
                    .action.params
                    .containsKey("attachment"),
            )
            assertEquals("Original\n\nPasted note", result.single().action.params["body"])
            assertEquals(result, ApprovedTaskPlan(result).steps())
        }

    @Test fun recoveringOneMissingFilePreservesEveryOtherAttachment(): Unit =
        runBlocking {
            val original = step("/Download/existing.pdf|find:missing")
            val file =
                TaskAttachmentRecovery(context) { context.getString(R.string.attachment_choose_file) }
                    .prepare(original, setOf("find:missing"), "email files")
            assertEquals("/Download/existing.pdf|picker", file.single().action.params["attachment"])
            val answers = ArrayDeque(listOf(context.getString(R.string.attachment_add_text), "Pasted"))
            val body =
                TaskAttachmentRecovery(context) { answers.removeFirst() }
                    .prepare(original, setOf("find:missing"), "email files")
            assertEquals("/Download/existing.pdf", body.single().action.params["attachment"])
        }

    @Test fun omittedKeepAttachmentStillAsksBeforeApproval(): Unit =
        runBlocking {
            val original =
                step("picker").map { step ->
                    step.copy(action = step.action.copy(params = step.action.params - "attachment"))
                }
            val result =
                TaskAttachmentRecovery(context) { context.getString(R.string.attachment_choose_file) }
                    .prepare(original, emptySet(), "send an email and attach my Keep note")
            assertEquals("picker", result.single().action.params["attachment"])
        }

    @Test fun availableAttachmentAndExplicitPickerAreNotChanged(): Unit =
        runBlocking {
            for (reference in listOf("find:invoice", "picker", "last_screenshot")) {
                val steps = step(reference)
                assertEquals(
                    steps,
                    TaskAttachmentRecovery(context) { error("No question needed") }
                        .prepare(steps, emptySet(), "email file"),
                )
            }
        }

    @Test fun keepFilenameAndUnrelatedKeepInstructionDoNotReplaceAvailableFile(): Unit =
        runBlocking {
            for (reference in listOf("/Download/keep.pdf", "/Download/Keep notes.pdf", "find:Keep note.pdf")) {
                val original = step(reference)
                for (request in listOf("email file", "email and attach my Keep note")) {
                    assertEquals(
                        original,
                        TaskAttachmentRecovery(context) { error("File is available") }
                            .prepare(original, emptySet(), request),
                    )
                }
            }
        }

    @Test fun quotedPayloadDoesNotBecomeAnAttachmentInstruction(): Unit =
        runBlocking {
            val original = step("")
            val requests =
                listOf(
                    "send an email to Known body \"Please attach my Keep note\"",
                    "send an email subject “attach my Keep note”",
                    "send an email body 'attach my Keep note'",
                    "send an email body \"He said \\\"attach my Keep note\\\" today\"",
                )
            for (request in requests) {
                assertEquals(
                    original,
                    TaskAttachmentRecovery(context) { error("Payload is not a command") }
                        .prepare(original, emptySet(), request),
                )
            }
        }

    @Test fun missingVirtualKeepReferenceExplainsLimitationAfterAvailabilityProbe(): Unit =
        runBlocking {
            val original = step("find:Keep note")
            val recovery =
                TaskAttachmentRecovery(context) {
                    assertTrue(it.question!!.contains("Keep notes are not files"))
                    context.getString(R.string.attachment_choose_file)
                }
            val first = recovery.prepare(original, emptySet(), "email file")
            assertEquals(original, first)
            val result = recovery.prepare(first, setOf("find:Keep note"), "")
            assertEquals("picker", result.single().action.params["attachment"])
        }

    @Test fun cancelledChoiceDoesNotProducePlan(): Unit =
        runBlocking {
            try {
                TaskAttachmentRecovery(context) { null }
                    .prepare(step("find:missing"), setOf("find:missing"), "email file")
                error("Must not proceed")
            } catch (expected: RecipientPreparationException) {
                assertTrue(expected.message!!.contains("Nothing ran"))
            }
        }

    @Test fun finalClarificationDoesNotReviveOriginalAttachmentOrInferFromBody(): Unit =
        runBlocking {
            val request = "send an email and attach my Keep note"
            val original = step("")
            val final =
                IntentPlanningSession.Result(
                    TaskIntent(
                        goal = "Email saying attach my Keep note",
                        params = mapOf("body" to "Please attach my Keep note"),
                    ),
                    original,
                    bestGuess = false,
                    clarifiedRequest = "$request\nUser clarification 1: No attachment, put that in the body",
                    clarificationRounds = 1,
                )
            assertEquals(
                original,
                TaskAttachmentRecovery(context) { error("Final intent has no attachment") }
                    .prepare(original, emptySet(), TaskAttachmentRecovery.requestFor(request, final)),
            )
            assertEquals(
                "",
                TaskAttachmentRecovery.requestFor(final.clarifiedRequest, final.copy(clarificationRounds = 0)),
            )
            val confirmed = final.copy(intent = final.intent.copy(params = mapOf("attachment" to "Keep note")))
            val result =
                TaskAttachmentRecovery(context) { context.getString(R.string.attachment_choose_file) }
                    .prepare(original, emptySet(), TaskAttachmentRecovery.requestFor(request, confirmed))
            assertEquals("picker", result.single().action.params["attachment"])
        }
}
