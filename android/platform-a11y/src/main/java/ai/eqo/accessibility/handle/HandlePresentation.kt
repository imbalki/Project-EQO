// Origin: EQO-authored ephemeral handle timing; no platform dependencies or persisted content.
package ai.eqo.accessibility.handle

/** Deadlines use monotonic time in production and an injectable clock in tests. */
class HandlePresentation(
    private val now: () -> Long,
) {
    var expanded = false
        private set
    private var collapseAt = 0L
    private var resultUntil = 0L
    private var hiddenPackage: String? = null

    fun open() {
        expanded = true
        interact()
    }

    fun interact() {
        if (expanded) collapseAt = now() + COLLAPSE_MS
    }

    fun collapse() {
        expanded = false
    }

    fun tick(): Boolean {
        val expired = expanded && now() >= collapseAt
        if (expired) collapse()
        return expired
    }

    fun inserted() {
        resultUntil = now() + RESULT_MS
    }

    fun resultVisible(): Boolean = now() < resultUntil

    fun hide(packageName: String) {
        hiddenPackage = packageName
        collapse()
    }

    fun hidden(packageName: String): Boolean = hiddenPackage == packageName

    fun foregroundChanged() {
        hiddenPackage = null
        resultUntil = 0L
        collapse()
    }

    companion object {
        const val COLLAPSE_MS = 3_000L
        const val RESULT_MS = 2_000L
    }
}

/** Stay above the IME and outside the focused input, or disappear when neither side has room. */
fun fitHandleY(
    preferred: Int,
    height: Int,
    available: Int,
    inputTop: Int?,
    inputBottom: Int?,
): Int? {
    val fitted = preferred.coerceIn(0, maxOf(0, available - height))
    return when {
        height > available -> null
        inputTop == null || inputBottom == null || fitted + height <= inputTop || fitted >= inputBottom -> fitted
        inputTop >= height -> inputTop - height
        available - inputBottom >= height -> inputBottom
        else -> null
    }
}
