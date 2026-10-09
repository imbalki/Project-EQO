// Origin: EQO edge-handle task; coordinate guard independent of the underlying active app window.
package ai.eqo.accessibility.handle

/** Pure hitboxes, shared by takeover attribution and automation. Never clears a takeover latch. */
class HandleWindowGuard {
    data class Bounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        fun contains(
            x: Int,
            y: Int,
        ): Boolean = x >= left && x < right && y >= top && y < bottom
    }

    @Volatile
    var bounds: Bounds? = null

    @Volatile
    var panelOpen: Boolean = false

    fun contains(
        x: Int,
        y: Int,
    ): Boolean = bounds?.contains(x, y) == true

    fun blocksGesture(
        x: Float,
        y: Float,
    ): Boolean = panelOpen || contains(x.toInt(), y.toInt())

    fun clear() {
        bounds = null
        panelOpen = false
    }

    companion object {
        val shared = HandleWindowGuard()
    }
}
