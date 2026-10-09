// Origin: EQO-authored voice input; draft text only, no planner or run capability.
package ai.eqo.task

internal enum class VoiceInputState {
    READY,
    PERMISSION_NEEDED,
    LISTENING,
    PROCESSING,
    REVIEW,
    NOT_CAUGHT,
    NOT_ALLOWED,
    UNAVAILABLE,
    ERROR,
    ;

    companion object {
        val BLOCKS_TAP = setOf(UNAVAILABLE, PERMISSION_NEEDED)
    }
}

/** The only result side effect is filling the editable draft. No submit callback exists. */
internal class VoiceInputPresenter(
    private val render: (VoiceInputState) -> Unit,
    private val fillDraft: (String) -> Unit,
    private val requestPermission: () -> Unit,
    private val startListening: () -> Unit,
    private val readDraft: () -> String = { "" },
    private val stopListening: () -> Unit = {},
    private val cancelProcessing: () -> Unit = {},
) {
    private var state = VoiceInputState.READY
    private var prefix = ""
    private var partial = ""

    fun availability(available: Boolean) {
        if (state in setOf(VoiceInputState.PERMISSION_NEEDED, VoiceInputState.LISTENING, VoiceInputState.PROCESSING)) return
        update(if (available) VoiceInputState.READY else VoiceInputState.UNAVAILABLE)
    }

    fun tap(permissionGranted: Boolean) {
        if (state == VoiceInputState.PROCESSING) {
            cancelProcessing()
            cancel()
            return
        }
        if (state == VoiceInputState.LISTENING) {
            stopListening()
            return
        }
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
        if (state !in setOf(VoiceInputState.LISTENING, VoiceInputState.PROCESSING)) return
        val heard = text?.takeIf { it.isNotBlank() } ?: partial
        if (heard.isBlank()) {
            update(VoiceInputState.NOT_CAUGHT)
        } else {
            fillDraft(join(heard))
            update(VoiceInputState.REVIEW)
        }
    }

    fun partial(text: String?) {
        if (state != VoiceInputState.LISTENING || text.isNullOrBlank()) return
        partial = text
        fillDraft(join(text))
    }

    private fun join(text: String): String = listOf(prefix, text.trim()).filter { it.isNotBlank() }.joinToString(" ")

    fun error(failure: VoiceInputState) {
        if (state in setOf(VoiceInputState.LISTENING, VoiceInputState.PROCESSING)) update(failure)
    }

    fun processing() {
        if (state == VoiceInputState.LISTENING) update(VoiceInputState.PROCESSING)
    }

    fun cancel() {
        if (state in setOf(VoiceInputState.LISTENING, VoiceInputState.PROCESSING)) update(VoiceInputState.READY)
    }

    private fun listen() {
        prefix = readDraft().trimEnd()
        partial = ""
        update(VoiceInputState.LISTENING)
        startListening()
    }

    private fun update(next: VoiceInputState) {
        state = next
        render(next)
    }
}
