package ai.eqo.handle

import ai.eqo.R
import ai.eqo.accessibility.handle.HandleEntry
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import android.widget.Button

/** Entry-scoped navigation only. No usage history or package name is saved or logged. */
internal object HandleReturn {
    private const val PREVIOUS = "ai.eqo.handle.PREVIOUS_APP"

    fun intent(
        context: Context,
        destination: Class<*>,
        entry: HandleEntry?,
    ): Intent =
        Intent(context, destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply {
            entry?.foregroundPackage?.takeIf { it != context.packageName }?.let { putExtra(PREVIOUS, it) }
        }

    fun install(activity: Activity) {
        val previous = activity.intent.getStringExtra(PREVIOUS) ?: return
        val label =
            runCatching {
                activity.packageManager.getApplicationLabel(activity.packageManager.getApplicationInfo(previous, 0))
            }.getOrElse { activity.getString(R.string.handle_previous_app) }
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val layout = root.getChildAt(0)
        val column = if (layout is android.widget.ScrollView) layout.getChildAt(0) as ViewGroup else layout as ViewGroup
        column.addView(
            Button(activity).apply {
                text = activity.getString(R.string.handle_back_app, label)
                setOnClickListener {
                    val launch = activity.packageManager.getLaunchIntentForPackage(previous)
                    if (launch != null) runCatching { activity.startActivity(launch) }
                    activity.moveTaskToBack(true)
                }
            },
            0,
        )
    }
}
