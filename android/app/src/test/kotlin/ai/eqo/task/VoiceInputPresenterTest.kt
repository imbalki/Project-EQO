// Origin: EQO-authored draft-only voice presenter regressions.
package ai.eqo.task

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInputPresenterTest {
    private val states = mutableListOf<VoiceInputState>()
    private val drafts = mutableListOf<String>()
    private var permissions = 0
    private var starts = 0
    private val presenter = VoiceInputPresenter(states::add, drafts::add, { permissions++ }, { starts++ })

    @Test
    fun deniedPermissionLeavesTypingDraftAlone() {
        presenter.tap(false)
        assertEquals(1, permissions)
        assertEquals(0, starts)
        presenter.permissionResult(false)
        assertEquals(VoiceInputState.NOT_ALLOWED, states.last())
        assertEquals(emptyList<String>(), drafts)
    }

    @Test
    fun noRecognizerDoesNotRequestPermissionOrStart() {
        presenter.availability(false)
        presenter.tap(false)
        assertEquals(VoiceInputState.UNAVAILABLE, states.last())
        assertEquals(0, permissions)
        assertEquals(0, starts)
    }

    @Test
    fun cancelledPermissionIntentDoesNotStartOnLateGrant() {
        presenter.tap(false)
        presenter.cancel()
        presenter.permissionResult(true)
        assertEquals(0, starts)
        assertEquals(VoiceInputState.READY, states.last())
    }

    @Test
    fun permissionDialogPausePreservesIntentUntilGrant() {
        presenter.tap(false)
        presenter.cancel(preservePermission = true)
        presenter.availability(true)
        presenter.permissionResult(true)
        assertEquals(1, starts)
    }

    @Test
    fun permissionGrantStartsOnlyOnceAndResultOnlyFillsDraft() {
        presenter.tap(false)
        presenter.tap(false)
        presenter.permissionResult(true)
        presenter.permissionResult(true)
        assertEquals(1, starts)
        assertEquals(1, permissions)
        assertEquals(VoiceInputState.LISTENING, states.last())
        presenter.result("Open notes")
        assertEquals(listOf("Open notes"), drafts)
        assertEquals(VoiceInputState.REVIEW, states.last())
        presenter.result("late result")
        assertEquals(listOf("Open notes"), drafts)
    }

    @Test
    fun errorsKeepDraftUntouchedAndAllowRetry() {
        listOf(VoiceInputState.NOT_CAUGHT, VoiceInputState.NOT_ALLOWED, VoiceInputState.ERROR).forEach {
            presenter.tap(true)
            presenter.error(it)
            assertEquals(it, states.last())
        }
        assertEquals(3, starts)
        assertEquals(emptyList<String>(), drafts)
    }

    @Test
    fun emptyResultsAskForRetry() {
        listOf(null, "", "  ").forEach {
            presenter.tap(true)
            presenter.result(it)
            assertEquals(VoiceInputState.NOT_CAUGHT, states.last())
        }
        assertEquals(emptyList<String>(), drafts)
    }

    @Test
    fun leavingScreenIgnoresLateResultsAndErrors() {
        presenter.tap(true)
        presenter.cancel()
        presenter.result("late text")
        presenter.error(VoiceInputState.ERROR)
        assertEquals(VoiceInputState.READY, states.last())
        assertEquals(emptyList<String>(), drafts)
    }

    @Test
    fun partialsReplaceCurrentSegmentAndEarlyEndAppendsOnNextTap() {
        var draft = "Typed prefix"
        var stops = 0
        val voice = VoiceInputPresenter({}, { draft = it }, {}, {}, { draft }, { stops++ })
        voice.tap(true)
        voice.partial("long")
        assertEquals("Typed prefix long", draft)
        voice.partial("long sentence")
        assertEquals("Typed prefix long sentence", draft)
        voice.error(VoiceInputState.NOT_CAUGHT)
        voice.tap(true)
        voice.partial("with pauses")
        voice.tap(true)
        assertEquals(1, stops)
        voice.result(null)
        assertEquals("Typed prefix long sentence with pauses", draft)
        voice.tap(true)
        voice.result("and more")
        assertEquals("Typed prefix long sentence with pauses and more", draft)
    }

    @Test
    fun processingCanBeCancelledAndLateTranscriptIsIgnored() {
        var cancelled = false
        val voice = VoiceInputPresenter(states::add, drafts::add, {}, {}, stopListening = { cancelled = true })
        voice.tap(true)
        voice.processing()
        voice.tap(true)
        voice.result("late transcript")
        assertEquals(true, cancelled)
        assertEquals(VoiceInputState.READY, states.last())
        assertEquals(emptyList<String>(), drafts)
    }
}
