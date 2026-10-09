package ai.eqo.onboarding

import ai.eqo.adb.pairing.AdbPairingCode
import ai.eqo.adb.pairing.PairingInput
import ai.eqo.adb.pairing.WirelessAdbEndpoints

/** Transient only; never include credentials in diagnostics or generated data-class strings. */
internal class WirelessPairingReply(
    val code: AdbPairingCode,
    val pairingPort: Int?,
    val connectionPort: Int?,
) {
    fun request(
        knownPairingPort: Int?,
        knownConnectionPort: Int?,
    ): WirelessPairingRequest? {
        val pair = pairingPort ?: knownPairingPort
        val connect = connectionPort ?: knownConnectionPort
        val valid = validPort(pair) && (connect == null || validPort(connect))
        if (!valid || pair == connect) return null
        // ADB's pair operation does not use connectionPort. Do not guess a connection endpoint.
        val unused = if (pair == MIN_PORT) MIN_PORT + 1 else MIN_PORT
        val input = PairingInput(WirelessAdbEndpoints(pair!!, connect ?: unused), code)
        return WirelessPairingRequest(input, connect != null)
    }

    companion object {
        const val MIN_PORT = 1024
        const val MAX_PORT = 65_535
        private const val MAX_REPLY_LENGTH = 32
        private val syntax = Regex("[0-9]{6}(?:[ ,]+[0-9]{4,5}){0,2}")

        fun validPort(port: Int?): Boolean = port != null && port in MIN_PORT..MAX_PORT

        @Suppress("ReturnCount") // Explicit fail-closed input guards; no malformed code is retained.
        fun parse(raw: CharSequence?): WirelessPairingReply? {
            val text = raw?.toString()?.trim() ?: return null
            if (text.length > MAX_REPLY_LENGTH || !syntax.matches(text)) return null
            val parts = text.split(Regex("[ ,]+"))
            val code = (AdbPairingCode.parse(parts[0]) as? AdbPairingCode.ParseResult.Ok)?.code ?: return null
            val ports = parts.drop(1).map { it.toInt() }
            if (ports.any { !validPort(it) }) return null
            return WirelessPairingReply(code, ports.getOrNull(0), ports.getOrNull(1))
        }
    }
}

internal class WirelessPairingRequest(
    val input: PairingInput,
    val connect: Boolean,
)
