// Origin: EQO-authored non-secret voice preferences and setup controls.
package ai.eqo.task

import ai.eqo.R
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Button
import androidx.core.content.edit
import java.util.Locale

internal enum class VoiceEngine { PHONE, OPENROUTER }

internal class VoiceSettings(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("eqo_voice_settings", Context.MODE_PRIVATE)
    var engine: VoiceEngine
        get() = if (prefs.getBoolean("ai_engine", false)) VoiceEngine.OPENROUTER else VoiceEngine.PHONE
        set(value) {
            prefs.edit { putBoolean("ai_engine", value == VoiceEngine.OPENROUTER) }
        }
    var language: String
        get() = prefs.getString("language", null) ?: Locale.getDefault().toLanguageTag()
        set(value) {
            prefs.edit { putString("language", value) }
        }
    var consent: Boolean
        get() = prefs.getBoolean("audio_consent", false)
        set(value) {
            prefs.edit { putBoolean("audio_consent", value) }
        }

    companion object {
        fun bind(activity: Activity) {
            val settings = VoiceSettings(activity)
            activity.findViewById<Button>(R.id.voice_engine_setting).setOnClickListener {
                AlertDialog
                    .Builder(activity)
                    .setTitle(R.string.voice_engine_title)
                    .setSingleChoiceItems(
                        activity.resources.getStringArray(R.array.voice_engines),
                        settings.engine.ordinal,
                    ) { dialog, which ->
                        settings.engine = VoiceEngine.entries[which]
                        dialog.dismiss()
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            val languages = linkedSetOf(Locale.getDefault().toLanguageTag(), "en-IN", "hi-IN")
            val details =
                RecognizerIntent.getVoiceDetailsIntent(activity)
                    ?: Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS)
            activity.sendOrderedBroadcast(
                details,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) {
                        getResultExtras(false)
                            ?.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)
                            ?.let(languages::addAll)
                    }
                },
                null,
                Activity.RESULT_OK,
                null,
                null,
            )
            activity.findViewById<Button>(R.id.voice_language_setting).setOnClickListener {
                val tags = listOf("") + languages.toList()
                val labels = listOf(activity.getString(R.string.voice_language_default)) + languages.toList()
                AlertDialog
                    .Builder(activity)
                    .setTitle(R.string.voice_language_title)
                    .setItems(labels.toTypedArray()) { _, which ->
                        if (which == 0) settings.prefs.edit { remove("language") } else settings.language = tags[which]
                    }.setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }
}
