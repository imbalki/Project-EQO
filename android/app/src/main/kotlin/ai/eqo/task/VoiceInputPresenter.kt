// Origin: EQO-authored voice input; draft text only, no planner or run capability.
package ai.eqo.task

internal enum class VoiceInputState {
    READY,
    PERMISSION_NEEDED,
    LISTENING,
    REVIEW,
    NOT_CAUGHT,
    NOT_ALLOWED,
    UNAVAILABLE,
    ERROR,
    ;

    companion object {
        val BLOCKS_TAP = setOf(UNAVAILABLE, LISTENING, PERMISSION_NEEDED)
    }
}

/** The only result side effect is filling the editable draft. No submit callback exists. */
internal class VoiceInputPresenter(
    private val render: (VoiceInputState) -> Unit,
    private val fillDraft: (String) -> Unit,
    private val requestPermission: () -> Unit,
    private val startListening: () -> Unit,
) {
    private var state = VoiceInputState.READY

    fun availability(available: Boolean) = update(if (available) VoiceInputState.READY else VoiceInputState.UNAVAILABLE)

    fun tap(permissionGranted: Boolean) {
        if (state in VoiceInputState.BLOCKS_TAP) return
        if (permissionGranted) {
            listen()
        } else {
            update(VoiceInputState.PERMISSION_NEEDED)
            requestPermission()
        }
    }

    fun permissionResult(granted: Boolean) {
        if (state != VoiceInputState.PERMISSION_NEEDED) return
        if (granted) listen() else update(VoiceInputState.NOT_ALLOWED)
    }

    fun result(text: String?) {
        if (state != VoiceInputState.LISTENING) return
        if (text.isNullOrBlank()) {
            update(VoiceInputState.NOT_CAUGHT)
        } else {
            fillDraft(text)
            update(VoiceInputState.REVIEW)
        }
    }

    fun error(failure: VoiceInputState) {
        if (state == VoiceInputState.LISTENING) update(failure)
    }

    fun cancel() {
        if (state == VoiceInputState.LISTENING) update(VoiceInputState.READY)
    }

    private fun listen() {
        update(VoiceInputState.LISTENING)
        startListening()
    }

    private fun update(next: VoiceInputState) {
        state = next
        render(next)
    }
}
