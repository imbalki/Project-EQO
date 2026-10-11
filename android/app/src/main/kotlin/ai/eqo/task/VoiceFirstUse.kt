// Origin: EQO-authored first-use voice choice; shared public catalog, no credentials persisted.
package ai.eqo.task

import ai.eqo.R
import ai.eqo.core.llm.providers.OpenRouterModel
import ai.eqo.core.security.AndroidProviderCredentialStore
import ai.eqo.core.security.CredentialStoreResult
import ai.eqo.core.security.ProviderCredentialId
import ai.eqo.onboarding.ModelKeySetupActivity
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

internal class VoiceFirstUse(
    private val activity: Activity,
    private val settings: VoiceSettings,
    private val loadModels: () -> List<OpenRouterModel> = { VoiceModelPicker.loadModels(activity) },
    private val hasKey: () -> Boolean? = {
        val store = AndroidProviderCredentialStore(activity.applicationContext)
        val result = store.read(ProviderCredentialId.ApiKey("openrouter"))
        (result as? CredentialStoreResult.Success)?.let { !it.value.isNullOrBlank() }
    },
    private val loadInBackground: (() -> Unit) -> Unit = { Thread(it).start() },
) {
    private var dialog: AlertDialog? = null
    private var generation = 0

    fun tap(record: () -> Unit) {
        if (settings.hasChoice) record() else show(record)
    }

    fun close() {
        generation++
        dialog?.dismiss()
        dialog = null
    }

    fun show(onChosen: () -> Unit = {}) {
        if (dialog?.isShowing == true) return
        val current = ++generation
        val view = activity.layoutInflater.inflate(R.layout.voice_first_use, null)
        val sheet =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.voice_first_use_title)
                .setView(view)
                .setPositiveButton(R.string.voice_first_use_ai, null)
                .setNeutralButton(R.string.voice_first_use_phone) { _, _ ->
                    settings.engine = VoiceEngine.PHONE
                    onChosen()
                }.setNegativeButton(R.string.voice_choose_later, null)
                .show()
        dialog = sheet
        sheet.window?.setGravity(Gravity.BOTTOM)
        sheet.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        view.findViewById<Button>(R.id.voice_first_use_key).setOnClickListener {
            close()
            activity.startActivity(Intent(activity, ModelKeySetupActivity::class.java))
        }
        loadInBackground {
            val keySaved = hasKey()
            activity.runOnUiThread {
                if (sheet.isShowing) updateKey(view, keySaved)
            }
            val models = VoiceModelPicker.audioModels(loadModels())
            activity.runOnUiThread {
                val hostActive = !activity.isDestroyed && !activity.isFinishing
                if (generation == current && sheet.isShowing && hostActive) {
                    populate(sheet, view, models, keySaved, onChosen)
                }
            }
        }
    }

    private fun updateKey(
        view: View,
        keySaved: Boolean?,
    ) {
        if (keySaved != true) {
            val message =
                if (keySaved == false) {
                    R.string.voice_first_use_key_missing
                } else {
                    R.string.voice_first_use_key_unavailable
                }
            view.findViewById<TextView>(R.id.voice_first_use_status).setText(message)
        }
        val keyButton = view.findViewById<Button>(R.id.voice_first_use_key)
        keyButton.visibility = if (keySaved == true) View.GONE else View.VISIBLE
    }

    private fun populate(
        sheet: AlertDialog,
        view: View,
        models: List<OpenRouterModel>,
        keySaved: Boolean?,
        onChosen: () -> Unit,
    ) {
        val message = view.findViewById<TextView>(R.id.voice_first_use_status)
        if (keySaved == true) message.setText(R.string.voice_model_title)
        val group = view.findViewById<RadioGroup>(R.id.voice_first_use_models)
        val recommended = VoiceModelPicker.defaultAudioModel(models)
        val selected = settings.audioModel?.takeIf { id -> models.any { it.id == id } } ?: recommended
        models.forEachIndexed { index, model ->
            val row = RadioButton(activity)
            row.id = View.generateViewId()
            val label =
                activity.getString(
                    R.string.voice_model_row,
                    model.name,
                    model.id,
                    activity.getString(R.string.voice_model_audio),
                )
            row.text =
                if (model.id == recommended) activity.getString(R.string.voice_model_recommended, label) else label
            row.tag = index
            row.isEnabled = keySaved == true
            group.addView(row)
            if (model.id == selected) group.check(row.id)
        }
        if (models.isEmpty() && keySaved == true) message.setText(R.string.voice_models_unavailable)
        sheet.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            isEnabled = keySaved == true && models.isNotEmpty()
            setOnClickListener {
                val row = group.findViewById<RadioButton>(group.checkedRadioButtonId)
                if (row != null) {
                    settings.audioModel = models[row.tag as Int].id
                    settings.consent = true
                    settings.engine = VoiceEngine.OPENROUTER
                    sheet.dismiss()
                    onChosen()
                }
            }
        }
    }
}
