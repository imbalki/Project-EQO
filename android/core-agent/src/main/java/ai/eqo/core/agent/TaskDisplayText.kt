package ai.eqo.core.agent

/** Display-only escaping. Never feed this representation back into execution parameters. */
object TaskDisplayText {
    private const val HEX_WIDTH = 4
    private const val HEX_RADIX = 16

    fun escape(value: String): String =
        buildString {
            value.codePoints().forEach { codePoint ->
                if (codePoint == '\\'.code) {
                    append("\\\\")
                } else if (hidden(codePoint)) {
                    append("\\u{")
                    append(codePoint.toString(HEX_RADIX).uppercase().padStart(HEX_WIDTH, '0'))
                    append('}')
                } else {
                    appendCodePoint(codePoint)
                }
            }
        }

    private fun hidden(codePoint: Int): Boolean =
        Character.getType(codePoint) in
            setOf(
                Character.FORMAT.toInt(),
                Character.CONTROL.toInt(),
                Character.LINE_SEPARATOR.toInt(),
                Character.PARAGRAPH_SEPARATOR.toInt(),
            )
}
