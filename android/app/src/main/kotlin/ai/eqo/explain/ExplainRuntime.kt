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
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Reads the active app only, never searches behind EQO or the notification shade. */
class AndroidExplainSource(
    private val service: EQOAccessibilityService,
    private val rootReader: () -> AccessibilityNodeInfo? = { service.rootInActiveWindow },
    private val capture: suspend () -> String? = { service.takeScreenshotAndEncode() },
) : ExplainSource {
    private var original: ExplainScreen? = null
    private var closed = false

    override fun close() {
        closed = true
        original = null
    }

    override fun read(): ExplainScreen {
        check(!closed) { "Session closed" }
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

    @Suppress("ReturnCount") // Every failed revalidation exits before image transmission.
    override suspend fun screenshot(): String? {
        val saved = original ?: return null
        val now = runCatching { read() }.getOrNull() ?: return null
        // Never send a different screen in a follow-up or capture EQO's own sheet.
        if (!matches(saved, now)) return null
        val image = capture()
        val after = runCatching { read() }.getOrNull() ?: return null
        return image?.takeIf {
            matches(saved, after) && it.length <= MAX_IMAGE_CHARS
        }
    }

    private fun matches(
        saved: ExplainScreen,
        current: ExplainScreen,
    ): Boolean = saved.app == current.app && saved.text == current.text && imageSafe(current)

    private fun imageSafe(screen: ExplainScreen): Boolean = !screen.hasPassword && screen.completeTree

    private fun label(
        node: AccessibilityNodeInfo,
        foreign: Boolean,
    ): String? =
        if (node.isPassword || !node.isVisibleToUser || foreign) {
            null
        } else {
            node.text?.toString()?.takeIf { it.isNotBlank() } ?: node.contentDescription?.toString()
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
        val foreign = node.packageName?.toString()?.let { it != app } == true
        collector.add(
            node.packageName?.toString() ?: app,
            label(node, foreign),
            node.isPassword,
            node.isVisibleToUser,
        )
        // Password subtrees and foreign-package subtrees are excluded entirely.
        if (foreign) collector.markIncomplete()
        if (node.isPassword || foreign) return
        for (index in 0 until node.childCount.coerceAtMost(ExplainSession.MAX_NODES)) {
            if (!collector.hasCapacity()) break
            node.getChild(index)?.let { child ->
                try {
                    collect(child, app, collector, depth + 1)
                } finally {
                    child.recycle()
                }
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
    private val closed = AtomicBoolean(false)
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
            .eventListener(
                object : EventListener() {
                    override fun callStart(call: Call) {
                        // A queued call must not begin sending after the sheet has closed.
                        if (closed.get()) call.cancel()
                    }
                },
            ).connectTimeout(CONNECT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_SECONDS, TimeUnit.SECONDS)
            .build()
    private val provider = OpenRouterProvider(client, SettingsRepository(context.applicationContext, store))

    override fun close() {
        closed.set(true)
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

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
                            " Explain the Android screen in plain language in " +
                            "${Locale.getDefault().toLanguageTag()}. " +
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
        private const val CONNECT_SECONDS = 20L
        private const val READ_SECONDS = 60L
        private const val CALL_SECONDS = 90L

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
