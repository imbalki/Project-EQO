// Origin: EQO round-6 UX fix; the plain run sentence is built from actions and outcomes, never executor text.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.study.StepProgress
import ai.eqo.study.StepProgressState
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RunSummaryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private class Item(
        val action: String,
        val params: Map<String, String>,
        val state: StepProgressState,
        val detail: String,
    )

    private fun item(
        action: String,
        params: Map<String, String> = emptyMap(),
        state: StepProgressState = StepProgressState.DONE,
        detail: String = "",
    ) = Item(action, params, state, detail)

    private fun say(
        vararg items: Item,
        terminal: String = "COMPLETED",
    ): String? {
        val plan = items.mapIndexed { i, entry -> LoopStep("s$i", ExecutedAction(entry.action, entry.params)) }
        val progress =
            items.mapIndexed { i, entry -> StepProgress("s$i", entry.action, entry.state, detail = entry.detail) }
        val sentences = RunSummary.sentences(RunSummary.steps(plan, progress), terminal)
        return TaskResultPresentation.sentence(sentences) { it.text(context) }
    }

    private val plumbing =
        arrayOf(
            item("WAIT", mapOf("duration" to "3000"), detail = "waited 3000 ms"),
            item("PRESS_BACK", detail = "automation verb 'press_back' applied through the accessibility plane"),
        )

    @Test fun openingAnAppSaysSoAndNeverQuotesInternalSteps() {
        val text =
            say(
                item("OPEN_APP", mapOf("appName" to "calculator"), detail = "Requested app launched"),
                *plumbing,
            )
        assertEquals("Opened Calculator.", text)
        assertFalse(text.orEmpty().contains("launched"))
        assertFalse(text.orEmpty().contains("3000"))
    }

    @Test fun aSearchNamesTheTermAndTheApp() {
        val text =
            say(
                item("OPEN_APP", mapOf("appName" to "Zepto"), detail = "Requested app launched"),
                item("WAIT", detail = "waited 3000 ms"),
                item("TYPE_TEXT", mapOf("searchText" to "Search", "content" to "banana"), detail = "typed"),
                item("PRESS_ENTER", detail = "pressed"),
            )
        assertEquals("Searched banana in Zepto.", text)
    }

    @Test fun anUnverifiedSendIsSaidHonestlyAndPlainly() {
        val sent =
            item(
                "SEND_WHATSAPP",
                mapOf("contact" to "Example", "message" to "hi"),
                detail = "WhatsApp Send pressed; delivery is not verified.",
            )
        assertEquals("WhatsApp message sent. I could not confirm it was delivered.", say(sent))
        val verified = item("SEND_EMAIL", detail = "Email sent successfully.")
        assertEquals("Email sent.", say(verified))
        val telegram = item("SEND_TELEGRAM", detail = "Telegram Send pressed; delivery is not verified.")
        assertEquals("Telegram message sent. I could not confirm it was delivered.", say(telegram))
    }

    @Test fun handoffsGivePlainInstructionsAndAreNeverCalledFailures() {
        val sms = item("SEND_SMS", state = StepProgressState.NEEDS_YOU, detail = "SMS draft opened; user sends it")
        assertEquals("Text message ready; press Send.", say(sms, terminal = "FAILED"))
        val calendar =
            item(
                "CREATE_CALENDAR_EVENT",
                state = StepProgressState.NEEDS_YOU,
                detail = "Direct calendar insertion failed. Finish creating the event in Calendar.",
            )
        assertEquals("Calendar event ready; tap Save.", say(calendar, terminal = "FAILED"))
        val email = item("SEND_EMAIL", state = StepProgressState.NEEDS_YOU, detail = "Email draft opened, but EQO...")
        assertEquals("Email ready; press Send.", say(email, terminal = "FAILED"))
        val whatsapp =
            item("SEND_WHATSAPP", state = StepProgressState.NEEDS_YOU, detail = "WhatsApp draft opened. You press Send")
        assertEquals("WhatsApp message ready; press Send.", say(whatsapp, terminal = "FAILED"))
        for (text in listOf(say(sms), say(calendar), say(email))) {
            assertFalse(text.orEmpty().contains("fail", ignoreCase = true))
            assertFalse(text.orEmpty().contains("execution_failed"))
        }
    }

    @Test fun aSendDraftIsNotClaimedWhenTheExecutorOnlyAskedAQuestion() {
        val asked = item("SEND_SMS", state = StepProgressState.NEEDS_YOU, detail = "Which Alex do you mean?")
        assertEquals("Which Alex do you mean?", say(asked, terminal = "FAILED"))
        val unknown = item("SEND_SMS", state = StepProgressState.NEEDS_YOU, detail = "Pick a recipient")
        assertEquals("EQO did its part. Please finish the last step yourself.", say(unknown, terminal = "FAILED"))
    }

    @Test fun theCalculateAnswerStaysAndLeadsAnyFollowOnSentence() {
        val calc = item("CALCULATE", mapOf("expression" to "238*8"), detail = "238*8 = 1904")
        assertEquals("238 times 8 is 1904.", say(calc))
        val spoken = item("CALCULATE", mapOf("expression" to "238 times 8"), detail = "238 times 8 = 1904")
        assertEquals("238 times 8 is 1904.", say(spoken))
        val sms = item("SEND_SMS", state = StepProgressState.NEEDS_YOU, detail = "SMS draft opened")
        assertEquals("238 times 8 is 1904. Text message ready; press Send.", say(calc, sms, terminal = "FAILED"))
        val percent = item("CALCULATE", mapOf("expression" to "20 percent of 50"), detail = "20 percent of 50 = 10")
        assertEquals("20 percent of 50 is 10.", say(percent))
    }

    @Test fun otherEverydayRunsHavePlainSentences() {
        assertEquals(
            "Note saved in Keep.",
            say(item("CREATE_NOTE", mapOf("body" to "buy milk", "appName" to "Keep"), detail = "Note saved.")),
        )
        val defaultApp = item("CREATE_NOTE", mapOf("body" to "buy milk"), detail = "Note saved.")
        assertEquals("Note saved in Keep.", say(defaultApp))
        assertEquals("Opened the browser.", say(item("OPEN_BROWSER", detail = "opened")))
        assertEquals("Screenshot taken.", say(item("TAKE_SCREENSHOT", detail = "/sdcard/x.png")))
        assertEquals(
            "Opened a search for weather in Pune.",
            say(
                item(
                    "WEB_SEARCH",
                    mapOf("query" to "weather in Pune"),
                    StepProgressState.NEEDS_YOU,
                    "Search opened. Review the results there; EQO has not retrieved or verified them.",
                ),
                terminal = "FAILED",
            ),
        )
        assertEquals("Done.", say(item("CLICK_TEXT", detail = "clicked")))
    }

    @Test fun failuresUseThePlainReasonNeverAnInternalCode() {
        val text =
            say(
                item("OPEN_APP", mapOf("appName" to "Zepto"), StepProgressState.FAILED, "a11y_disabled"),
                item("WAIT", state = StepProgressState.PENDING),
                terminal = "FAILED",
            )
        assertEquals(context.getString(R.string.run_accessibility_off), text)
        val unknownCode =
            say(
                item("OPEN_APP", state = StepProgressState.FAILED, detail = "execution_failed: boom"),
                terminal = "FAILED",
            )
        assertEquals(context.getString(R.string.run_failed_unknown), unknownCode)
        val takeover =
            say(
                item("SEND_EMAIL", state = StepProgressState.FAILED, detail = "a11y_takeover"),
                terminal = "FAILED",
            )
        assertEquals(context.getString(R.string.run_took_control), takeover)
    }

    @Test fun anUnknownResultSaysItCouldNotTell() {
        val text = say(item("SEND_SMS", state = StepProgressState.UNKNOWN, detail = "interrupted"), terminal = "FAILED")
        assertEquals("I could not tell whether that finished. Please check the screen.", text)
    }

    @Test fun runsTheOwnerEndedOrThatNeverFinishedAreNotAnnounced() {
        val opened = item("OPEN_APP", mapOf("appName" to "Zepto"))
        assertNull(say(opened, terminal = "STOPPED"))
        assertNull(say(opened, terminal = "CANCELLED"))
        val notRun = item("WAIT", state = StepProgressState.PENDING)
        assertNull(say(opened, notRun, terminal = "FAILED"))
        assertNull(say(terminal = "COMPLETED"))
    }

    @Test fun aMissingControlOrCancelledFileChoiceIsAPlainReasonNotAHandoff() {
        val missing = item("CLICK_TEXT", state = StepProgressState.NEEDS_YOU, detail = "control_not_found")
        assertEquals(context.getString(R.string.run_control_not_found), say(missing, terminal = "FAILED"))
        val cancelled =
            item(
                "SEND_EMAIL",
                state = StepProgressState.NEEDS_YOU,
                detail =
                    ai.eqo.core.agent.AttachmentFailure
                        .reason("attachment_cancelled"),
            )
        val text = say(cancelled, terminal = "FAILED").orEmpty()
        assertTrue(text, text.contains("Nothing was sent"))
    }

    @Test fun hiddenCharactersInOwnerTextAreMadeVisible() {
        val text = say(item("OPEN_APP", mapOf("appName" to "zepto‮")))
        assertEquals("Opened Zepto\\u{202E}.", text)
    }
}
