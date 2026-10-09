// Origin: EQO edge-handle task; app-private choice and placement, no screen content.
package ai.eqo.accessibility.handle

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class HandlePreferences(
    context: Context,
) : HandleChoiceStore {
    val preferences: SharedPreferences = context.getSharedPreferences("edge_handle", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean("handle_enabled", false)
        set(value) = preferences.edit { putBoolean("handle_enabled", value) }

    var rightEdge: Boolean
        get() = preferences.getBoolean("right_edge", true)
        set(value) = preferences.edit { putBoolean("right_edge", value) }

    var verticalFraction: Float
        get() = preferences.getFloat("vertical_fraction", DEFAULT_VERTICAL_FRACTION).coerceIn(0f, 1f)
        set(value) = preferences.edit { putFloat("vertical_fraction", value.coerceIn(0f, 1f)) }

    fun isHidden(packageName: String): Boolean = packageName in hiddenApps()

    fun hide(packageName: String) {
        preferences.edit { putStringSet("hidden_apps", hiddenApps() + packageName) }
    }

    fun clearHiddenApps() {
        preferences.edit { remove("hidden_apps") }
    }

    private fun hiddenApps(): Set<String> = preferences.getStringSet("hidden_apps", emptySet()).orEmpty().toSet()

    override fun load(): HandleChoice =
        HandleChoice(
            preferences
                .getString("order", "")
                .orEmpty()
                .split(',')
                .filter { it.isNotEmpty() },
            preferences.getStringSet("known", emptySet()).orEmpty().toSet(),
            preferences.getStringSet("enabled", emptySet()).orEmpty().toSet(),
        )

    override fun save(choice: HandleChoice) {
        preferences.edit {
            putString("order", choice.order.joinToString(","))
            putStringSet("known", choice.known)
            putStringSet("enabled", choice.enabled)
        }
    }

    private companion object {
        const val DEFAULT_VERTICAL_FRACTION = 0.4f
    }
}

/** Application installs feature registrations; service and settings render the same registry. */
object EdgeHandleFeatures {
    var registry: HandleShortcutRegistry<Context>? = null
        private set

    fun install(registry: HandleShortcutRegistry<Context>) {
        this.registry = registry
    }
}
