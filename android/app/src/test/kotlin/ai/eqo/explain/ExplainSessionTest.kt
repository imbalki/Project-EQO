package ai.eqo.explain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplainSessionTest {
    private class Source(
        var screen: ExplainScreen = ExplainScreen("test.app", "Screen text", 6),
    ) : ExplainSource {
        var reads = 0
        var captures = 0
        var capture: String? = "synthetic-image"

        override fun read(): ExplainScreen = screen.also { reads++ }

        override suspend fun screenshot(): String? = capture.also { captures++ }
    }

    private class Model(
        var vision: Boolean = true,
    ) : ExplainModel {
        val screens = mutableListOf<ExplainScreen>()
        val images = mutableListOf<String?>()
        var calls = 0

        override suspend fun supportsImages(): Boolean = vision

        override suspend fun answer(
            screen: ExplainScreen,
            image: String?,
            question: String,
        ): String {
            calls++
            screens += screen
            images += image
            return "Synthetic explanation"
        }
    }

    @Test fun `labelled text does not capture`() =
        runTest {
            val source = Source()
            val model = Model()
            ExplainSession(source, model, { true }).ask("Explain screen")
            assertEquals(0, source.captures)
            assertEquals(listOf<String?>(null), model.images)
        }

    @Test fun `sparse text uses screenshot`() =
        runTest {
            val source = Source(ExplainScreen("test.app", "one", 1))
            val model = Model()
            ExplainSession(source, model, { true }).ask("Explain screen")
            assertEquals(1, source.captures)
            assertEquals(listOf("synthetic-image"), model.images)
        }

    @Test fun `formula or diagram asks for image even with labelled text`() =
        runTest {
            for (question in listOf("Explain this formula", "Explain the diagram", "What is in the image?")) {
                val source = Source()
                ExplainSession(source, Model(), { true }).ask(question)
                assertEquals(1, source.captures)
            }
        }

    @Test fun `capture refusal falls back to original text`() =
        runTest {
            val source = Source(ExplainScreen("test.app", "few", 1)).apply { capture = null }
            val model = Model()
            val session = ExplainSession(source, model, { true })
            assertEquals("Synthetic explanation", session.ask("Explain"))
            assertEquals("capture", session.textOnlyReason)
            assertEquals("few", model.screens.single().text)
            assertEquals(listOf<String?>(null), model.images)
        }

    @Test fun `nonvision model never captures and reports text only`() =
        runTest {
            val source = Source()
            val model = Model(false)
            val session = ExplainSession(source, model, { true })
            session.ask("Explain image")
            assertEquals(0, source.captures)
            assertEquals("vision", session.textOnlyReason)
            assertEquals("text", session.sourceType)
        }

    @Test fun `consent off prevents reads captures and uploads`() =
        runTest {
            val source = Source()
            val model = Model()
            val session = ExplainSession(source, model, { false })
            assertTrue(runCatching { session.ask("Explain image") }.isFailure)
            assertEquals(0, source.reads)
            assertEquals(0, source.captures)
            assertEquals(0, model.calls)
        }

    @Test fun `password means no image upload`() =
        runTest {
            val source = Source(ExplainScreen("test.app", "public label", 1, true))
            val model = Model()
            val session = ExplainSession(source, model, { true })
            session.ask("Explain image")
            assertEquals(0, source.captures)
            assertEquals("password", session.textOnlyReason)
        }

    @Test fun `own window password and hidden labels excluded`() {
        val collector = ExplainTextCollector("ai.eqo.app")
        collector.add("ai.eqo.app", "own private window", false)
        collector.add("test.app", "synthetic password", true)
        collector.add("test.app", "hidden", false, false)
        collector.add("test.app", "visible", false)
        val result = collector.result("test.app")
        assertEquals("visible\n", result.text)
        assertEquals(1, result.labels)
        assertTrue(result.hasPassword)
    }

    @Test fun `text and node counts are bounded including blank nodes`() {
        val collector = ExplainTextCollector("ai.eqo.app")
        collector.add("test.app", "x".repeat(ExplainSession.MAX_TEXT + 100), false)
        assertEquals(ExplainSession.MAX_TEXT, collector.result("test.app").text.length)
        assertFalse(collector.hasCapacity())
        val blanks = ExplainTextCollector("ai.eqo.app")
        repeat(ExplainSession.MAX_NODES) { blanks.add("test.app", null, false) }
        blanks.add("test.app", "must not be read", false)
        assertFalse(blanks.hasCapacity())
        assertEquals("", blanks.result("test.app").text)
    }

    @Test fun `followup keeps snapshot and close drops context`() =
        runTest {
            val source = Source(ExplainScreen("test.app", "original", 1))
            val model = Model()
            val session = ExplainSession(source, model, { true })
            session.ask("Explain")
            source.screen = ExplainScreen("different.app", "different", 6)
            session.ask("Tell me more")
            assertEquals(1, source.reads)
            assertEquals(1, source.captures)
            assertTrue(model.screens.all { it.text == "original" })
            assertEquals(listOf("synthetic-image", "synthetic-image"), model.images)
            session.close()
            assertEquals("text", session.sourceType)
            assertEquals(null, session.textOnlyReason)
            assertTrue(runCatching { session.ask("Again") }.isFailure)
            assertEquals(2, model.calls)
        }

    @Test fun `close during pending answer rejects late result`() =
        runTest {
            val waiting = CompletableDeferred<Unit>()
            val started = CompletableDeferred<Unit>()
            val model =
                object : ExplainModel {
                    override suspend fun supportsImages() = false

                    override suspend fun answer(
                        screen: ExplainScreen,
                        image: String?,
                        question: String,
                    ): String {
                        started.complete(Unit)
                        waiting.await()
                        return "late result"
                    }
                }
            val session = ExplainSession(Source(), model, { true })
            val work = async { runCatching { session.ask("Explain") } }
            started.await()
            session.close()
            waiting.complete(Unit)
            assertTrue(work.await().isFailure)
        }

    @Test fun `typed request routes to shortcut guidance`() {
        assertTrue(ExplainSession.isScreenRequest("What is on my screen?"))
        assertTrue(ExplainSession.isScreenRequest("Explain this screen"))
        assertFalse(ExplainSession.isScreenRequest("Open calculator"))
    }

    @Test fun `incomplete privacy traversal cannot send an image`() =
        runTest {
            val source = Source(ExplainScreen("test.app", "bounded", 1, completeTree = false))
            val session = ExplainSession(source, Model(), { true })
            session.ask("Explain image")
            assertEquals(0, source.captures)
            assertEquals("password", session.textOnlyReason)
        }

    @Test fun `close during capture discards late image without calling model`() =
        runTest {
            val waiting = CompletableDeferred<Unit>()
            val started = CompletableDeferred<Unit>()
            val source =
                object : ExplainSource {
                    override fun read() = ExplainScreen("test.app", "few", 1)

                    override suspend fun screenshot(): String {
                        started.complete(Unit)
                        waiting.await()
                        return "late-image"
                    }
                }
            val model = Model()
            val session = ExplainSession(source, model, { true })
            val work = async { runCatching { session.ask("Explain image") } }
            started.await()
            session.close()
            waiting.complete(Unit)
            assertTrue(work.await().isFailure)
            assertEquals("text", session.sourceType)
            assertEquals(0, model.calls)
        }

    @Test fun `close releases adapters and prevents upload from ready callback`() =
        runTest {
            val baseModel = Model()
            var sourceClosed = false
            var modelClosed = false
            val source =
                object : ExplainSource by Source() {
                    override fun close() {
                        sourceClosed = true
                    }
                }
            val model =
                object : ExplainModel by baseModel {
                    override fun close() {
                        modelClosed = true
                    }
                }
            lateinit var session: ExplainSession
            session = ExplainSession(source, model, { true }, ready = { session.close() })
            assertTrue(runCatching { session.ask("Explain") }.isFailure)
            assertTrue(sourceClosed)
            assertTrue(modelClosed)
            assertEquals(0, baseModel.calls)
        }
}
