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

internal fun acceptsConfirmationTouch(flags: Int): Boolean = flags and TOUCH_POINT_OBSCURED == 0

// Match filterTouchesWhenObscured: reject an overlay covering the touch point.
// PARTIALLY_OBSCURED alone can be an OEM overlay elsewhere on the screen.
private const val TOUCH_POINT_OBSCURED = 0x1
