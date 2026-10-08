// Origin: EQO vision-locate, Android settings and immutable approval integration tests.
@file:Suppress("MaxLineLength") // Synthetic table-driven scenarios; assertions preserve exact approved payloads.

package ai.eqo.task

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.TaskPlanPreview
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
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
class VisionLocateRuntimeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val step = LoopStep("1", ExecutedAction("tap_text", mapOf("text" to "Approved button")))

    @After
    fun reset() {
        VisionLocateSettings.setEnabled(context, false)
    }

    @Test
    fun settingDefaultsOffPersistsAndPreviewNamesOnlyEligibleSteps() {
        assertFalse(VisionLocateSettings.enabled(context))
        VisionLocateSettings.setEnabled(context, true)
        assertTrue(VisionLocateSettings.enabled(ApplicationProvider.getApplicationContext()))
        assertFalse(ApprovedTaskPlan(listOf(step)).visionAllowed)
        assertFalse(TaskPlanPreview.describe(listOf(step)).contains("screen image"))
        assertTrue(TaskPlanPreview.describe(listOf(step), visionAllowed = true).contains("one screen image"))
        val open = LoopStep("2", ExecutedAction("open_app", mapOf("app" to "test app")))
        assertFalse(TaskPlanPreview.describe(listOf(open), visionAllowed = true).contains("screen image"))
    }

    @Test
    fun accessibilityFirstThenOnlyNodeNotFoundCanLocate() =
        runTest {
            val outcomes =
                listOf(
                    A11yResult.success("found"),
                    A11yResult.failure(A11yError.NodeNotFound("button")),
                    A11yResult.failure(A11yError.TakeoverDetected),
                    A11yResult.failure(A11yError.SecureWindow),
                    A11yResult.failure(A11yError.AccessibilityDisabled),
                    A11yResult.failure(A11yError.ActionRejected("button")),
                )
            for (outcome in outcomes) {
                val order = mutableListOf<String>()
                val runtime =
                    VisionLocateRuntime(context, ApprovedTaskPlan(listOf(step), visionAllowed = true), textAction = { label, content ->
                        assertEquals("Approved button", label)
                        assertNull(content)
                        order += "accessibility"
                        outcome
                    }, locateOverride = { label ->
                        assertEquals("Approved button", label)
                        assertTrue(
                            ai.eqo.accessibility.TakeoverDetector.shared
                                .isAgentActionInFlight(),
                        )
                        order += "locate"
                        ExecuteResult.Success("screen changed")
                    })
                runtime.execute(step)
                assertFalse(
                    ai.eqo.accessibility.TakeoverDetector.shared
                        .isAgentActionInFlight(),
                )
                assertEquals(
                    if ((outcome as? A11yResult.Failure)?.error is A11yError.NodeNotFound) {
                        listOf(
                            "accessibility",
                            "locate",
                        )
                    } else {
                        listOf("accessibility")
                    },
                    order,
                )
            }
        }

    @Test
    fun newLabelNewStepAndNoVisionConsentCannotInvokeFallback() =
        runTest {
            val runtime =
                VisionLocateRuntime(context, ApprovedTaskPlan(listOf(step), visionAllowed = true), textAction = {
                    _,
                    _,
                    ->
                    error("No action")
                })
            assertTrue(runtime.execute(step.copy(action = step.action.copy(params = mapOf("text" to "Other")))) is ExecuteResult.Failure)
            assertTrue(runtime.execute(step.copy(stepId = "injected")) is ExecuteResult.Failure)
            val off = VisionLocateRuntime(context, ApprovedTaskPlan(listOf(step)), textAction = { _, _ -> error("Normal dispatch") })
            assertNull(off.execute(step))
        }

    @Test
    fun typeUsesOnlyApprovedTextAfterSuccessfulLocateAndCannotRepeat() =
        runTest {
            val type = LoopStep("1", ExecutedAction("type_text", mapOf("target" to "Search", "text" to "approved query")))
            val order = mutableListOf<String>()
            val runtime =
                VisionLocateRuntime(context, ApprovedTaskPlan(listOf(type), visionAllowed = true), textAction = { label, text ->
                    assertEquals("Search", label)
                    assertEquals("approved query", text)
                    order += "accessibility"
                    A11yResult.failure(A11yError.NodeNotFound(label))
                }, typeFocused = { text ->
                    assertEquals("approved query", text)
                    order += "type"
                    A11yResult.success("typed")
                }, locateOverride = { label ->
                    assertEquals("Search", label)
                    order += "locate"
                    ExecuteResult.Success("screen changed")
                })
            assertTrue(runtime.execute(type) is ExecuteResult.Success)
            assertEquals(listOf("accessibility", "locate", "type"), order)
            assertTrue(runtime.execute(type) is ExecuteResult.Failure)
            assertEquals(3, order.size)
        }

    @Test
    fun failedLocateNeverTypesAndExecutorChecksApprovalBeforeCallback() =
        runTest {
            val type = LoopStep("1", ExecutedAction("type_text", mapOf("target" to "Search", "text" to "approved query")))
            val plan = ApprovedTaskPlan(listOf(type), visionAllowed = true)
            val runtime =
                VisionLocateRuntime(context, plan, textAction = {
                    label,
                    _,
                    ->
                    A11yResult.failure(A11yError.NodeNotFound(label))
                }, typeFocused = { error("No typing") }, locateOverride = { ExecuteResult.Failure("unchanged screen") })
            val executor = StudyActionExecutor(EqoAutomationPort({ null }, { _, _ -> false }), plan, approvedTextExecute = runtime::execute)
            assertTrue(executor.execute(type.copy(stepId = "injected")) is ExecuteResult.Failure)
            assertTrue(executor.execute(type) is ExecuteResult.Failure)
        }
}
