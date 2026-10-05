package ai.eqo.task

import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.TaskPlanPreview
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyDispatchTest {
    @Test fun handlerMapKeepsLegacyAliasesAndDefaults() =
        runBlocking {
            val port = RecordingPort()
            val executor = StudyActionExecutor(port)
            val cases =
                mapOf(
                    "observe" to "observe",
                    "tap" to "tap:hello",
                    "click_text" to "tap:hello",
                    "tap_text" to "tap:hello",
                    "click_id" to "id:field",
                    "open_app" to "open:mail",
                    "type_text" to "type:hello",
                    "paste" to "type:hello",
                    "back" to "back",
                    "press_back" to "back",
                    "home" to "home",
                    "press_home" to "home",
                    "press_enter" to "enter",
                    "send_whatsapp" to "chat:whatsapp:body",
                    "send_telegram" to "chat:telegram:body",
                    "compose_sms" to "sms::body",
                    "compose_email" to "email::subject:body",
                    "scroll" to "scroll:down",
                )
            val params =
                mapOf(
                    "text" to "hello",
                    "view_id" to "field",
                    "app" to "mail",
                    "subject" to "subject",
                    "body" to "body",
                )
            cases.forEach { (verb, expected) ->
                assertTrue(verb, executor.execute(LoopStep("1", ExecutedAction(verb, params))) is ExecuteResult.Success)
                assertEquals(expected, port.last)
            }
            val before = port.last
            assertTrue(executor.execute(LoopStep("1", ExecutedAction("unsupported"))) is ExecuteResult.Failure)
            assertEquals(before, port.last)
        }

    @Test fun approvedTypingAndDraftsDispatchOriginalBytesNotEscapedPreview() =
        runBlocking {
            val raw = "hello\u202E\u200F\uFEFF"
            val steps =
                listOf(
                    LoopStep("1", ExecutedAction("type_text", mapOf("target" to "field", "text" to raw))),
                    LoopStep("2", ExecutedAction("compose_sms", mapOf("to" to "", "body" to raw))),
                )
            val plan = ApprovedTaskPlan(steps)
            assertTrue(TaskPlanPreview.describe(plan.steps()).contains("\\u{202E}"))
            val port = RecordingPort()
            val executor = StudyActionExecutor(port, plan)
            assertTrue(executor.execute(plan.steps()[0]) is ExecuteResult.Success)
            assertEquals("target:field:$raw", port.last)
            assertTrue(executor.execute(plan.steps()[1]) is ExecuteResult.Success)
            assertEquals("sms::$raw", port.last)
            val changed = steps[1].copy(action = steps[1].action.copy(params = mapOf("to" to "", "body" to "changed")))
            assertTrue(executor.execute(changed) is ExecuteResult.Failure)
            assertEquals("sms::$raw", port.last)
        }

    private class RecordingPort : StudyAutomationPort {
        var last = ""

        private fun record(value: String): Boolean {
            last = value
            return true
        }

        override fun observe(): String {
            record("observe")
            return "screen"
        }

        override fun tap(text: String) = record("tap:$text")

        override fun tapById(viewId: String) = record("id:$viewId")

        override fun typeText(text: String) = record("type:$text")

        override fun typeTarget(
            target: String,
            text: String,
        ) = record("target:$target:$text")

        override fun scroll(direction: String) = record("scroll:$direction")

        override fun back() = record("back")

        override fun home() = record("home")

        override fun enter() = record("enter")

        override fun openApp(app: String) = record("open:$app")

        override suspend fun sendChat(
            app: String,
            body: String,
        ) = record("chat:$app:$body")

        override fun composeSmsDraft(
            recipient: String,
            body: String,
        ) = record("sms:$recipient:$body")

        override fun composeEmailDraft(
            recipient: String,
            subject: String,
            body: String,
        ) = record("email:$recipient:$subject:$body")
    }
}
