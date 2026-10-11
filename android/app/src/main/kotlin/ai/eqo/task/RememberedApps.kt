// Origin: EQO app-only choice memory. Raw names and request text are never stored.
package ai.eqo.task

import ai.eqo.actions.impl.AppNameMatcher
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest

internal class RememberedApps(
    private val preferences: SharedPreferences,
) {
    constructor(context: Context) : this(context.getSharedPreferences("eqo_remembered_apps", Context.MODE_PRIVATE))

    fun packageFor(name: String): String? = preferences.getString(key(name), null)

    fun remember(
        name: String,
        packageName: String,
    ) {
        preferences.edit { putString(key(name), packageName) }
    }

    fun entries(): Map<String, String> =
        preferences.all
            .mapNotNull { (key, value) ->
                (value as? String)?.let { key to it }
            }.toMap()

    fun delete(key: String) {
        preferences.edit { remove(key) }
    }

    private fun key(name: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(AppNameMatcher.normalize(name).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
