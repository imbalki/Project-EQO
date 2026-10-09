package ai.eqo.explain

/** UI-only state; contains no screen text. Clock supplied for deterministic timeout tests. */
internal class ExplainPanelState(
    private val now: () -> Long,
) {
    enum class Size { SMALL, MEDIUM, LARGE }

    var size = Size.MEDIUM
    private var seeUntil = 0L

    val seeThrough: Boolean get() = now() < seeUntil
    val alpha: Float get() = if (seeThrough) SEE_ALPHA else 1f

    fun seeScreen() {
        seeUntil = now() + SEE_MS
    }

    fun height(
        screenHeight: Int,
        chipHeight: Int,
    ): Int =
        when (size) {
            Size.SMALL -> chipHeight.coerceAtMost((screenHeight * MAX_FRACTION).toInt())
            Size.MEDIUM -> (screenHeight * MEDIUM_FRACTION).toInt()
            Size.LARGE -> (screenHeight * MAX_FRACTION).toInt()
        }

    companion object {
        const val SEE_MS = 5000L
        const val SEE_ALPHA = 0.05f
        const val MAX_FRACTION = 0.35f
        const val MEDIUM_FRACTION = 0.25f
        const val BACKGROUND_ALPHA = 217 // 85% of 255, text stays fully opaque.
    }
}
