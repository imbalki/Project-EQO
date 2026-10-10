// Origin: EQO TASK-077 (#20), UI recreation and approval persistence regression tests.
package ai.eqo.task

import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.data.models.PlanStatus
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ForegroundPlanRunTest {
    @After fun reset() {
        TaskRunSession.retry = TaskPlanRetry()
        TaskRunSession.requestDraft = ""
        TaskRunSession.controller = null
        TaskRunSession.pending = null
        TaskRunSession.receipt = null
        TaskRunSession.progress.clear()
        TaskRunSession.status = PlanStatus.PENDING
        PlanApprovalSettings.setRequired(ApplicationProvider.getApplicationContext(), true)
    }

    @Test fun everyEnabledRegistryActionIsReachableThroughExecutor() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val registry =
                ai.eqo.actions.impl.AndroidActionRegistry
                    .create(
                        context,
                        ai.eqo.actions.impl
                            .PermissionRequester { false },
                    )
            val reached = mutableSetOf<String>()
            registry.enabledActionNames.forEach { name ->
                val definition =
                    requireNotNull(
                        ai.eqo.core.agent.ActionSchema
                            .getAction(name),
                    )
                val params =
                    definition.params.filter { it.required }.associate { param ->
                        param.name to
                            when (param.type) {
                                ai.eqo.core.agent.ParamType.INT -> "1"
                                ai.eqo.core.agent.ParamType.BOOLEAN -> "true"
                                ai.eqo.core.agent.ParamType.ENUM -> param.enumValues.first()
                                ai.eqo.core.agent.ParamType.STRING -> "example"
                            }
                    }
                val step = LoopStep("1", ExecutedAction(name, params, irreversible = true))
                val plan = ApprovedTaskPlan(listOf(step))
                val executor =
                    StudyActionExecutor(EqoAutomationPort({ null }, { _, _ -> false }), plan) { action, _ ->
                        reached += action
                        ai.eqo.actions.base.ActionResult
                            .Success()
                    }
                assertTrue(executor.execute(step) is ExecuteResult.Success)
            }
            assertEquals(registry.enabledActionNames, reached)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun permissionRetryReapprovesOnlyLocationEvenWithApprovalPreferenceOff() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        PlanApprovalSettings.setRequired(context, false)
        org.robolectric.Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val steps = listOf(
            LoopStep("text", ExecutedAction("SEND_WHATSAPP", mapOf("contact" to "+15555550199", "message" to "test"))),
            LoopStep("location", ExecutedAction("SHARE_LOCATION", mapOf("to" to "+15555550199", "via" to "whatsapp"))),
        )
        TaskRunSession.retry.started(ApprovedTaskPlan(steps))
        TaskRunSession.retry.record(
            ai.eqo.study.StepProgress("text", "SEND_WHATSAPP", ai.eqo.study.StepProgressState.DONE),
        )
        TaskRunSession.retry.record(
            ai.eqo.study.StepProgress(
                "location", "SHARE_LOCATION", ai.eqo.study.StepProgressState.NEEDS_YOU,
                detail = "Allow location. This step did not run.",
            ),
        )
        val lifecycle = Robolectric.buildActivity(TaskActivity::class.java).setup()
        val activity = lifecycle.get()
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.Dispatchers.Unconfined)
        try {
            kotlinx.coroutines.runBlocking {
                activity.prepareAndShowPlan(TaskRunSession.retry.remaining()!!.steps(), retry = true)
            }
        } finally {
            kotlinx.coroutines.Dispatchers.resetMain()
        }
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        val preview = activity.findViewById<android.widget.TextView>(ai.eqo.R.id.task_preview).text.toString()
        assertTrue(preview.contains("Completed steps will not run again"))
        assertTrue(preview.contains("current location"))
        assertFalse(preview.contains("test"))
        assertEquals(null, TaskRunSession.pending)
        assertEquals(null, TaskRunSession.controller)
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(listOf("location"), TaskRunSession.retry.remaining()!!.steps().map { it.stepId })
        activity.findViewById<android.widget.Button>(ai.eqo.R.id.task_stop_button).performClick()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(null, TaskRunSession.pending)
        assertEquals(null, TaskRunSession.controller)
        lifecycle.pause().stop().destroy()
    }

    @Test fun defaultOnAndTogglePersistsAcrossInstances() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(PlanApprovalSettings.required(context))
        PlanApprovalSettings.setRequired(context, false)
        assertFalse(PlanApprovalSettings.required(ApplicationProvider.getApplicationContext()))
    }

    @Test fun whatsappCallRequiresPlanApprovalEvenWhenPreferenceIsOff() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        PlanApprovalSettings.setRequired(context, false)
        val call = LoopStep("1", ExecutedAction("WHATSAPP_CALL", mapOf("contact" to "Alice"), irreversible = true))
        val open = LoopStep("2", ExecutedAction("OPEN_APP", mapOf("appName" to "gmail")))
        assertTrue(PlanApprovalSettings.requiredFor(context, listOf(call)))
        assertTrue(PlanApprovalSettings.requiredFor(context, listOf(open, call)))
        assertFalse(PlanApprovalSettings.requiredFor(context, listOf(open)))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun destroyRecreateKeepsControllerAndRegistryRunNeedsNoStepApproval() =
        runTest {
            val steps =
                listOf(
                    LoopStep("1", ExecutedAction("OPEN_APP", mapOf("appName" to "gmail"), irreversible = true)),
                )
            val approved = ApprovedTaskPlan(steps)
            var calls = 0
            val settle = CompletableDeferred<Unit>()
            val executor =
                StudyActionExecutor(
                    EqoAutomationPort({ null }, { _, _ -> false }),
                    approved,
                ) { name, params ->
                    assertEquals("OPEN_APP", name)
                    assertEquals("gmail", params["appName"])
                    calls++
                    settle.await()
                    ai.eqo.actions.base.ActionResult
                        .Success()
                }
            val controller =
                StudyTaskController(
                    steps,
                    StudyPermissionCheck({ true }, { true }, { false }),
                    StudyApprovalGate(
                        StudyApprovalSurface { error("Per-step approval must not show") },
                        { 0L },
                        approved,
                    ),
                    executor,
                    { "" },
                )
            TaskRunSession.controller = controller
            val first = Robolectric.buildActivity(TaskActivity::class.java).setup()
            val running = async { controller.run() }
            runCurrent()
            assertEquals(1, calls)
            first.pause().stop().destroy()
            assertSame(controller, TaskRunSession.controller)
            val second = Robolectric.buildActivity(TaskActivity::class.java).setup()
            assertSame(controller, TaskRunSession.controller)
            assertFalse(running.isCompleted)
            settle.complete(Unit)
            val receipt = running.await()
            assertEquals(listOf("1"), receipt.executedStepIds)
            assertEquals(1, calls)
            assertTrue(
                executor.execute(
                    steps.single().copy(action = ExecutedAction("OPEN_APP", mapOf("appName" to "phone"))),
                ) is ExecuteResult.Failure,
            )
            second.pause().stop().destroy()
        }
}
