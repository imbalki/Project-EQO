// Origin: EQO edge-handle task; built-ins call existing run controls, never loop internals.
package ai.eqo.handle

import ai.eqo.MainActivity
import ai.eqo.R
import ai.eqo.accessibility.handle.HandlePreferences
import ai.eqo.accessibility.handle.HandleShortcut
import ai.eqo.accessibility.handle.HandleShortcutRegistry
import ai.eqo.core.agent.LoopState
import ai.eqo.task.TaskActivity
import ai.eqo.task.TaskRunSession
import android.content.Context
import android.content.Intent
import ai.eqo.platform.R as PlatformR

internal interface HandleRunControls {
    fun active(): Boolean

    fun pause()

    fun stop()
}

internal object LiveHandleRunControls : HandleRunControls {
    override fun active(): Boolean = TaskRunSession.controller != null

    override fun pause() {
        TaskRunSession.controller?.pause()
    }

    override fun stop() {
        TaskRunSession.controller?.stop()
    }
}

/** A real user panel-open gesture requests Pause; closing the panel never resumes work. */
internal fun prepareHandlePanel(requestPause: Boolean): Boolean {
    val controller = TaskRunSession.controller ?: return true
    val state = controller.currentState()
    if (requestPause && state == LoopState.RUNNING) controller.pause()
    return state != LoopState.RUNNING && !controller.isActionInFlight()
}

internal class BuiltInHandleShortcut(
    override val id: String,
    override val labelRes: Int,
    override val iconRes: Int,
    private val reason: (Context) -> Int? = { null },
    private val action: (Context) -> Unit,
) : HandleShortcut<Context> {
    override val defaultEnabled = true

    override fun unavailableReason(context: Context): Int? = reason(context)

    override fun run(context: Context) {
        if (unavailableReason(context) == null) action(context)
    }
}

internal fun createHandleRegistry(
    context: Context,
    controls: HandleRunControls = LiveHandleRunControls,
): HandleShortcutRegistry<Context> {
    val registry = HandleShortcutRegistry<Context>(HandlePreferences(context))
    registry.register(
        BuiltInHandleShortcut("explain_screen", R.string.explain_title, PlatformR.drawable.ic_handle_explain) {
            ai.eqo.explain.ExplainEntryActivity
                .launch(it, "edge_handle")
        },
    )
    registry.register(
        BuiltInHandleShortcut("ask_eqo", R.string.handle_ask, PlatformR.drawable.ic_handle_ask) {
            it.startActivity(
                Intent(it, TaskActivity::class.java)
                    .putExtra(TaskActivity.FOCUS_REQUEST, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
    registerRunControls(registry, controls)
    registry.register(
        BuiltInHandleShortcut("open_eqo", R.string.handle_open, PlatformR.drawable.ic_handle_open) {
            it.startActivity(Intent(it, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
    )
    registry.register(
        BuiltInHandleShortcut(
            ai.eqo.accessibility.handle.EdgeHandleFeatures.DICTATION_ID,
            R.string.dictation_switch,
            PlatformR.drawable.ic_handle_dictation,
        ) {
            ai.eqo.task.HandleDictation
                .tap(it)
        },
    )
    registry.register(
        BuiltInHandleShortcut(
            ai.eqo.accessibility.handle.EdgeHandleFeatures.RECORDING_ID,
            PlatformR.string.edge_recording_button,
            R.drawable.ic_record_note,
        ) { HandleRecording.tap(it) },
    )
    registry.register(
        BuiltInHandleShortcut(
            ai.eqo.accessibility.handle.EdgeHandleFeatures.SCREENSHOT_ID,
            PlatformR.string.edge_screenshot_button,
            PlatformR.drawable.ic_handle_screenshot,
        ) { HandleScreenshot.tap(it) },
    )
    return registry
}

/** Pause and Stop only exist during a run; they call the controller's own controls. */
private fun registerRunControls(
    registry: HandleShortcutRegistry<Context>,
    controls: HandleRunControls,
) {
    val activeReason: (Context) -> Int? = { if (controls.active()) null else R.string.handle_no_run }
    registry.register(
        BuiltInHandleShortcut("pause", R.string.task_pause, PlatformR.drawable.ic_handle_pause, activeReason) {
            controls.pause()
        },
    )
    registry.register(
        BuiltInHandleShortcut("stop", R.string.task_stop, PlatformR.drawable.ic_handle_stop, activeReason) {
            controls.stop()
        },
    )
}
