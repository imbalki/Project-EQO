/*
 * EQO (TASK-008, issue #13): the six-digit code Android shows in the
 * "Pair device with pairing code" dialog.
 *
 * The code is the TLS-PSK password of the pairing handshake; it is typed by the owner
 * from the phone's own screen, never generated or stored by EQO. A malformed entry is
 * refused before any socket is opened, with guidance — never silently coerced.
 */
package ai.eqo.adb.pairing

data class AdbPairingCode private constructor(
    val digits: String,
) {
    companion object {
        /** Android renders the pairing code as exactly six digits. */
        const val LENGTH = 6

        fun parse(raw: String): ParseResult {
            val trimmed = raw.trim()
            if (trimmed.length != LENGTH || !trimmed.all { it in '0'..'9' }) {
                return ParseResult.Malformed(raw)
            }
            return ParseResult.Ok(AdbPairingCode(trimmed))
        }
    }

    sealed class ParseResult {
        data class Ok(
            val code: AdbPairingCode,
        ) : ParseResult()

        data class Malformed(
            val raw: String,
        ) : ParseResult()
    }
}
