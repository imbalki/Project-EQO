package ai.eqo.onboarding

import ai.eqo.adb.pairing.ActivationCheck
import ai.eqo.adb.pairing.ActivationReport
import ai.eqo.adb.pairing.ActivationStepRunner
import ai.eqo.adb.pairing.AdbPairingCode
import ai.eqo.adb.pairing.CheckOutcome
import ai.eqo.adb.pairing.CheckRecord
import ai.eqo.adb.pairing.PairingInput
import ai.eqo.adb.pairing.WirelessAdbEndpoints

/** In-memory only. A service is eligible only when its address belongs to this phone's Wi-Fi. */
internal class WirelessDiscoveryState {
    @Volatile
    var networkId: String? = null
        private set

    @Volatile
    var revision = 0L
        private set
    private var localAddresses = emptySet<String>()
    private val services = linkedMapOf<String, Pair<String, Int>>()
    var timedOut = false
        private set

    val pairingPort: Int? get() = uniquePort(PAIRING)
    val connectionPort: Int? get() = uniquePort(CONNECT)
    val manualFallback: Boolean get() = timedOut && endpoints() == null

    fun networkChanged(
        id: String?,
        addresses: Set<String>,
        force: Boolean = false,
    ) {
        val unchanged = networkId == id && localAddresses == addresses
        if (!force && unchanged) return
        networkId = id
        localAddresses = addresses
        services.clear()
        timedOut = false
        revision++
    }

    fun found(
        name: String,
        type: String,
        address: String?,
        port: Int,
        network: String?,
    ): Boolean {
        val local = networkId != null && network == networkId && address in localAddresses
        val normalizedType = type.trimEnd('.') + "."
        val adbType = normalizedType == PAIRING || normalizedType == CONNECT
        val validPort = port in WirelessAdbEndpoints.PORT_MIN..WirelessAdbEndpoints.PORT_MAX
        if (!local || !adbType || !validPort) return false
        services[name] = normalizedType to port
        return true
    }

    fun lost(name: String) {
        services.remove(name)
    }

    fun timeout() {
        timedOut = true
    }

    fun endpoints(): WirelessAdbEndpoints? {
        val pair = pairingPort
        val connect = connectionPort
        if (pair == null || connect == null) return null
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

/** Never invokes helper start or authorization, even on success. Uses one enrollment-owning runner. */
internal fun pairNotificationReply(
    runner: ActivationStepRunner,
    input: PairingInput,
    networkStillValid: () -> Boolean,
): ActivationReport {
    check(networkStillValid()) { "Wi-Fi changed" }
    runner.pair(input.endpoints, input.code)
    check(networkStillValid()) { "Wi-Fi changed" }
    runner.connect(input.endpoints)
    check(networkStillValid()) { "Wi-Fi changed" }
    return ActivationReport(
        listOf(
            CheckRecord(ActivationCheck.PAIR, CheckOutcome.Passed),
            CheckRecord(ActivationCheck.CONNECT, CheckOutcome.Passed),
        ),
    )
}

internal object WirelessPairingSession {
    var state = WirelessDiscoveryState()
    var discovering = false
    var observer: (() -> Unit)? = null
    var busy = false
    var message: String? = null

    fun changed() {
        observer?.invoke()
    }
}
