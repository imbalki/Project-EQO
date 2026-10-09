// Origin: EQO edge-handle task; built-ins call existing run controls, never loop internals.
package ai.eqo.handle

import ai.eqo.MainActivity
import ai.eqo.R
import ai.eqo.accessibility.handle.HandlePreferences
import ai.eqo.accessibility.handle.HandleShortcut
import ai.eqo.accessibility.handle.HandleShortcutRegistry
import ai.eqo.task.TaskActivity
import ai.eqo.task.TaskRunSession
import android.content.Context
import android.content.Intent

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
        BuiltInHandleShortcut("ask_eqo", R.string.handle_ask, android.R.drawable.ic_menu_edit) {
            it.startActivity(
                Intent(it, TaskActivity::class.java)
                    .putExtra(TaskActivity.FOCUS_REQUEST, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
    val activeReason: (Context) -> Int? = { if (controls.active()) null else R.string.handle_no_run }
    registry.register(
        BuiltInHandleShortcut("pause", R.string.task_pause, android.R.drawable.ic_media_pause, activeReason) {
            controls.pause()
        },
    )
    registry.register(
        BuiltInHandleShortcut("stop", R.string.task_stop, android.R.drawable.ic_delete, activeReason) {
            controls.stop()
        },
    )
    registry.register(
        BuiltInHandleShortcut("open_eqo", R.string.handle_open, android.R.drawable.ic_menu_view) {
            it.startActivity(Intent(it, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
    )
    return registry
}
