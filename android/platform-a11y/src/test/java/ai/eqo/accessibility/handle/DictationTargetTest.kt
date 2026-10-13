// Origin: EQO-authored fake field/clipboard policy tests; no app content or platform actions.
package ai.eqo.accessibility.handle

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictationTargetTest {
    @Test
    fun `password own window missing owner secure panel and non editable targets are refused`() {
        assertTrue(dictationAllowed(true, false, "synthetic.app", "eqo", false))
        assertFalse(dictationAllowed(true, true, "synthetic.app", "eqo", false))
        assertFalse(dictationAllowed(true, false, "eqo", "eqo", false))
        assertFalse(dictationAllowed(true, false, null, "eqo", false))
        assertFalse(dictationAllowed(true, false, "synthetic.app", "eqo", true))
        assertFalse(dictationAllowed(false, false, "synthetic.app", "eqo", false))
    }

    @Test
    fun `paste is verified and transcript stays on clipboard after success`() =
        runTest {
            val calls = mutableListOf<String>()
            val field = FakeField(calls)
            var clipboard = "old synthetic clip"
            assertTrue(
                insertDictation(field, "synthetic words") {
                    clipboard = it
                    calls += "copy"
                },
            )
            assertEquals("synthetic words", clipboard)
            assertEquals(listOf("copy", "safe", "paste", "safe", "verify"), calls)
        }

    @Test
    fun `accepted paste without matching text retains transcript for manual paste`() =
        runTest {
            val calls = mutableListOf<String>()
            val field = FakeField(calls, matches = false)
            var clipboard = "old synthetic clip"
            val accepted = insertDictation(field, "synthetic words") { clipboard = it }
            assertFalse(accepted)
            assertEquals("synthetic words", clipboard)
            assertEquals(listOf("safe", "paste", "safe", "verify"), calls)
        }

    @Test
    fun `refused paste and stale unsafe field copy but never use set text`() =
        runTest {
            val fields =
                listOf(FakeField(mutableListOf(), accepts = false), FakeField(mutableListOf(), allowed = false))
            fields.forEach { field ->
                var copied = false
                val accepted =
                    insertDictation(field, "synthetic words") { copied = true }
                assertFalse(accepted)
                assertTrue(copied)
            }
        }

    @Test
    fun `cancelling verification retains transcript without reading or restoring clipboard`() =
        runTest {
            var clipboard = "old synthetic clip"
            val work =
                launch {
                    insertDictation(
                        FakeField(mutableListOf()),
                        "synthetic words",
                        { clipboard = it },
                    )
                }
            runCurrent()
            work.cancel()
            advanceUntilIdle()
            assertEquals("synthetic words", clipboard)
        }

    @Test
    fun `stale native node exception still copies transcript for manual paste`() =
        runTest {
            val field =
                object : DictationField {
                    override fun safe(): Boolean = error("synthetic stale node")

                    override fun paste(text: String): Boolean = error("must not paste")

                    override fun verified(text: String): Boolean = error("must not read")
                }
            var copied = ""
            assertFalse(insertDictation(field, "synthetic words") { copied = it })
            assertEquals("synthetic words", copied)
        }

    @Test
    fun `clipboard write failure never reports a manual paste fallback or touches field`() =
        runTest {
            val calls = mutableListOf<String>()
            var failed = false
            try {
                insertDictation(FakeField(calls), "synthetic words") { error("synthetic write failure") }
            } catch (_: IllegalStateException) {
                failed = true
            }
            assertTrue(failed)
            assertTrue(calls.isEmpty())
        }

    private class FakeField(
        private val calls: MutableList<String>,
        private val matches: Boolean = true,
        private val accepts: Boolean = true,
        private val allowed: Boolean = true,
    ) : DictationField {
        override fun safe(): Boolean {
            calls += "safe"
            return allowed
        }

        override fun paste(text: String): Boolean {
            calls += "paste"
            return accepts
        }

        override fun verified(text: String): Boolean {
            calls += "verify"
            return matches
        }
    }
}
