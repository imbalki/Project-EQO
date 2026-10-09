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
    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val CALL_TIMEOUT_SECONDS = 90L

    fun planner(
        context: Context,
        enabledActions: Set<String>,
    ): TaskPlanner? {
        val store = AndroidProviderCredentialStore(context.applicationContext)
        val key = (store.read(ProviderCredentialId.ApiKey("openrouter")) as? CredentialStoreResult.Success)?.value
        val model = StudyModelChoice.read(context)
        if (key.isNullOrBlank() || model.isNullOrBlank()) return null
        val provider = voiceProvider(context)
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

    // Voice uses the same provider, encrypted BYOK store and network policy; no separate network client.
    private val client by lazy {
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            // Planning waits for a whole model answer; OkHttp's 10 s default timed out on slower replies
            // (SocketTimeoutException, captured 2026-10-07). A bounded, longer budget instead.
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    fun voiceProvider(context: Context): OpenRouterProvider =
        OpenRouterProvider(
            client,
            SettingsRepository(context.applicationContext, AndroidProviderCredentialStore(context.applicationContext)),
        )
}
