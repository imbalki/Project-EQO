package ai.eqo.core.llm.providers

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

/** Public metadata only. Neither this transport nor the cache accepts credentials. */
class OpenRouterModelHttp internal constructor(
    httpClient: OkHttpClient,
) {
    constructor() : this(OkHttpClient())

    private val client =
        httpClient
            .newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    fun fetch(): String =
        client
            .newCall(
                Request
                    .Builder()
                    .url(ENDPOINT)
                    .get()
                    .build(),
            ).execute()
            .use { response ->
                if (!response.isSuccessful) throw IOException("Model list unavailable")
                val body = response.body
                if (body.contentLength() > OpenRouterModelCatalog.MAX_JSON_CHARS) {
                    throw IOException("Model list too large")
                }
                val source = body.source()
                source.request(OpenRouterModelCatalog.MAX_JSON_CHARS.toLong() + 1)
                if (source.buffer.size > OpenRouterModelCatalog.MAX_JSON_CHARS) {
                    throw IOException("Model list too large")
                }
                source.readUtf8()
            }

    companion object {
        const val ENDPOINT = "https://openrouter.ai/api/v1/models"
        private const val CALL_TIMEOUT_SECONDS = 20L
    }
}

data class OpenRouterModel(
    val id: String,
    val name: String,
    val contextLength: Long?,
    val inputPrice: String?,
    val outputPrice: String?,
) {
    val provider: String get() = id.substringBefore('/')
}

object OpenRouterModelCatalog {
    const val MAX_JSON_CHARS = 4_000_000
    private const val MAX_MODELS = 5_000
    private const val PRICE_SCALE = 6
    private const val MAX_LABEL_LENGTH = 256
    private const val MAX_PRICE_LENGTH = 64
    private const val MIN_PRICE_SCALE = -20
    private const val MAX_PRICE_SCALE = 100
    private val recommended = listOf("openai/gpt-4.1-mini", "google/gemini-2.5-flash", "anthropic/claude-sonnet-4")

    fun parse(json: String): List<OpenRouterModel> {
        require(json.length <= MAX_JSON_CHARS)
        val root = JsonParser.parseString(json)
        require(root.isJsonObject)
        val data = root.asJsonObject.get("data")
        require(data != null && data.isJsonArray)
        return data.asJsonArray
            .asSequence()
            .take(MAX_MODELS)
            .mapNotNull { element ->
                if (!element.isJsonObject) return@mapNotNull null
                val obj = element.asJsonObject
                val id =
                    text(obj, "id")?.takeIf { it.isNotBlank() && it.length <= MAX_LABEL_LENGTH && '/' in it }
                        ?: return@mapNotNull null
                val pricing = obj.get("pricing")?.takeIf { it.isJsonObject }?.asJsonObject
                OpenRouterModel(
                    id = id,
                    name = text(obj, "name")?.take(MAX_LABEL_LENGTH)?.takeIf(String::isNotBlank) ?: id,
                    contextLength = text(obj, "context_length")?.toLongOrNull()?.takeIf { it > 0 },
                    inputPrice = pricing?.let { text(it, "prompt") },
                    outputPrice = pricing?.let { text(it, "completion") },
                )
            }.distinctBy { it.id }
            .toList()
    }

    fun pricePerMillion(raw: String?): String {
        val amount = raw?.takeIf { it.length <= MAX_PRICE_LENGTH }?.toBigDecimalOrNull()
        return when {
            amount == null -> "n/a"
            amount.signum() < 0 || amount.scale() !in MIN_PRICE_SCALE..MAX_PRICE_SCALE -> "n/a"
            amount.signum() == 0 -> "free"
            else -> formattedPrice(amount)
        }
    }

    private fun formattedPrice(amount: BigDecimal): String {
        val million = amount.multiply(BigDecimal("1000000"))
        val rounded = million.setScale(PRICE_SCALE, RoundingMode.HALF_UP)
        return if (rounded.signum() == 0) "<\$0.000001" else "\$${rounded.stripTrailingZeros().toPlainString()}"
    }

    fun search(
        models: List<OpenRouterModel>,
        lastChoice: String?,
        query: String,
    ): List<OpenRouterModel> {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        return models
            .filter { model ->
                terms.all { term -> "${model.id} ${model.name} ${model.provider}".contains(term, ignoreCase = true) }
            }.sortedWith(
                compareBy<OpenRouterModel> {
                    when (it.id) {
                        lastChoice -> -1
                        in recommended -> recommended.indexOf(it.id)
                        else -> recommended.size
                    }
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id },
            )
    }

    private fun text(
        obj: JsonObject,
        key: String,
    ): String? = obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString
}

/** App-private persistence is supplied by Android; fake storage/HTTP keep JVM tests offline. */
interface ModelCatalogCache {
    fun read(): CachedModelCatalog?

    fun write(value: CachedModelCatalog): Boolean
}

data class CachedModelCatalog(
    val json: String,
    val fetchedAt: Long,
    val attemptedAt: Long,
)

data class ModelCatalogResult(
    val models: List<OpenRouterModel>,
    val fetchedAt: Long?,
    val offline: Boolean,
    val cached: Boolean,
)

class OpenRouterModelRepository(
    private val cache: ModelCatalogCache,
    private val fetch: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Synchronized
    fun load(force: Boolean = false): ModelCatalogResult {
        val timestamp = now()
        val stored = runCatching { cache.read() }.getOrNull()
        val cachedModels = stored?.let { runCatching { OpenRouterModelCatalog.parse(it.json) }.getOrNull() }.orEmpty()
        if (!force && stored != null && isRecent(stored, timestamp)) {
            return ModelCatalogResult(cachedModels, stored.fetchedAt.takeIf { cachedModels.isNotEmpty() }, false, true)
        }
        return refresh(stored, cachedModels, timestamp)
    }

    private fun isRecent(
        stored: CachedModelCatalog,
        timestamp: Long,
    ): Boolean = timestamp - stored.attemptedAt in 0 until DAY_MILLIS

    private fun refresh(
        stored: CachedModelCatalog?,
        cachedModels: List<OpenRouterModel>,
        timestamp: Long,
    ): ModelCatalogResult {
        // Throttle failed attempts too, without replacing a usable last-known list.
        val attempted = CachedModelCatalog(stored?.json ?: "{\"data\":[]}", stored?.fetchedAt ?: 0, timestamp)
        runCatching { cache.write(attempted) }
        val json = runCatching { fetch() }.getOrNull()
        val models = json?.let { runCatching { OpenRouterModelCatalog.parse(it) }.getOrNull() }.orEmpty()
        if (models.isEmpty()) {
            return ModelCatalogResult(cachedModels, stored?.fetchedAt?.takeIf { cachedModels.isNotEmpty() }, true, true)
        }
        runCatching { cache.write(CachedModelCatalog(requireNotNull(json), timestamp, timestamp)) }
        return ModelCatalogResult(models, timestamp, false, false)
    }

    companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
