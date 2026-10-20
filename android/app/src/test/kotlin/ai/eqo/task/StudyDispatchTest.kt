package ai.eqo.task

import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.ExecutedAction
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.TaskPlanPreview
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyDispatchTest {
    @Test fun shoppingPopupHandoffIsRecordedWithoutClaimingTheQuantityChanged() =
        runTest {
            val message = ai.eqo.actions.impl.ShoppingPopupGuard.HANDOFF
            val step =
                LoopStep(
                    "quantity",
                    ExecutedAction(
                        "CLICK_TEXT",
                        mapOf("text" to ai.eqo.core.agent.ShoppingPlans.INCREASE),
                        plannerClaim = "shopping:milk in Zepto",
                    ),
                )
            val executor =
                StudyActionExecutor(RecordingPort(), ApprovedTaskPlan(listOf(step))) { _, _ ->
                    ai.eqo.actions.base.ActionResult
                        .UserActionRequired(message)
                }
            assertEquals(ExecuteResult.Success(message, handedToOwner = true), executor.execute(step))
            assertEquals(message, executor.handoffDetail(step.stepId))
        }

    @Test fun stopCancelsAnIrreversiblePermissionWaitBeforeAnyEffect() =
        runTest {
            val answer = CompletableDeferred<Boolean>()
            var waiting = false
            var effects = 0
            val step =
                LoopStep(
                    "location",
                    ExecutedAction("SHARE_LOCATION", mapOf("to" to "Example", "via" to "sms"), irreversible = true),
                )
            val plan = ApprovedTaskPlan(listOf(step))
            val executor =
                StudyActionExecutor(RecordingPort(), plan) { _, _ ->
                    waiting = true
                    val allowed = answer.await()
                    waiting = false
                    if (allowed) {
                        effects++
                        ai.eqo.actions.base.ActionResult
                            .Success(mapOf("message" to "done"))
                    } else {
                        ai.eqo.actions.base.ActionResult
                            .Failure("Permission was not granted; this step did not run.")
                    }
                }
            val controller =
                StudyTaskController(
                    listOf(step),
                    StudyPermissionCheck({ true }, { true }, { true }),
                    StudyApprovalGate(StudyApprovalSurface { error("Already approved") }, { 0L }, plan),
                    executor,
                    observe = { "" },
                    takeoverDetector = ai.eqo.accessibility.TakeoverDetector(),
                    isPermissionWaiting = { waiting },
                    cancelPermissionWait = { answer.complete(false) },
                )
            val run = async { controller.run() }
            runCurrent()
            advanceTimeBy(6_000)
            runCurrent()
            assertTrue(!run.isCompleted)
            assertTrue(controller.stop())
            advanceUntilIdle()
            assertEquals("STOPPED", run.await().terminal)
            assertEquals(0, effects)
        }

    @Test fun grantAfterFiveSecondsContinuesSameApprovedStepExactlyOnce() =
        runTest {
            val answer = CompletableDeferred<Boolean>()
            var waiting = false
            var effects = 0
            val step =
                LoopStep(
                    "email",
                    ExecutedAction(
                        "SEND_EMAIL",
                        mapOf("to" to "Example", "subject" to "Test", "body" to "Test"),
                        irreversible = true,
                    ),
                )
            val plan = ApprovedTaskPlan(listOf(step))
            val executor =
                StudyActionExecutor(RecordingPort(), plan) { _, _ ->
                    waiting = true
                    answer.await()
                    waiting = false
                    effects++
                    ai.eqo.actions.base.ActionResult
                        .Success(mapOf("message" to "done"))
                }
            val controller =
                StudyTaskController(
                    listOf(step),
                    StudyPermissionCheck({ true }, { true }, { true }),
                    StudyApprovalGate(StudyApprovalSurface { error("No second approval") }, { 0L }, plan),
                    executor,
                    observe = { "" },
                    takeoverDetector = ai.eqo.accessibility.TakeoverDetector(),
                    isPermissionWaiting = { waiting },
                )
            val run = async { controller.run() }
            runCurrent()
            advanceTimeBy(15_000)
            runCurrent()
            assertTrue(!run.isCompleted)
            answer.complete(true)
            advanceUntilIdle()
            assertEquals("COMPLETED", run.await().terminal)
            assertEquals(1, effects)
        }

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

    @Test fun registryHandoffStopsTheLoopButPresentsNeedsYou() =
        runBlocking {
            val steps =
                listOf(
                    LoopStep(
                        "1",
                        ExecutedAction("SEND_SMS", mapOf("contact" to "test recipient", "message" to "test draft")),
                    ),
                    LoopStep("2", ExecutedAction("WAIT")),
                )
            val plan = ApprovedTaskPlan(steps)
            val calls = mutableListOf<String>()
            val executor =
                StudyActionExecutor(RecordingPort(), plan) { name, _ ->
                    calls += name
                    ai.eqo.actions.base.ActionResult
                        .UserActionRequired("SMS draft opened; nothing was sent")
                }
            val controller =
                StudyTaskController(
                    steps,
                    StudyPermissionCheck({ true }, { true }, { true }),
                    StudyApprovalGate(
                        StudyApprovalSurface { ai.eqo.study.ApprovalOutcome.Approved },
                        { 0L },
                        plan,
                    ),
                    executor,
                    observe = { "" },
                    takeoverDetector = ai.eqo.accessibility.TakeoverDetector(),
                )
            val receipt = controller.run()
            assertEquals(listOf("SEND_SMS"), calls)
            assertEquals("COMPLETED", receipt.terminal)
            assertEquals(ai.eqo.study.StepProgressState.NEEDS_YOU, receipt.steps.first().state)
            assertEquals(ai.eqo.study.StepProgressState.PENDING, receipt.steps.last().state)
            assertEquals("NEEDS_YOU", RunStatusMapping.terminal(receipt))
            assertEquals(ai.eqo.R.string.run_sms_draft, RunStatusMapping.detail(receipt.steps.first())?.resource)
        }

    @Test fun executorHandoffDoesNotTurnNeedsInputIntoSuccess() =
        runBlocking {
            val step = LoopStep("1", ExecutedAction("SEND_SMS"))
            val executor =
                StudyActionExecutor(RecordingPort(), registryExecute = { _, _ ->
                    ai.eqo.actions.base.ActionResult
                        .NeedsInput("Choose a recipient")
                })
            val result = executor.execute(step)
            assertTrue(result is ExecuteResult.Failure)
            assertEquals("Needs user input: Choose a recipient", (result as ExecuteResult.Failure).reason)
            assertEquals("Choose a recipient", executor.handoffDetail("1"))
        }

    @Test fun everyRegistryActionCanHandOffWithoutFailingRetryingObservingOrRunningTheNextStep() =
        runTest {
            ai.eqo.core.agent.ActionSchema.ALL_ACTIONS.forEach { definition ->
                val message = "Editor opened; review and finish there."
                val steps =
                    listOf(
                        LoopStep("1", ExecutedAction(definition.name, expectedPostconditions = listOf("saved"))),
                        LoopStep("2", ExecutedAction("WAIT")),
                    )
                val calls = mutableListOf<String>()
                val statuses = mutableListOf<ai.eqo.data.models.PlanStatus>()
                var observations = 0
                val executor =
                    StudyActionExecutor(
                        RecordingPort(),
                        registryExecute = { name, _ ->
                            calls += name
                            ActionResult.UserActionRequired(message)
                        },
                    )
                val controller =
                    StudyTaskController(
                        steps,
                        StudyPermissionCheck({ true }, { true }, { true }),
                        StudyApprovalGate(StudyApprovalSurface { ai.eqo.study.ApprovalOutcome.Approved }, { 0L }),
                        executor,
                        observe = {
                            observations++
                            "owner's editor"
                        },
                        onPlanStatus = statuses::add,
                        takeoverDetector = ai.eqo.accessibility.TakeoverDetector(),
                    )
                val receipt = controller.run()
                assertEquals(definition.name, "COMPLETED", receipt.terminal)
                assertEquals(listOf(definition.name), calls)
                assertEquals(0, observations)
                assertTrue(ai.eqo.data.models.PlanStatus.FAILED !in statuses)
                assertEquals(ai.eqo.study.StepProgressState.NEEDS_YOU, receipt.steps.first().state)
                assertEquals(message, receipt.steps.first().detail)
                assertEquals(ai.eqo.study.StepProgressState.PENDING, receipt.steps.last().state)
                assertEquals("NEEDS_YOU", RunStatusMapping.terminal(receipt))
            }
        }

    @Test fun deniedPrerequisitesAndPendingFlowsAreNotCompletedHandoffs() =
        runTest {
            val step = LoopStep("1", ExecutedAction("CREATE_CALENDAR_EVENT"))
            val results =
                listOf(
                    ActionResult.Failure("Calendar is unavailable"),
                    ActionResult.PendingUserAction("Call verification pending"),
                    ActionResult.UserActionRequired("Permission denied", handoffCompleted = false),
                )
            results.forEach { result ->
                val executor = StudyActionExecutor(RecordingPort(), registryExecute = { _, _ -> result })
                assertTrue(executor.execute(step) is ExecuteResult.Failure)
            }
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
