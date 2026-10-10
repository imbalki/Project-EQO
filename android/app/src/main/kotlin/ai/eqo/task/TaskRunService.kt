// Origin: EQO TASK-077 (#20), foreground ownership of an immutable approved run.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.actions.impl.AndroidActionRegistry
import ai.eqo.actions.impl.AttachmentChoice
import ai.eqo.actions.impl.AttachmentDecision
import ai.eqo.actions.impl.AttachmentSelection
import ai.eqo.actions.impl.PermissionRequester
import ai.eqo.actions.impl.PickedAttachment
import ai.eqo.actions.impl.RegistryOptions
import ai.eqo.core.agent.ActionLoop
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.data.models.PlanStatus
import ai.eqo.study.ApprovalOutcome
import ai.eqo.study.RunReceipt
import ai.eqo.study.StepProgress
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Process-only snapshot: activity detach never ends a run. Process death never silently repeats sends. */
internal object TaskRunSession {
    var retry = TaskPlanRetry()
    var requestDraft: String = ""
    var pending: ApprovedTaskPlan? = null
    var controller: StudyTaskController? = null
    var status: PlanStatus = PlanStatus.PENDING
    var receipt: RunReceipt? = null
    val progress = linkedMapOf<String, StepProgress>()
    var observer: (() -> Unit)? = null
    var permissionRequester: PermissionRequester? = null
    var attachmentSelection: AttachmentSelection? = null

    fun changed() {
        observer?.invoke()
    }
}

class TaskRunService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "EQO running plan", NotificationManager.IMPORTANCE_LOW),
        )
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, TaskActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        fun command(
            action: String,
            code: Int,
        ) = PendingIntent.getService(
            this,
            code,
            Intent(this, TaskRunService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification =
            Notification
                .Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("EQO is running your plan")
                .setContentText("Tap to view progress or resume after takeover")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Pause", command(PAUSE, 1)).build())
                .addAction(Notification.Action.Builder(null, "Stop", command(STOP, 2)).build())
                .build()
        startForeground(NOTIFICATION, notification)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            PAUSE -> TaskRunSession.controller?.pause()
            STOP -> TaskRunSession.controller?.stop()
            else ->
                if (TaskRunSession.controller == null) {
                    val plan = TaskRunSession.pending
                    TaskRunSession.pending = null
                    if (plan == null) stopSelf() else startPlan(plan)
                }
        }
        return START_NOT_STICKY
    }

    @Suppress("LongMethod") // Single start sequence; splitting would hide the ordering.
    private fun startPlan(plan: ApprovedTaskPlan) {
        check(plan.matches(plan.steps())) { "Approved plan hash mismatch" }
        ai.eqo.accessibility.TakeoverDetector.shared
            .startNewRun()
        val registry =
            AndroidActionRegistry.create(
                applicationContext,
                PermissionRequester { permission ->
                    val granted =
                        when (permission) {
                            is ai.eqo.actions.impl.ActionPermission.Runtime ->
                                checkSelfPermission(permission.name) ==
                                    android.content.pm.PackageManager.PERMISSION_GRANTED
                            is ai.eqo.actions.impl.ActionPermission.SpecialAccess -> permission.isGranted()
                        }
                    // Never launch system UI from an active approved run. Revocation needs explicit restart.
                    granted
                },
                options =
                    RegistryOptions().also {
                        it.attachmentSelection =
                            object : AttachmentSelection {
                                override suspend fun choose(
                                    search: String,
                                    files: List<AttachmentChoice>,
                                ): Int? = TaskRunSession.attachmentSelection?.choose(search, files)

                                override suspend fun pick(initialFolder: String): PickedAttachment? =
                                    TaskRunSession.attachmentSelection?.pick(initialFolder)

                                override suspend fun confirm(files: List<AttachmentChoice>): AttachmentDecision =
                                    TaskRunSession.attachmentSelection?.confirm(files) ?: AttachmentDecision.CANCEL

                                override suspend fun showResolved(files: List<AttachmentChoice>): Boolean =
                                    TaskRunSession.attachmentSelection?.showResolved(files) ?: false
                            }
                    },
            )
        val executor =
            StudyActionExecutor(
                EqoAutomationPort({ EQOAccessibilityService.getInstance()?.automation }, { _, _ -> false }),
                plan,
                registry::execute,
            )
        TaskRunSession.retry.started(plan)
        TaskRunSession.receipt = null
        TaskRunSession.progress.clear()
        plan.steps().forEach {
            TaskRunSession.progress[it.stepId] =
                StepProgress(it.stepId, it.action.name, ai.eqo.study.StepProgressState.PENDING)
        }
        val controller =
            StudyTaskController(
                steps = plan.steps(),
                permissionCheck =
                    StudyPermissionCheck({
                        checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }, {
                        EQOAccessibilityService.getInstance() !=
                            null
                    }, { false }),
                approvalGate =
                    StudyApprovalGate(
                        StudyApprovalSurface {
                            ApprovalOutcome.Rejected("Plan approval missing")
                        },
                        { System.currentTimeMillis() },
                        plan,
                    ),
                executor = executor,
                observe = { "" },
                onPlanStatus = {
                    TaskRunSession.status = it
                    TaskRunSession.changed()
                },
                onStepProgress = {
                    TaskRunSession.retry.record(it)
                    TaskRunSession.progress[it.stepId] = it
                    TaskRunSession.changed()
                },
                config = ActionLoop.Config(),
                isPermissionWaiting = { TaskRunSession.permissionRequester?.isWaiting() == true },
                cancelPermissionWait = { TaskRunSession.permissionRequester?.cancelWaiting() },
            )
        TaskRunSession.controller = controller
        launchRun(controller)
    }

    private fun launchRun(controller: StudyTaskController) {
        scope.launch {
            try {
                val receipt = controller.run()
                receipt.steps.forEach {
                    TaskRunSession.retry.record(it)
                    TaskRunSession.progress[it.stepId] = it
                }
                TaskRunSession.receipt = receipt
            } finally {
                TaskRunSession.controller = null
                TaskRunSession.changed()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        // Service termination, unlike UI destruction, cancels execution. No automatic replay.
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "eqo_plan_run"
        private const val NOTIFICATION = 77
        private const val PAUSE = "ai.eqo.task.PAUSE"
        private const val STOP = "ai.eqo.task.STOP"
    }
}
