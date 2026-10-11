// Origin: EQO task-screen app chooser and package-only memory management.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.actions.impl.LaunchableAppResolver
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal class AppChoiceDialog(
    private val activity: Activity,
) {
    /** Pick the app, then say whether that answer is for this plan only or always. Cancel at either step stops. */
    suspend fun choose(apps: List<LaunchableAppResolver.Candidate>): AppChoice =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                var shown: AlertDialog? = null

                fun finish(choice: AppChoice) {
                    if (continuation.isActive) continuation.resume(choice)
                }

                fun askScope(app: LaunchableAppResolver.Candidate) {
                    val scope =
                        AlertDialog
                            .Builder(activity)
                            .setTitle(activity.getString(R.string.app_chooser_scope_title, app.label))
                            .setMessage(R.string.app_chooser_scope_message)
                            .setPositiveButton(R.string.app_chooser_always) { _, _ ->
                                finish(AppChoice.Selected(app, remember = true))
                            }.setNegativeButton(R.string.app_chooser_this_time) { _, _ ->
                                finish(AppChoice.Selected(app))
                            }.setNeutralButton(android.R.string.cancel) { _, _ -> finish(AppChoice.Cancelled) }
                            .setOnCancelListener { finish(AppChoice.Cancelled) }
                            .create()
                    shown = scope
                    if (continuation.isActive) {
                        scope.show()
                        protect(scope)
                    }
                }

                val dialog =
                    AlertDialog
                        .Builder(activity)
                        .setTitle(R.string.app_chooser_title)
                        .setAdapter(AppAdapter(activity, apps.take(AppPlanResolution.MAX_CHOICES))) { _, index ->
                            if (continuation.isActive) askScope(apps[index])
                        }.setNeutralButton(R.string.app_chooser_none) { _, _ -> finish(AppChoice.None) }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> finish(AppChoice.Cancelled) }
                        .setOnCancelListener { finish(AppChoice.Cancelled) }
                        .create()
                shown = dialog
                continuation.invokeOnCancellation { activity.runOnUiThread { shown?.dismiss() } }
                if (continuation.isActive) {
                    dialog.show()
                    protect(dialog)
                }
            }
        }

    // Every button and the content reject touches while another window overlays the dialog.
    private fun protect(dialog: AlertDialog) {
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
            .forEach { dialog.getButton(it)?.filterTouchesWhenObscured = true }
        protectConfirmationTouches(dialog.findViewById(android.R.id.content))
    }

    private class AppAdapter(
        private val activity: Activity,
        private val apps: List<LaunchableAppResolver.Candidate>,
    ) : BaseAdapter() {
        override fun getCount(): Int = apps.size

        override fun getItem(position: Int): LaunchableAppResolver.Candidate = apps[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup?,
        ): View =
            (convertView as? TextView ?: TextView(activity)).apply {
                val app = apps[position]
                text = activity.getString(R.string.app_chooser_row, app.label, app.packageName)
                val padding = activity.resources.getDimensionPixelSize(R.dimen.study_card_padding)
                setPadding(padding, padding, padding, padding)
                val icon =
                    try {
                        activity.packageManager.getApplicationIcon(app.packageName)
                    } catch (_: PackageManager.NameNotFoundException) {
                        activity.packageManager.defaultActivityIcon
                    }
                val size = activity.resources.getDimensionPixelSize(R.dimen.app_chooser_icon)
                icon.setBounds(0, 0, size, size)
                compoundDrawablePadding = padding
                setCompoundDrawables(icon, null, null, null)
            }
    }
}

/** Labels are read live; preferences contain only opaque name hashes and selected packages. */
object RememberedAppsSettings {
    fun show(activity: Activity): AlertDialog {
        val memory = RememberedApps(activity)
        val entries = memory.entries().toList()
        val labels =
            entries
                .map { (_, pkg) ->
                    val label =
                        try {
                            val info = activity.packageManager.getApplicationInfo(pkg, 0)
                            activity.packageManager.getApplicationLabel(info).toString()
                        } catch (_: PackageManager.NameNotFoundException) {
                            pkg
                        }
                    activity.getString(R.string.app_chooser_row, label, pkg)
                }.toTypedArray()
        val builder =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.remembered_apps_title)
                .setNegativeButton(android.R.string.cancel, null)
        if (entries.isEmpty()) {
            builder.setMessage(R.string.remembered_apps_empty)
        } else {
            builder.setItems(labels) { _, index ->
                AlertDialog
                    .Builder(activity)
                    .setTitle(R.string.remembered_apps_delete)
                    .setMessage(labels[index])
                    .setPositiveButton(R.string.remembered_apps_delete) { _, _ ->
                        memory.delete(entries[index].first)
                        show(activity)
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        return builder.show()
    }
}
