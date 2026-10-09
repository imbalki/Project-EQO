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
    ): PairingInput? {
        val pair = pairingPort ?: knownPairingPort
        val connect = connectionPort ?: knownConnectionPort
        val valid = validPort(pair) && validPort(connect)
        if (!valid || pair == connect) return null
        return PairingInput(WirelessAdbEndpoints(pair!!, connect!!), code)
    }

    companion object {
        const val MIN_PORT = 1024
        const val MAX_PORT = 65_535
        private const val MAX_REPLY_LENGTH = 32
        private val syntax = Regex("[0-9]{6}(?:(?: +| *, *)[0-9]{4,5}){0,2}")

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

/** Waiting is before PAIR: fresh CONNECT enrollment must stay bound to the real connection port. */
internal class WirelessPendingReply(
    private val clockMs: () -> Long,
) {
    private var reply: WirelessPairingReply? = null
    private var revision = -1L
    private var expires = 0L

    fun stage(
        value: WirelessPairingReply?,
        state: WirelessDiscoveryState,
    ): Boolean {
        val pair = value?.pairingPort ?: state.pairingPort
        val eligible = value != null && state.networkId != null && WirelessPairingReply.validPort(pair)
        if (!eligible || value?.connectionPort != null || state.connectionPort != null) return false
        reply = WirelessPairingReply(value!!.code, pair, null)
        revision = state.revision
        expires = clockMs() + WAIT_MS
        return true
    }

    fun takeReady(state: WirelessDiscoveryState): WirelessPairingReply? {
        if (state.revision != revision || clockMs() >= expires) clear()
        val value = reply?.takeIf { it.request(state.pairingPort, state.connectionPort) != null }
        if (value != null) clear()
        return value
    }

    fun clear() {
        reply = null
    }

    companion object {
        const val WAIT_MS = 60_000L
    }
}
