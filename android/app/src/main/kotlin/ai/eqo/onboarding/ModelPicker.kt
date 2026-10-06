package ai.eqo.onboarding

import ai.eqo.R
import ai.eqo.core.llm.providers.CachedModelCatalog
import ai.eqo.core.llm.providers.ModelCatalogCache
import ai.eqo.core.llm.providers.ModelCatalogResult
import ai.eqo.core.llm.providers.OpenRouterModel
import ai.eqo.core.llm.providers.OpenRouterModelCatalog
import ai.eqo.core.llm.providers.OpenRouterModelHttp
import ai.eqo.core.llm.providers.OpenRouterModelRepository
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.edit
import java.text.DateFormat
import java.util.Date

/** Same non-secret preference seam as the typed-request planner; never stores the key. */
object ModelSelection {
    fun read(context: Context): String? =
        context.getSharedPreferences("study_model_choice", Context.MODE_PRIVATE).getString("openrouter_model", null)

    fun save(
        context: Context,
        model: String,
    ): Boolean {
        val prefs = context.getSharedPreferences("study_model_choice", Context.MODE_PRIVATE)
        prefs.edit(commit = true) { putString("openrouter_model", model) }
        return prefs.getString("openrouter_model", null) == model
    }
}

class AndroidModelCatalogCache(
    context: Context,
) : ModelCatalogCache {
    private val prefs = context.getSharedPreferences("openrouter_model_catalog", Context.MODE_PRIVATE)

    override fun read(): CachedModelCatalog? =
        prefs.getString("json", null)?.let {
            CachedModelCatalog(it, prefs.getLong("fetched_at", 0), prefs.getLong("attempted_at", 0))
        }

    override fun write(value: CachedModelCatalog): Boolean {
        prefs.edit(commit = true) {
            putString("json", value.json)
            putLong("fetched_at", value.fetchedAt)
            putLong("attempted_at", value.attemptedAt)
        }
        return prefs.getLong("attempted_at", -1) == value.attemptedAt
    }
}

/** Bounded-height searchable list; manual entry remains available even with no catalog. */
class ModelPicker(
    private val activity: Activity,
    private val repository: OpenRouterModelRepository =
        OpenRouterModelRepository(AndroidModelCatalogCache(activity.applicationContext), OpenRouterModelHttp()::fetch),
) {
    private var result = ModelCatalogResult(emptyList(), null, false, false)
    private var loading = false
    private var dialog: AlertDialog? = null
    private var updateDialog: (() -> Unit)? = null
    private val selected get() = activity.findViewById<TextView>(R.id.model_model_input)

    fun start() {
        if (selected.text.isBlank()) selected.text = ModelSelection.read(activity).orEmpty()
        activity.findViewById<Button>(R.id.model_picker_button).setOnClickListener { show() }
        activity.findViewById<Button>(R.id.model_picker_manual).setOnClickListener { manual() }
        activity.findViewById<Button>(R.id.model_picker_refresh).setOnClickListener { refresh(true) }
        refresh(false)
    }

    fun close() {
        updateDialog = null
        dialog?.dismiss()
    }

    private fun refresh(force: Boolean) {
        if (loading) return
        loading = true
        status()
        Thread {
            val loaded = repository.load(force)
            activity.runOnUiThread {
                if (!activity.isDestroyed && !activity.isFinishing) {
                    result = loaded
                    loading = false
                    status()
                    updateDialog?.invoke()
                }
            }
        }.start()
    }

    private fun status() {
        val message =
            when {
                loading -> activity.getString(R.string.model_catalog_loading)
                result.offline && result.models.isEmpty() -> activity.getString(R.string.model_catalog_unavailable)
                result.offline -> activity.getString(R.string.model_catalog_offline)
                result.models.isEmpty() -> activity.getString(R.string.model_catalog_unavailable)
                result.cached -> activity.getString(R.string.model_catalog_cached)
                else -> activity.getString(R.string.model_catalog_live)
            }
        val dated = result.fetchedAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
        activity.findViewById<TextView>(R.id.model_catalog_status).text =
            if (dated == null) message else activity.getString(R.string.model_catalog_date, message, dated)
        activity.findViewById<Button>(R.id.model_picker_refresh).isEnabled = !loading
    }

    private fun show() {
        val layout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val filter =
            EditText(activity).apply {
                setHint(R.string.model_picker_filter)
                minHeight = pixels(TOUCH_TARGET_DP)
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
        val empty = TextView(activity).apply { setText(R.string.model_picker_no_matches) }
        val list = ListView(activity)
        layout.addView(filter)
        layout.addView(empty)
        layout.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, pixels(LIST_HEIGHT_DP)))
        var visible = emptyList<OpenRouterModel>()
        val update = {
            visible = OpenRouterModelCatalog.search(result.models, selected.text.toString(), filter.text.toString())
            list.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, visible.map { row(it) })
            empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        }
        filter.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    update()
                }

                override fun afterTextChanged(s: Editable?) = Unit
            },
        )
        dialog =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.model_picker_title)
                .setView(layout)
                .setNegativeButton(R.string.model_picker_cancel, null)
                .create()
        list.setOnItemClickListener { _, _, position, _ ->
            selected.text = visible[position].id
            dialog?.dismiss()
        }
        updateDialog = update
        dialog?.setOnDismissListener { updateDialog = null }
        update()
        dialog?.show()
    }

    private fun row(model: OpenRouterModel): String =
        activity.getString(
            R.string.model_picker_row,
            model.name,
            model.id,
            model.provider,
            model.contextLength?.toString() ?: "n/a",
            OpenRouterModelCatalog.pricePerMillion(model.inputPrice),
            OpenRouterModelCatalog.pricePerMillion(model.outputPrice),
        )

    private fun manual() {
        val input =
            EditText(activity).apply {
                setHint(R.string.model_model_hint)
                setText(selected.text)
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                filters = arrayOf(android.text.InputFilter.LengthFilter(MAX_MODEL_ID_LENGTH))
            }
        dialog =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.model_picker_manual)
                .setView(input)
                .setPositiveButton(R.string.model_picker_use) { _, _ -> selected.text = input.text.toString().trim() }
                .setNegativeButton(R.string.model_picker_cancel, null)
                .create()
        dialog?.show()
    }

    private fun pixels(dp: Int): Int = (dp * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val TOUCH_TARGET_DP = 48
        const val LIST_HEIGHT_DP = 320
        const val MAX_MODEL_ID_LENGTH = 256
    }
}
