package ai.eqo.core.llm.providers

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OpenRouterModelCatalogTest {
    private val fixture = requireNotNull(javaClass.getResource("/openrouter-models.json")).readText()

    @Test
    fun `real endpoint shape parses unknown fields and free pricing`() {
        val models = OpenRouterModelCatalog.parse(fixture)
        assertEquals(2, models.size)
        assertTrue(models.all { it.contextLength != null && it.provider.isNotBlank() })
        assertTrue(models.any { OpenRouterModelCatalog.pricePerMillion(it.inputPrice) == "free" })
    }

    @Test
    fun `missing malformed and duplicate rows are handled defensively`() {
        val models =
            OpenRouterModelCatalog.parse(
                """{"data":[null,8,{}, {"id":"bad"}, {"id":"a/b","pricing":[]},
            {"id":"a/b"},{"id":"c/d","context_length":{},"pricing":{"prompt":null,"completion":{}}}]}""",
            )
        assertEquals(listOf("a/b", "c/d"), models.map { it.id })
        assertNull(models.last().contextLength)
        assertEquals("n/a", OpenRouterModelCatalog.pricePerMillion(models.last().inputPrice))
        assertTrue(runCatching { OpenRouterModelCatalog.parse("oops") }.isFailure)
        assertTrue(runCatching { OpenRouterModelCatalog.parse("{}") }.isFailure)
    }

    @Test
    fun `huge lists and bodies are bounded`() {
        val json = "{\"data\":[" + (1..6000).joinToString { "{\"id\":\"p/$it\"}" } + "]}"
        assertEquals(5000, OpenRouterModelCatalog.parse(json).size)
        val oversized = " ".repeat(OpenRouterModelCatalog.MAX_JSON_CHARS + 1)
        assertTrue(runCatching { OpenRouterModelCatalog.parse(oversized) }.isFailure)
    }

    @Test
    fun `prices use decimal per million math and half up rounding`() {
        val cases =
            mapOf(
                null to "n/a",
                "" to "n/a",
                "junk" to "n/a",
                "-1" to "n/a",
                "1e9999" to "n/a",
                "0" to "free",
                "0.0000003" to "\$0.3",
                "0.0000015" to "\$1.5",
                "0.0000000000015" to "\$0.000002",
                "0.0000000000001" to "<\$0.000001",
            )
        cases.forEach { (raw, expected) -> assertEquals(expected, OpenRouterModelCatalog.pricePerMillion(raw)) }
    }

    @Test
    fun `last selection recommended and name order with case insensitive multi term filter`() {
        val models =
            listOf("z/last", "other/z", "google/gemini-2.5-flash", "openai/gpt-4.1-mini", "other/a")
                .map { OpenRouterModel(it, it, null, null, null) }
        assertEquals(
            listOf("z/last", "openai/gpt-4.1-mini", "google/gemini-2.5-flash", "other/a", "other/z"),
            OpenRouterModelCatalog.search(models, "z/last", "").map { it.id },
        )
        val filtered = OpenRouterModelCatalog.search(models, null, "GOOGLE flash")
        assertEquals(listOf("google/gemini-2.5-flash"), filtered.map { it.id })
        assertTrue(OpenRouterModelCatalog.search(models, null, "no match").isEmpty())
    }

    @Test
    fun `daily cache survives offline failures and explicit refresh retries`() {
        val cache = FakeCache()
        var calls = 0
        var time = 1000L
        var offline = false
        val repository =
            OpenRouterModelRepository(cache, {
                calls++
                if (offline) throw IOException("offline")
                fixture
            }, { time })
        assertFalse(repository.load().cached)
        assertTrue(repository.load().cached)
        assertEquals(1, calls)
        time += OpenRouterModelRepository.DAY_MILLIS
        offline = true
        val failed = repository.load()
        assertTrue(failed.offline)
        assertEquals(2, failed.models.size)
        assertEquals(1000L, failed.fetchedAt)
        repository.load()
        assertEquals(2, calls)
        repository.load(force = true)
        assertEquals(3, calls)
        offline = false
        assertFalse(repository.load(force = true).offline)
        assertEquals(time, cache.value?.fetchedAt)
    }

    @Test
    fun `corrupt cache and malformed HTTP preserve fallback without throwing`() {
        val cache = FakeCache().apply { value = CachedModelCatalog("bad", 1, 1) }
        val repository = OpenRouterModelRepository(cache, { "{}" }, { OpenRouterModelRepository.DAY_MILLIS + 2 })
        assertTrue(repository.load().offline)
        assertTrue(repository.load().models.isEmpty())
        val unavailable =
            object : ModelCatalogCache {
                override fun read(): CachedModelCatalog? = throw IOException()

                override fun write(value: CachedModelCatalog): Boolean = throw IOException()
            }
        assertEquals(2, OpenRouterModelRepository(unavailable, { fixture }).load().models.size)
    }

    @Test
    fun `fake HTTP uses only public OpenRouter GET with no credentials and refuses redirects`() {
        var calls = 0
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    calls++
                    val request = chain.request()
                    assertEquals(OpenRouterModelHttp.ENDPOINT, request.url.toString())
                    assertEquals("GET", request.method)
                    assertNull(request.header("Authorization"))
                    assertEquals(0, request.headers.size)
                    Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("redirect")
                        .header("Location", "https://example.org/models")
                        .body("{}".toResponseBody())
                        .build()
                }.build()
        assertTrue(runCatching { OpenRouterModelHttp(client).fetch() }.exceptionOrNull() is IOException)
        assertEquals(1, calls)
    }

    @Test
    fun `fake HTTP returns successful metadata and rejects oversized bodies`() {
        var body = fixture
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody())
                        .build()
                }.build()
        val transport = OpenRouterModelHttp(client)
        assertEquals(fixture, transport.fetch())
        body = " ".repeat(OpenRouterModelCatalog.MAX_JSON_CHARS + 1)
        assertTrue(runCatching { transport.fetch() }.exceptionOrNull() is IOException)
    }

    private class FakeCache : ModelCatalogCache {
        var value: CachedModelCatalog? = null

        override fun read(): CachedModelCatalog? = value

        override fun write(value: CachedModelCatalog): Boolean {
            this.value = value
            return true
        }
    }
}
