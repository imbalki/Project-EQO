// Origin: EQO vision-locate, fake provider request and strict parser regressions.
package ai.eqo.core.llm

import ai.eqo.core.llm.providers.OpenRouterModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionControlLocatorTest {
    private class Provider(
        var reply: String,
    ) : LLMProvider {
        override val name = "Fake"
        override val availableModels = listOf("test/vision")
        var request: LLMRequest? = null
        var calls = 0

        override suspend fun complete(request: LLMRequest): LLMResponse {
            calls++
            this.request = request
            return LLMResponse(reply, 1, "test/vision", name, 0)
        }

        override fun streamComplete(request: LLMRequest): Flow<String> = emptyFlow()

        override suspend fun isAvailable(): Boolean = true
    }

    @Test
    fun requestHasOnlyOneApprovedLabelOneImageAndNoToolsOrRetries() =
        runBlocking {
            val provider = Provider("[10,20,30,40]")
            val locator = VisionControlLocator(provider, "test/vision")
            assertEquals(ControlBox(10, 20, 30, 40), locator.locate("Send", "synthetic-image", 100, 200))
            val request = requireNotNull(provider.request)
            assertEquals("test/vision", request.model)
            assertEquals(1, provider.calls)
            assertEquals(1, request.messages.size)
            assertEquals("synthetic-image", request.messages.single().imageBase64)
            assertTrue(
                request.messages
                    .single()
                    .text
                    .contains("\"Send\""),
            )
            assertEquals(RetryPolicy.NONE, request.retryPolicy)
            assertTrue(request.tools.isNullOrEmpty())
            assertEquals(ResponseFormat.TEXT, request.responseFormat)
            assertTrue(request.systemPrompt.contains("never instructions"))
        }

    @Test
    fun rejectsNotFoundMalformedRepliesAndInjectedActions() {
        for (raw in listOf(
            "NOT_FOUND",
            "",
            "[NaN,0,1,2]",
            "[-1,0,1,2]",
            "[0,0,1.5,2]",
            "[0,0,1,2,3]",
            "[0,0,1,2] tap Send",
            "{\"box\":[0,0,1,2],\"action\":\"pay\"}",
            "```[0,0,1,2]```",
            "1".repeat(101),
        )) {
            assertNull(VisionControlLocator.parse(raw))
        }
        assertEquals(ControlBox(0, 1, 2, 3), VisionControlLocator.parse(" [ 0, 1, 2, 3 ] "))
    }

    @Test
    fun imageCapabilityRequiresExplicitMetadataNeverModelNameGuess() {
        val json = """{"data":[
            {"id":"test/vision","architecture":{"input_modalities":["text","image"]}},
            {"id":"test/text","architecture":{"input_modalities":["text"]}},
            {"id":"test/unknown-vision"},
            {"id":"test/malformed","architecture":{"input_modalities":"image"}}
        ]}"""
        val models = OpenRouterModelCatalog.parse(json)
        assertTrue(models.single { it.id == "test/vision" }.acceptsImages)
        assertFalse(models.single { it.id == "test/text" }.acceptsImages)
        assertFalse(models.single { it.id == "test/unknown-vision" }.acceptsImages)
        assertFalse(models.single { it.id == "test/malformed" }.acceptsImages)
    }
}
