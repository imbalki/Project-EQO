// Origin: EQO-authored audio-only selection from the shared OpenRouter catalog; no credentials stored.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.llm.providers.AudioUnsupportedException
import ai.eqo.core.llm.providers.OpenRouterModel
import ai.eqo.core.llm.providers.OpenRouterModelCatalog
import ai.eqo.core.llm.providers.OpenRouterModelHttp
import ai.eqo.core.llm.providers.OpenRouterModelRepository
import ai.eqo.onboarding.AndroidModelCatalogCache
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.widget.Button

internal class VoiceModelPicker(
    private val activity: Activity,
    private val settings: VoiceSettings,
    private val repository: OpenRouterModelRepository =
        OpenRouterModelRepository(AndroidModelCatalogCache(activity), OpenRouterModelHttp()::fetch),
    private val loadInBackground: (() -> Unit) -> Unit = { Thread(it).start() },
) {
    private var active = true
    private var generation = 0
    private var dialog: AlertDialog? = null

    fun activate() {
        active = true
    }

    fun pause() {
        active = false
        generation++
        dialog?.dismiss()
        dialog = null
        activity.findViewById<Button>(R.id.voice_model_setting).apply {
            isEnabled = true
            label(this)
        }
    }

    fun bind() {
        val button = activity.findViewById<Button>(R.id.voice_model_setting)
        label(button)
        button.setOnClickListener {
            if (!active) return@setOnClickListener
            val current = ++generation
            button.isEnabled = false
            button.setText(R.string.model_catalog_loading)
            loadInBackground {
                val result = repository.load(force = true)
                activity.runOnUiThread {
                    if (activity.isDestroyed || activity.isFinishing) return@runOnUiThread
                    if (active && generation == current) {
                        button.isEnabled = true
                        show(audioModels(result.models), button)
                    }
                }
            }
        }
    }

    private fun label(button: Button) {
        val models = cachedModels(activity)
        if (settings.audioModel == null) settings.audioModel = defaultAudioModel(models)
        val selected = settings.audioModel
        if (selected == null) {
            button.setText(R.string.voice_model_title)
        } else {
            button.text = activity.getString(R.string.voice_model_selected, selected)
        }
    }

    private fun show(
        models: List<OpenRouterModel>,
        button: Button,
    ) {
        if (settings.audioModel == null) settings.audioModel = defaultAudioModel(models)
        label(button)
        val builder = AlertDialog.Builder(activity).setTitle(R.string.voice_model_title)
        if (models.isEmpty()) {
            builder.setMessage(R.string.voice_models_unavailable)
        } else {
            builder.setSingleChoiceItems(
                models.map { activity.getString(R.string.voice_model_row, it.name, it.id) }.toTypedArray(),
                models.indexOfFirst { it.id == settings.audioModel },
            ) { dialog, which ->
                settings.audioModel = models[which].id
                label(button)
                dialog.dismiss()
            }
        }
        dialog = builder.setNegativeButton(android.R.string.cancel, null).show()
    }

    companion object {
        fun audioModels(models: List<OpenRouterModel>): List<OpenRouterModel> =
            models.filter { "audio" in it.inputModalities }.sortedBy { it.id }

        fun defaultAudioModel(models: List<OpenRouterModel>): String? {
            val audio = audioModels(models)
            return audio.firstOrNull { it.id == "google/gemini-2.5-flash" }?.id ?: audio.firstOrNull()?.id
        }

        fun selectedModel(
            context: Context,
            settings: VoiceSettings,
        ): String {
            val models = cachedModels(context)
            val selected = settings.audioModel ?: defaultAudioModel(models) ?: StudyModelChoice.read(context)
            if (selected == null) throw AudioUnsupportedException()
            if (models.isNotEmpty() && audioModels(models).none { it.id == selected }) throw AudioUnsupportedException()
            settings.audioModel = selected
            return selected
        }

        private fun cachedModels(context: Context): List<OpenRouterModel> =
            AndroidModelCatalogCache(context)
                .read()
                ?.let {
                    runCatching { OpenRouterModelCatalog.parse(it.json) }.getOrDefault(emptyList())
                }.orEmpty()
    }
}
