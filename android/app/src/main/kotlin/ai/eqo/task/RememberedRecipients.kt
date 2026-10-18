// Origin: EQO recipient recovery; local owner-approved aliases, never telemetry.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.agent.isLiteralEmail
import ai.eqo.core.agent.isLiteralPhone
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Locale

internal class RememberedRecipients(
    private val preferences: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences("eqo_remembered_recipients", Context.MODE_PRIVATE),
    )

    fun destination(
        name: String,
        email: Boolean,
    ): String? = preferences.getString(key(name, email), null)

    fun remember(
        name: String,
        email: Boolean,
        destination: String,
    ) {
        require(if (email) isLiteralEmail(destination) else isLiteralPhone(destination))
        preferences.edit { putString(key(name, email), destination.trim()) }
    }

    fun entries(): Map<String, String> =
        preferences.all
            .mapNotNull { (key, value) ->
                (value as? String)?.let { key to it }
            }.toMap()

    fun delete(key: String) {
        preferences.edit { remove(key) }
    }

    private fun key(
        name: String,
        email: Boolean,
    ): String = (if (email) "Email: " else "Phone: ") + name.trim().lowercase(Locale.ROOT)
}

internal object RememberedRecipientsSettings {
    fun show(activity: Activity): AlertDialog {
        val memory = RememberedRecipients(activity)
        val entries = memory.entries().toList()
        val rows =
            entries
                .map { (key, value) ->
                    activity.getString(R.string.remembered_recipients_row, key, value)
                }.toTypedArray()
        val builder =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.remembered_recipients_title)
                .setNegativeButton(android.R.string.cancel, null)
        if (entries.isEmpty()) {
            builder.setMessage(R.string.remembered_recipients_empty)
        } else {
            builder.setItems(rows) { _, index ->
                AlertDialog
                    .Builder(activity)
                    .setTitle(R.string.remembered_apps_delete)
                    .setMessage(rows[index])
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
