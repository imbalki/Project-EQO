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
    private var warning: AlertDialog? = null

    fun activate() {
        active = true
    }

    fun pause() {
        active = false
        generation++
        dialog?.dismiss()
        dialog = null
        warning?.dismiss()
        warning = null
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
                val result = repository.load()
                activity.runOnUiThread {
                    if (activity.isDestroyed || activity.isFinishing) return@runOnUiThread
                    if (active && generation == current) {
                        button.isEnabled = true
                        show(orderedModels(result.models), button)
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
        val recommended = defaultAudioModel(models)
        val builder = AlertDialog.Builder(activity).setTitle(R.string.voice_model_title)
        if (models.isEmpty()) {
            builder.setMessage(R.string.voice_models_unavailable)
        } else {
            builder.setSingleChoiceItems(
                models.map { modelLabel(it, recommended) }.toTypedArray(),
                models.indexOfFirst { it.id == settings.audioModel },
            ) { dialog, which ->
                val selected = models[which]
                if ("audio" in selected.inputModalities) {
                    settings.audioModel = selected.id
                    label(button)
                    dialog.dismiss()
                } else {
                    (dialog as AlertDialog).listView.setItemChecked(which, false)
                    dialog.listView.setItemChecked(models.indexOfFirst { it.id == settings.audioModel }, true)
                    warning?.dismiss()
                    warning =
                        AlertDialog
                            .Builder(activity)
                            .setMessage(R.string.voice_ai_unsupported)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                }
            }
        }
        dialog = builder.setNegativeButton(android.R.string.cancel, null).show()
    }

    companion object {
        fun orderedModels(models: List<OpenRouterModel>): List<OpenRouterModel> =
            models.sortedWith(compareBy<OpenRouterModel> { "audio" !in it.inputModalities }.thenBy { it.id })

        fun audioModels(models: List<OpenRouterModel>): List<OpenRouterModel> =
            models.filter { "audio" in it.inputModalities }.sortedBy { it.id }

        fun defaultAudioModel(models: List<OpenRouterModel>): String? {
            val audio = audioModels(models)
            return audio
                .lastOrNull {
                    it.id.startsWith("google/gemini-") && it.id.endsWith("-flash")
                }?.id ?: audio.firstOrNull()?.id
        }

        fun selectedModel(
            context: Context,
            settings: VoiceSettings,
            models: List<OpenRouterModel> = cachedModels(context),
        ): String {
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

        fun loadModels(context: Context): List<OpenRouterModel> =
            OpenRouterModelRepository(AndroidModelCatalogCache(context), OpenRouterModelHttp()::fetch).load().models
    }

    private fun modelLabel(
        model: OpenRouterModel,
        recommended: String?,
    ): String {
        val capability = if ("audio" in model.inputModalities) R.string.voice_model_audio else R.string.voice_model_text
        val label = activity.getString(R.string.voice_model_row, model.name, model.id, activity.getString(capability))
        return if (model.id == recommended) activity.getString(R.string.voice_model_recommended, label) else label
    }
}
