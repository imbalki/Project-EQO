package ai.eqo.task

/** Android MotionEvent obscuration bits, kept pure for host-side safety tests. */
internal class ConfirmationTouchGuard {
    private var blockedGesture = false

    /** A single obscured event poisons the gesture until a fresh, unobscured DOWN. */
    fun accepts(
        flags: Int,
        isDown: Boolean,
    ): Boolean {
        if (isDown) blockedGesture = false
        if (!acceptsConfirmationTouch(flags)) blockedGesture = true
        return !blockedGesture
    }
}

internal fun acceptsConfirmationTouch(flags: Int): Boolean = flags and OBSCURATION_FLAGS == 0

// MotionEvent.FLAG_WINDOW_IS_OBSCURED (1) | FLAG_WINDOW_IS_PARTIALLY_OBSCURED (2).
private const val OBSCURATION_FLAGS = 0x1 or 0x2
