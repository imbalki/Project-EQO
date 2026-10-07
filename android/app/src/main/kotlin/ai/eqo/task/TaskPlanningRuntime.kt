package ai.eqo.task

import ai.eqo.core.agent.TaskPlanner
import ai.eqo.core.llm.WrappedLLMProvider
import ai.eqo.core.llm.providers.OpenRouterProvider
import ai.eqo.core.security.AndroidProviderCredentialStore
import ai.eqo.core.security.CredentialStoreResult
import ai.eqo.core.security.ProviderCredentialId
import ai.eqo.data.models.LLMConfig
import ai.eqo.data.repository.SettingsRepository
import android.content.Context
import androidx.core.content.edit
import okhttp3.OkHttpClient

/** Non-secret model choice from the successful ConnectionTest, shared across app restarts. */
object StudyModelChoice {
    private const val PREFS = "study_model_choice"
    private const val MODEL = "openrouter_model"

    fun save(
        context: Context,
        model: String,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(MODEL, model) }
    }

    fun read(context: Context): String? =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(MODEL, null)
}

object TaskPlanningRuntime {
    fun planner(
        context: Context,
        enabledActions: Set<String>,
    ): TaskPlanner? {
        val store = AndroidProviderCredentialStore(context.applicationContext)
        val key = (store.read(ProviderCredentialId.ApiKey("openrouter")) as? CredentialStoreResult.Success)?.value
        val model = StudyModelChoice.read(context)
        if (key.isNullOrBlank() || model.isNullOrBlank()) return null
        // No interceptors and no redirects: only the provider's hard-coded OpenRouter endpoint.
        val client =
            OkHttpClient
                .Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        val provider = OpenRouterProvider(client, SettingsRepository(context.applicationContext, store))
        val config =
            LLMConfig(
                activeProvider = "OpenRouter",
                activeModel = model,
                selectedModels = mapOf("OpenRouter" to model),
                apiKeys = mapOf("OpenRouter" to key),
            )
        // Reuse the donor's typed failure mapping, bounded retries and request-lifetime redaction.
        return TaskPlanner(WrappedLLMProvider(provider, configProvider = { config }), enabledActions) {
            android.util.Log.i("EqoRun", it)
        }
    }
}
