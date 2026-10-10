package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.agent.ApprovalDecision
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.TaskPlanner
import android.content.Intent
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class NaturalTaskFlowTest {
    @Test fun smsAndEmailUseOnlySendToWithEncodedRecipientsAndBody() {
        val intents = mutableListOf<Intent>()
        assertTrue(SmsDraftOpener { intents.add(it) }.open("+123", "hello"))
        assertTrue(EmailDraftOpener { intents.add(it) }.open("a@example.com", "A & B", "hello? & body"))
        assertTrue(intents.all { it.action == Intent.ACTION_SENDTO })
        assertEquals("smsto", intents[0].data!!.scheme)
        assertEquals("+123", intents[0].data!!.schemeSpecificPart)
        assertEquals("hello", intents[0].getStringExtra("sms_body"))
        assertEquals("mailto", intents[1].data!!.scheme)
        assertTrue(intents[1].data.toString().contains("subject=A%20%26%20B"))
        assertEquals("hello? & body", intents[1].getStringExtra(Intent.EXTRA_TEXT))
        assertFalse(intents.any { it.action == Intent.ACTION_CHOOSER || it.action == Intent.ACTION_SEND })
    }

    @Test fun missingComposerIsFailure() {
        assertFalse(EmailDraftOpener { throw android.content.ActivityNotFoundException() }.open("", "", "hello"))
        assertFalse(SmsDraftOpener { throw android.content.ActivityNotFoundException() }.open("", "hello"))
    }

    @Test fun exactWholePlanApprovalReplacesPerStepCardButNotPolicy() =
        runBlocking {
            val proposal = """{"steps":[{"action":"compose_sms","params":{"to":"","body":"hello"}}]}"""
            val step = TaskPlanner.parse(proposal).single()
            val plan = ApprovedTaskPlan(listOf(step))
            var cards = 0
            val gate =
                StudyApprovalGate(
                    StudyApprovalSurface {
                        cards++
                        ai.eqo.study.ApprovalOutcome.Approved
                    },
                    { 0 },
                    plan,
                )
            assertTrue(
                ai.eqo.core.agent.SensitivityApprovalPolicy
                    .requiresApproval(step.action),
            )
            assertEquals(ApprovalDecision.Approved, gate.request(step))
            assertTrue(
                gate.request(
                    step.copy(action = step.action.copy(params = mapOf("to" to "", "body" to "changed"))),
                ) is ApprovalDecision.Rejected,
            )
            assertEquals(0, cards)
        }

    @Test fun newNonSensitiveStepAlsoCannotExecuteUnderOldApproval() =
        runBlocking {
            val step = LoopStep("1", ExecutedAction("observe"))
            val port = RecordingPort()
            val executor = StudyActionExecutor(port, ApprovedTaskPlan(listOf(step)))
            assertTrue(executor.execute(step) is ExecuteResult.Success)
            assertTrue(executor.execute(step.copy(stepId = "injected")) is ExecuteResult.Failure)
            assertEquals(1, port.observations)
        }

    @Test fun screenHasRequestAndNoSampleRunsOnCreate() {
        val activity = Robolectric.buildActivity(TaskActivity::class.java).setup().get()
        assertEquals("What should EQO do?", activity.findViewById<EditText>(R.id.task_request).hint.toString())
        assertEquals("Plan", activity.getString(R.string.task_start))
        assertTrue(activity.getString(R.string.task_recipient_limit).contains("only when a plan uses a name"))
        assertEquals(0, activity.findViewById<android.widget.LinearLayout>(R.id.task_steps_container).childCount)
    }

    @Test fun previewDialogAndContextApprovalEscapeFormatCharacters() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        val raw = "hello\u202E\u2066\u200F\uFEFF"
        val action = ExecutedAction("compose_sms", mapOf("to" to "", "body" to raw))
        val plan = ApprovedTaskPlan(listOf(LoopStep("1", action)))
        val show =
            TaskActivity::class.java.getDeclaredMethod(
                "showPlan",
                ApprovedTaskPlan::class.java,
                Map::class.java,
                List::class.java,
                Boolean::class.javaPrimitiveType,
            )
        show.isAccessible = true
        show.invoke(activity, plan, emptyMap<String, String>(), emptyList<String>(), false)
        val preview = activity.findViewById<android.widget.TextView>(R.id.task_preview).text.toString()
        val dialog =
            org.robolectric.shadows.ShadowAlertDialog
                .getLatestAlertDialog()
        assertEquals(preview, dialog.findViewById<android.widget.TextView>(android.R.id.message).text.toString())
        assertTrue(preview.contains("\\u{202E}"))
        assertFalse(preview.contains('\u202E'))
        val target = TaskActivity::class.java.getDeclaredMethod("approvalTarget", String::class.java)
        target.isAccessible = true
        listOf("to=, body=$raw", raw).forEach { value ->
            val rendered = target.invoke(activity, value) as String
            assertTrue(rendered.contains("\\u{202E}"))
            assertFalse(rendered.contains('\u202E'))
        }
        assertEquals(
            raw,
            plan
                .steps()
                .single()
                .action.params["body"],
        )
        dialog.dismiss()
        lifecycle.pause().stop().destroy()
    }

    @Test fun resolvedContactIsVisibleInApprovalButNeverLoggedEvenInDebug() {
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        val phone = "+15551234567"
        val plan =
            ApprovedTaskPlan(
                listOf(LoopStep("sms", ExecutedAction("SEND_SMS", mapOf("contact" to phone, "message" to "hello")))),
            )
        activity.applicationInfo.flags =
            activity.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE
        val show =
            TaskActivity::class.java.getDeclaredMethod(
                "showPlan",
                ApprovedTaskPlan::class.java,
                Map::class.java,
                List::class.java,
                Boolean::class.javaPrimitiveType,
            )
        show.isAccessible = true
        show.invoke(activity, plan, mapOf("sms" to "Contact Name"), emptyList<String>(), false)
        val preview = activity.findViewById<android.widget.TextView>(R.id.task_preview).text.toString()
        val dialog =
            org.robolectric.shadows.ShadowAlertDialog
                .getLatestAlertDialog()
        assertTrue(preview.contains("Contact Name"))
        assertTrue(preview.contains(phone))
        assertEquals(preview, dialog.findViewById<android.widget.TextView>(android.R.id.message).text.toString())
        org.robolectric.shadows.ShadowLog.getLogs().forEach {
            assertFalse(it.msg.contains("Alice"))
            assertFalse(it.msg.contains(phone))
        }
        dialog.dismiss()
        lifecycle.pause().stop().destroy()
    }

    @Test fun modelSelectionSurvivesNewContext() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        StudyModelChoice.save(context, "fake-model")
        assertEquals("fake-model", StudyModelChoice.read(context))
    }

    private class RecordingPort : StudyAutomationPort {
        var observations = 0

        override fun observe(): String {
            observations++
            return "screen"
        }

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
