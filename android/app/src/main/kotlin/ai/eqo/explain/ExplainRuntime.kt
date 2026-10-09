package ai.eqo.explain

import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.UntrustedScreenText
import ai.eqo.core.llm.LLMRequest
import ai.eqo.core.llm.ProviderRequestConfig
import ai.eqo.core.llm.ResponseFormat
import ai.eqo.core.llm.providers.OpenRouterProvider
import ai.eqo.core.security.AndroidProviderCredentialStore
import ai.eqo.core.security.CredentialStoreResult
import ai.eqo.core.security.ProviderCredentialId
import ai.eqo.data.models.ChatMessage
import ai.eqo.data.repository.SettingsRepository
import ai.eqo.onboarding.AndroidModelCatalogCache
import ai.eqo.task.StudyModelChoice
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Reads the active app only, never searches behind EQO or the notification shade. */
class AndroidExplainSource(
    private val service: EQOAccessibilityService,
    private val rootReader: () -> AccessibilityNodeInfo? = { service.rootInActiveWindow },
    private val capture: suspend () -> String? = { service.takeScreenshotAndEncode() },
) : ExplainSource {
    private var original: ExplainScreen? = null

    override fun read(): ExplainScreen {
        val root = rootReader() ?: error("No foreground screen")
        try {
            val app = root.packageName?.toString().orEmpty()
            check(app.isNotBlank() && app != service.packageName && app != "com.android.systemui") {
                "Open another app first"
            }
            val collector = ExplainTextCollector(service.packageName)
            collect(root, app, collector, 0)
            return collector.result(app).also { if (original == null) original = it }
        } finally {
            root.recycle()
        }
    }

    override suspend fun screenshot(): String? {
        val saved = original ?: return null
        val now = runCatching { read() }.getOrNull() ?: return null
        // Never send a different screen in a follow-up or capture EQO's own sheet.
        if (saved.app != now.app || saved.text != now.text || now.hasPassword || !now.completeTree) return null
        val image = capture()
        val after = runCatching { read() }.getOrNull() ?: return null
        return image?.takeIf {
            saved.app == after.app &&
                saved.text == after.text &&
                !after.hasPassword &&
                after.completeTree &&
                it.length <= MAX_IMAGE_CHARS
        }
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        app: String,
        collector: ExplainTextCollector,
        depth: Int,
    ) {
        if (!collector.hasCapacity() || depth >= MAX_DEPTH) {
            collector.markIncomplete()
            return
        }
        collector.add(
            node.packageName?.toString() ?: app,
            if (node.isPassword || !node.isVisibleToUser || node.packageName?.toString()?.let { it != app } == true) {
                null
            } else {
                node.text?.toString()?.takeIf { it.isNotBlank() } ?: node.contentDescription?.toString()
            },
            node.isPassword,
            node.isVisibleToUser,
        )
        // Password subtrees and foreign-package subtrees are excluded entirely.
        if (node.isPassword || node.packageName?.toString()?.let { it != app } == true) return
        for (index in 0 until node.childCount.coerceAtMost(ExplainSession.MAX_NODES)) {
            if (!collector.hasCapacity()) break
            val child = node.getChild(index) ?: continue
            try {
                collect(child, app, collector, depth + 1)
            } finally {
                child.recycle()
            }
        }
        if (node.childCount > ExplainSession.MAX_NODES) collector.markIncomplete()
    }

    companion object {
        private const val MAX_DEPTH = 50
        private const val MAX_IMAGE_CHARS = 4_000_000
    }
}

/** No automation tools, history store, interceptors or screen-content logging. */
class AndroidExplainModel(
    private val context: Context,
) : ExplainModel {
    private val model = StudyModelChoice.read(context) ?: error("No model set")
    private val store = AndroidProviderCredentialStore(context.applicationContext)
    private val key =
        (store.read(ProviderCredentialId.ApiKey("openrouter")) as? CredentialStoreResult.Success)
            ?.value
            ?.takeIf { it.isNotBlank() } ?: error("No key set")
    private val client =
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    private val provider = OpenRouterProvider(client, SettingsRepository(context.applicationContext, store))

    override suspend fun supportsImages(): Boolean =
        withContext(Dispatchers.IO) {
            // Unknown/manual model IDs fail to text-only; never guess from a model's name.
            val json = AndroidModelCatalogCache(context).read()?.json ?: return@withContext false
            imageCapability(json, model)
        }

    override suspend fun answer(
        screen: ExplainScreen,
        image: String?,
        question: String,
    ): String =
        provider
            .complete(
                LLMRequest(
                    model = model,
                    providerConfig = ProviderRequestConfig(key, "https://openrouter.ai/api/v1/chat/completions"),
                    systemPrompt =
                        UntrustedScreenText.IMAGE_DIRECTIVE + " " + UntrustedScreenText.DIRECTIVE +
                            " Explain the Android screen in plain language in ${Locale.getDefault().toLanguageTag()}. " +
                            "Name the app/screen, describe main controls and suggest the next step. " +
                            "Explain formulas and code in simple steps. Only explain; never execute actions. " +
                            "Say when the available screen text is insufficient. Do not invent unseen controls.",
                    messages =
                        listOf(
                            ChatMessage(
                                id = "explain",
                                sender = ChatMessage.Sender.USER,
                                text =
                                    UntrustedScreenText.wrap("App: ${screen.app}\n${screen.text}") +
                                        "\nUser question: $question",
                                imageBase64 = image,
                            ),
                        ),
                    maxTokens = 1500,
                    responseFormat = ResponseFormat.TEXT,
                ),
            ).content
            .trim()
            .also { check(it.isNotBlank()) { "Model refused" } }

    companion object {
        fun imageCapability(
            json: String,
            model: String,
        ): Boolean =
            runCatching {
                val data = JSONObject(json).getJSONArray("data")
                (0 until data.length()).any { index ->
                    val item = data.getJSONObject(index)
                    val inputs = item.optJSONObject("architecture")?.optJSONArray("input_modalities")
                    item.optString("id") == model &&
                        inputs != null &&
                        (0 until inputs.length()).any { inputs.optString(it) == "image" }
                }
            }.getOrDefault(false)
    }
}
