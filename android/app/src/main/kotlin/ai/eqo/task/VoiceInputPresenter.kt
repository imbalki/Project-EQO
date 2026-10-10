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
        val ACTIVE = setOf(LISTENING, PROCESSING)
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
) {
    private var state = VoiceInputState.READY
    private var prefix = ""
    private var partial = ""

    fun availability(available: Boolean) {
        if (state == VoiceInputState.PERMISSION_NEEDED || state in VoiceInputState.ACTIVE) return
        update(if (available) VoiceInputState.READY else VoiceInputState.UNAVAILABLE)
    }

    fun tap(permissionGranted: Boolean) {
        when (state) {
            VoiceInputState.PROCESSING -> {
                stopListening()
                cancel()
            }
            VoiceInputState.LISTENING -> stopListening()
            in VoiceInputState.BLOCKS_TAP -> Unit
            else ->
                if (permissionGranted) {
                    listen()
                } else {
                    update(VoiceInputState.PERMISSION_NEEDED)
                    requestPermission()
                }
        }
    }

    fun permissionResult(granted: Boolean) {
        if (state != VoiceInputState.PERMISSION_NEEDED) return
        if (granted) listen() else update(VoiceInputState.NOT_ALLOWED)
    }

    fun result(text: String?) {
        if (state !in VoiceInputState.ACTIVE) return
        val heard = text?.takeIf { it.isNotBlank() } ?: partial
        if (heard.isBlank()) {
            update(VoiceInputState.NOT_CAUGHT)
        } else {
            partial(heard)
            update(VoiceInputState.REVIEW)
        }
    }

    fun partial(text: String?) {
        if (state !in VoiceInputState.ACTIVE || text.isNullOrBlank()) return
        partial = text
        fillDraft(listOf(prefix, text.trim()).filter { it.isNotBlank() }.joinToString(" "))
    }

    fun error(failure: VoiceInputState) {
        if (state in VoiceInputState.ACTIVE) update(failure)
    }

    fun processing() {
        if (state == VoiceInputState.LISTENING) update(VoiceInputState.PROCESSING)
    }

    fun cancel(preservePermission: Boolean = false) {
        val pending = state == VoiceInputState.PERMISSION_NEEDED && !preservePermission
        if (state in VoiceInputState.ACTIVE || pending) update(VoiceInputState.READY)
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
