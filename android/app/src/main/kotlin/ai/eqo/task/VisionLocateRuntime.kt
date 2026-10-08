// Origin: EQO vision-locate, approved-run Android/provider composition.
package ai.eqo.task

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.AndroidVisionLocateScreen
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.GenericAppAutomator
import ai.eqo.core.agent.ApprovedTaskPlan
import ai.eqo.core.agent.ExecuteResult
import ai.eqo.core.agent.LoopStep
import ai.eqo.core.agent.VisionLocateRun
import ai.eqo.core.llm.ControlLocator
import ai.eqo.core.llm.VisionControlLocator
import ai.eqo.core.llm.providers.OpenRouterModelHttp
import ai.eqo.core.llm.providers.OpenRouterModelRepository
import ai.eqo.core.llm.providers.OpenRouterProvider
import ai.eqo.core.security.AndroidProviderCredentialStore
import ai.eqo.data.repository.SettingsRepository
import ai.eqo.onboarding.AndroidModelCatalogCache
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object VisionLocateSettings {
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("eqo_plan_settings", Context.MODE_PRIVATE).getBoolean("vision_locate", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("eqo_plan_settings", Context.MODE_PRIVATE).edit { putBoolean("vision_locate", enabled) }
    }
}

/** Not installed in donor automators: only this immutable, approved foreground plan may use vision. */
class VisionLocateRuntime(
    private val context: Context,
    private val plan: ApprovedTaskPlan,
    private val textAction: suspend (String, String?) -> A11yResult = { label, content ->
        if (content == null) GenericAppAutomator.clickText(label) else GenericAppAutomator.typeText(label, content)
    },
    private val typeFocused: (String) -> A11yResult? = {
        EQOAccessibilityService.getInstance()?.automation?.type("focused", it)
    },
    private val locateOverride: (suspend (String) -> ExecuteResult)? = null,
) {
    private val attemptedSteps = mutableSetOf<String>()
    private val fallback =
        VisionLocateRun(
            enabled = { plan.visionAllowed && VisionLocateSettings.enabled(context) },
            locator = { configuredLocator() },
            screen = AndroidVisionLocateScreen(),
            diagnostic = { android.util.Log.i("EqoRun", it) },
        )

    /** null means normal dispatch. Typed non-NodeNotFound failures never invoke capture or the model. */
    suspend fun execute(step: LoopStep): ExecuteResult? {
        if (!plan.visionAllowed || step.action.name !in VERBS) return null
        if (!plan.permits(step)) return ExecuteResult.Failure("Step was not approved")
        if (!attemptedSteps.add(step.stepId)) return ExecuteResult.Failure("EQO will not repeat a screen lookup for this step.")
        val params = step.action.params
        val typing = step.action.name in setOf("type_text", "TYPE_TEXT")
        val label = params[if (typing) if (step.action.name == "TYPE_TEXT") "searchText" else "target" else "text"].orEmpty()
        val content = params[if (step.action.name == "TYPE_TEXT") "content" else "text"].orEmpty()
        val accessible =
            textAction(label, if (typing) content else null)
        if (accessible.isSuccess) return ExecuteResult.Success("The approved control was found through screen control.")
        val error = (accessible as A11yResult.Failure).error
        if (error !is A11yError.NodeNotFound) return ExecuteResult.Failure("EQO screen control refused the approved action.")
        // 'focused/current' is not an approved visible label. Never turn it into a visual guess.
        if (label.lowercase() in setOf("focused", "current")) return ExecuteResult.Failure("EQO could not find the focused text field.")
        val located = locateOverride?.invoke(label) ?: fallback.locateAndTap(label)
        if (!typing || located !is ExecuteResult.Success) return located
        // Same approved text only; require an actually focused, non-password field. No model-authored text.
        val typed = typeFocused(content)
        return if (typed?.isSuccess == true) {
            ExecuteResult.Success("The approved text was entered after locating its control.")
        } else {
            ExecuteResult.Failure("EQO found the control but could not enter the approved text safely.")
        }
    }

    private suspend fun configuredLocator(): ControlLocator? =
        withContext(Dispatchers.IO) {
            val model = StudyModelChoice.read(context)
            if (model.isNullOrBlank()) {
                null
            } else {
                val models =
                    OpenRouterModelRepository(AndroidModelCatalogCache(context), OpenRouterModelHttp()::fetch).load().models
                if (models.none { it.id == model && it.acceptsImages }) {
                    null
                } else {
                    val client =
                        OkHttpClient.Builder()
                            .followRedirects(false)
                            .followSslRedirects(false)
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(25, TimeUnit.SECONDS)
                            .callTimeout(30, TimeUnit.SECONDS)
                            .build()
                    val provider = OpenRouterProvider(client, SettingsRepository(context, AndroidProviderCredentialStore(context)))
                    VisionControlLocator(provider, model)
                }
            }
        }

    companion object {
        val VERBS = setOf("tap_text", "type_text", "CLICK_TEXT", "TYPE_TEXT")
    }
}
