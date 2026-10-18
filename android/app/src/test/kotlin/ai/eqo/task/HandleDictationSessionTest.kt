// Origin: EQO-authored fake recorder/provider/inserter tests; no microphone or network.
package ai.eqo.task

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HandleDictationSessionTest {
    private val ready = DictationSetup(key = true, model = true, consent = true, permission = true)

    @Test
    fun `idle recording transcribing inserted uses captured inserter and deletes audio`() =
        runTest {
            val recording = FakeRecording()
            val events = mutableListOf<DictationState>()
            val inserted = mutableListOf<String>()
            val session =
                HandleDictationSession(this, { recording }, { "synthetic words" }) { state, _ -> events += state }
            assertEquals(DictationState.IDLE, session.state)
            session.start(ready) {
                inserted += it
                true
            }
            assertEquals(DictationState.RECORDING, session.state)
            session.stop()
            assertEquals(DictationState.TRANSCRIBING, session.state)
            advanceUntilIdle()
            assertEquals(listOf("synthetic words"), inserted)
            assertEquals(
                listOf(
                    DictationState.RECORDING,
                    DictationState.TRANSCRIBING,
                    DictationState.INSERTING,
                    DictationState.INSERTED,
                ),
                events,
            )
            assertFalse(recording.file.exists())
        }

    @Test
    fun `failed verification reports copied and removes audio`() =
        runTest {
            val recording = FakeRecording()
            val failures = mutableListOf<DictationFailure?>()
            val session =
                HandleDictationSession(this, { recording }, { "synthetic words" }) { _, error -> failures += error }
            session.start(ready) { false }
            session.stop()
            advanceUntilIdle()
            assertEquals(DictationState.FAILED, session.state)
            assertEquals(DictationFailure.COPIED, failures.last())
            assertEquals("synthetic words", session.transcript)
            assertFalse(recording.file.exists())
        }

    @Test
    fun `missing key model consent or permission never records`() =
        runTest {
            val cases =
                listOf(
                    ready.copy(key = false) to DictationFailure.KEY,
                    ready.copy(model = false) to DictationFailure.MODEL,
                    ready.copy(consent = false) to DictationFailure.CONSENT,
                    ready.copy(permission = false) to DictationFailure.PERMISSION,
                )
            cases.forEach { (setup, expected) ->
                var failure: DictationFailure? = null
                val session =
                    HandleDictationSession(this, { error("must not record") }, { error("must not send") }) { _, why ->
                        failure = why
                    }
                session.start(setup) { error("must not insert") }
                assertEquals(DictationState.FAILED, session.state)
                assertEquals(expected, failure)
            }
        }

    @Test
    fun `invalid target never records`() =
        runTest {
            var failure: DictationFailure? = null
            val session =
                HandleDictationSession(this, { error("must not record") }, { error("must not send") }) { _, why ->
                    failure = why
                }
            session.start(ready, null)
            assertEquals(DictationFailure.TARGET, failure)
        }

    @Test
    fun `cancel and ninety second cap never transcribe or insert`() =
        runTest {
            val recording = FakeRecording()
            val session = HandleDictationSession(this, { recording }, { error("must not send") }, { _, _ -> })
            session.start(ready) { error("must not insert") }
            advanceTimeBy(VoiceAudioRecorder.MAX_DURATION_MILLIS)
            runCurrent()
            assertEquals(DictationState.CANCELLED, session.state)
            assertFalse(recording.file.exists())
            val second = FakeRecording()
            val cancelled = HandleDictationSession(this, { second }, { error("must not send") }, { _, _ -> })
            cancelled.start(ready) { error("must not insert") }
            cancelled.cancel()
            assertEquals(DictationState.CANCELLED, cancelled.state)
            assertFalse(second.file.exists())
        }

    @Test
    fun `cancel while transcribing rejects late completion and deletes audio`() =
        runTest {
            val recording = FakeRecording()
            val answer = CompletableDeferred<String>()
            var inserted = false
            val session = HandleDictationSession(this, { recording }, { answer.await() }, { _, _ -> })
            session.start(ready) {
                inserted = true
                true
            }
            session.stop()
            runCurrent()
            session.cancel()
            answer.complete("late synthetic words")
            advanceUntilIdle()
            assertEquals(DictationState.CANCELLED, session.state)
            assertFalse(inserted)
            assertFalse(recording.file.exists())
        }

    @Test
    fun `provider and blank responses fail without exposing payload or leaving audio`() =
        runTest {
            listOf<suspend (File) -> String>({ error("synthetic private error") }, { "  " }).forEach { provider ->
                val recording = FakeRecording()
                var failure: DictationFailure? = null
                val session = HandleDictationSession(this, { recording }, provider) { _, why -> failure = why }
                session.start(ready) { error("must not insert") }
                session.stop()
                advanceUntilIdle()
                assertEquals(DictationFailure.PROVIDER, failure)
                assertFalse(recording.file.exists())
            }
        }

    @Test
    fun `recorder start failure is cleaned up`() =
        runTest {
            val recording = FakeRecording(fail = true)
            var failure: DictationFailure? = null
            val session =
                HandleDictationSession(this, { recording }, { error("must not send") }) { _, why -> failure = why }
            session.start(ready) { true }
            assertEquals(DictationFailure.RECORDING, failure)
            assertTrue(recording.cancelled)
            assertFalse(recording.file.exists())
        }

    @Test
    fun `cancel is no longer offered once irreversible insertion has started`() =
        runTest {
            val recording = FakeRecording()
            val committed = CompletableDeferred<Boolean>()
            val session = HandleDictationSession(this, { recording }, { "synthetic words" }, { _, _ -> })
            session.start(ready) { committed.await() }
            session.stop()
            runCurrent()
            assertEquals(DictationState.INSERTING, session.state)
            session.cancel()
            assertEquals(DictationState.INSERTING, session.state)
            committed.complete(true)
            advanceUntilIdle()
            assertEquals(DictationState.INSERTED, session.state)
            assertFalse(recording.file.exists())
        }

    private class FakeRecording(
        private val fail: Boolean = false,
    ) : VoiceRecording {
        override val file: File = File.createTempFile("eqo-dictation-test-", ".wav")
        var cancelled = false

        override fun start(onLimit: () -> Unit) {
            if (fail) error("synthetic capture failure")
        }

        override fun stopCapture() = Unit

        override suspend fun finish(): File = file

        override fun cancel() {
            cancelled = true
            file.delete()
        }
    }
}
