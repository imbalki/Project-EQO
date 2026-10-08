package ai.eqo.onboarding

import ai.eqo.adb.pairing.AdbPairingCode
import ai.eqo.adb.pairing.WirelessAdbEndpoints

/** In-memory only. A service is eligible only when its address belongs to this phone's Wi-Fi. */
internal class WirelessDiscoveryState {
    @Volatile
    var networkId: String? = null
        private set
    private var localAddresses = emptySet<String>()
    private val services = linkedMapOf<String, Pair<String, Int>>()
    var timedOut = false
        private set

    val pairingPort: Int? get() = uniquePort(PAIRING)
    val connectionPort: Int? get() = uniquePort(CONNECT)
    val manualFallback: Boolean get() = timedOut && (pairingPort == null || connectionPort == null)

    fun networkChanged(
        id: String?,
        addresses: Set<String>,
    ) {
        if (networkId == id && localAddresses == addresses) return
        networkId = id
        localAddresses = addresses
        services.clear()
        timedOut = false
    }

    fun found(
        name: String,
        type: String,
        address: String?,
        port: Int,
        network: String?,
    ): Boolean {
        if (networkId == null || network != networkId || address !in localAddresses) return false
        if (type != PAIRING && type != CONNECT) return false
        if (port !in WirelessAdbEndpoints.PORT_MIN..WirelessAdbEndpoints.PORT_MAX) return false
        services[name] = type to port
        return true
    }

    fun lost(name: String) {
        services.remove(name)
    }

    fun timeout() {
        timedOut = true
    }

    fun endpoints(): WirelessAdbEndpoints? {
        val pair = pairingPort ?: return null
        val connect = connectionPort ?: return null
        return WirelessAdbEndpoints(pair, connect).takeUnless { it.isPortConfusion }
    }

    private fun uniquePort(type: String): Int? =
        services.values
            .filter { it.first == type }
            .map { it.second }
            .distinct()
            .singleOrNull()

    companion object {
        const val PAIRING = "_adb-tls-pairing._tcp."
        const val CONNECT = "_adb-tls-connect._tcp."
        const val TIMEOUT_MS = 10_000L
    }
}

/** Strict six ASCII digits, including leading zeroes; no code is retained in a failed result. */
internal fun notificationPairingCode(raw: CharSequence?): AdbPairingCode? =
    (AdbPairingCode.parse(raw?.toString().orEmpty()) as? AdbPairingCode.ParseResult.Ok)?.code

internal object WirelessPairingSession {
    var state = WirelessDiscoveryState()
    var observer: (() -> Unit)? = null
    var busy = false
    var message: String? = null

    fun changed() {
        observer?.invoke()
    }
}
