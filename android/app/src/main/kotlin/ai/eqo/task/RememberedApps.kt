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

    /** The app the person chose to always use for a generic role such as "browser"; stored by package only. */
    fun packageForRole(role: String): String? = preferences.getString(roleKey(role), null)

    /** The remembered role app's label, or null when none is remembered or it is no longer installed. */
    fun labelForRole(
        role: String,
        apps: List<Pair<String, String>>,
    ): String? = packageForRole(role)?.let { pkg -> apps.firstOrNull { it.first == pkg }?.second }

    fun rememberRole(
        role: String,
        packageName: String,
    ) {
        preferences.edit { putString(roleKey(role), packageName) }
    }

    fun entries(): Map<String, String> =
        preferences.all
            .mapNotNull { (key, value) ->
                (value as? String)?.let { key to it }
            }.toMap()

    fun delete(key: String) {
        preferences.edit { remove(key) }
    }

    private fun key(name: String): String = hash(AppNameMatcher.normalize(name))

    companion object {
        // "role:" survives no app-name normalization, so a role key never collides with an app-name key.
        fun roleKey(role: String): String = hash("role:$role")

        private fun hash(text: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
