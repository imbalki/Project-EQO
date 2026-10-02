package ai.eqo.core.security

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-005 review R1: the one-time legacy import must open the UPSTREAM-ERA on-device
 * SharedPreferences file. Upstream-era builds wrote their plaintext preferences to the
 * file named `opendroid_prefs` (upstream `KeystoreSecretStorage.kt`
 * `LEGACY_PREFERENCES_NAME` at 6ff5a06; same value at base a567321). Renaming that file
 * would make the migration read a file no upstream-era build ever wrote, so legacy
 * plaintext secrets/PII would be neither imported nor erased.
 */
class LegacyPlaintextPreferencesSourceTest {
    @Test
    fun `legacy import reads the upstream-era preference file`() {
        val context = RecordingContext()

        val source = LegacyPlaintextPreferencesSource(context)
        val keys = source.keys()

        assertEquals("opendroid_prefs", context.openedPreferenceFile)
        assertTrue(keys is SecretRecordResult.Success)
    }

    /** Records which preference file the legacy source opens; holds no real data. */
    private class RecordingContext : ContextWrapper(null) {
        var openedPreferenceFile: String? = null

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(
            name: String,
            mode: Int,
        ): SharedPreferences {
            openedPreferenceFile = name
            return EmptyPreferences()
        }
    }

    /** Minimal empty in-memory preferences: the test only cares which file is opened. */
    private class EmptyPreferences : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()

        override fun getString(
            key: String,
            defValue: String?,
        ): String? = defValue

        override fun getStringSet(
            key: String,
            defValues: Set<String>?,
        ): Set<String>? = defValues

        override fun getInt(
            key: String,
            defValue: Int,
        ): Int = defValue

        override fun getLong(
            key: String,
            defValue: Long,
        ): Long = defValue

        override fun getFloat(
            key: String,
            defValue: Float,
        ): Float = defValue

        override fun getBoolean(
            key: String,
            defValue: Boolean,
        ): Boolean = defValue

        override fun contains(key: String): Boolean = false

        override fun edit(): SharedPreferences.Editor = EmptyEditor()

        override fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener) = Unit
    }

    private class EmptyEditor : SharedPreferences.Editor {
        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor = this

        override fun putStringSet(
            key: String,
            values: Set<String>?,
        ): SharedPreferences.Editor = this

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor = this

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor = this

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor = this

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor = this

        override fun remove(key: String): SharedPreferences.Editor = this

        override fun clear(): SharedPreferences.Editor = this

        override fun commit(): Boolean = true

        override fun apply() = Unit
    }
}
