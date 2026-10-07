/*
 * EQO (TASK-008, issue #13): device-side runner for the five activation checks.
 *
 * Wires the extracted ClosePaw pairing stack ([AdbPairingClient], [AdbTlsClient],
 * [AdbWireProtocolClient]) to [ActivationStepRunner], and reports every failure as a
 * typed [StepSignal] so [FailureClassifier] can turn it into recovery guidance.
 *
 * Pairing and connect are implemented here on the extracted stack. Helper start,
 * authorize and binder health are SEPARATE auth planes (task Notes) whose runtime
 * implementation lives with the TASK-007 privileged helper; they enter through the
 * [HelperHooks] seam and each fails on its own.
 */
package ai.eqo.adb.pairing

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/** Bridge to the TASK-007 privileged helper's start/authorize/binder checks. */
interface HelperHooks {
    fun startHelper()

    fun authorizeHelper()

    fun checkBinder()
}

class WirelessAdbActivationRunner(
    private val keyStore: AdbCryptoKeyStore,
    private val helper: HelperHooks,
    private val handshakeTimeoutMs: Int = DEFAULT_HANDSHAKE_TIMEOUT_MS,
    /**
     * TASK-080: the [HelperStartCommand] to run over the pinned connection in [startHelper]. When
     * null the start is delegated entirely to [HelperHooks] (host tests, or a build with no helper).
     */
    private val helperStartCommand: String? = null,
) : ActivationStepRunner {
    private val pairingClient = AdbPairingClient(keyStore, deviceLabel = PEER_LABEL)
    private var pendingEnrollment: ConnectKeyEnrollment? = null

    @Volatile
    private var connectedPort: Int? = null

    @Suppress("TooGenericExceptionCaught") // every transport error must become a typed signal
    override fun pair(
        endpoints: WirelessAdbEndpoints,
        code: AdbPairingCode,
    ) {
        pendingEnrollment = null
        connectedPort = null
        try {
            pendingEnrollment =
                kotlinx.coroutines.runBlocking {
                    ConnectKeyEnrollment.afterPairing(LOCALHOST, endpoints.connectionPort) {
                        pairingClient.pair(
                            host = LOCALHOST,
                            port = endpoints.pairingPort,
                            psk = code.digits.toByteArray(Charsets.UTF_8),
                            timeoutMs = handshakeTimeoutMs,
                        )
                    }
                }
        } catch (e: Exception) {
            throw mapTransport(e, StepSignal.PAIRING_CODE_REJECTED)
        }
    }

    /**
     * The connect check is the mTLS adb session against the CONNECTION port (never the
     * pairing port): after the TLS handshake adbd speaks first with A_CNXN. An A_AUTH
     * frame here means adbd does not accept our key any more (revoked / rotated).
     */
    @Suppress("TooGenericExceptionCaught") // every transport error must become a typed signal
    override fun connect(endpoints: WirelessAdbEndpoints) {
        connectedPort = null
        val enrollment = pendingEnrollment
        pendingEnrollment = null // consume even on failed dial; reconnect can never enroll
        try {
            val channel =
                AdbTlsClient.connectWithStls(
                    host = LOCALHOST,
                    port = endpoints.connectionPort,
                    keyStore = keyStore,
                    handshakeTimeoutMs = handshakeTimeoutMs,
                    enrollment = enrollment,
                )
            channel.use {
                val msg = AdbProtocol.Message.read(it.inputStream)
                when (msg.command) {
                    AdbProtocol.A_CNXN -> Unit
                    AdbProtocol.A_AUTH ->
                        throw StepSignalException(
                            StepSignal.AUTH_REJECTED,
                            "adbd sent A_AUTH on the TLS port (pubkey not in adb_keys)",
                        )
                    else ->
                        throw StepSignalException(
                            StepSignal.PORT_REFUSED,
                            "unexpected frame from adbd: 0x${"%08x".format(msg.command)}",
                        )
                }
            }
            connectedPort = endpoints.connectionPort
        } catch (e: StepSignalException) {
            throw e
        } catch (e: Exception) {
            throw mapTransport(e, StepSignal.PORT_REFUSED)
        }
    }

    /**
     * Starts the privileged helper: the validated [HelperStartCommand] runs over the pinned
     * connection (the only use EQO makes of ADB), then [HelperHooks.startHelper] lets the app
     * begin waiting for the helper binder.
     */
    override fun startHelper() =
        runHelper(StepSignal.HELPER_NOT_STARTED) {
            val command = helperStartCommand
            if (command != null) {
                val port =
                    connectedPort
                        ?: throw StepSignalException(StepSignal.HELPER_NOT_STARTED, "connect did not pass first")
                try {
                    AdbShellLauncher(keyStore).runHelperStart(LOCALHOST, port, command, handshakeTimeoutMs)
                } catch (e: java.io.IOException) {
                    throw mapTransport(e, StepSignal.HELPER_NOT_STARTED)
                }
            }
            helper.startHelper()
        }

    override fun authorizeHelper() = runHelper(StepSignal.HELPER_NOT_AUTHORIZED) { helper.authorizeHelper() }

    override fun checkBinder() = runHelper(StepSignal.BINDER_DEAD) { helper.checkBinder() }

    @Suppress("TooGenericExceptionCaught") // a hook failure must become a typed signal
    private fun runHelper(
        fallback: StepSignal,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: StepSignalException) {
            throw e
        } catch (e: Exception) {
            throw StepSignalException(fallback, e.message ?: e.javaClass.simpleName, e)
        }
    }

    /**
     * Transport errors to typed signals. Refused/timeout = the port that was dialled is
     * not the live one (stale after reboot or swapped pairing/connection ports). TLS
     * failures during pairing are the wrong-code case: the pairing PSK is mixed into the
     * handshake, so a rejected code surfaces as a TLS/handshake error. Anything else
     * keeps the caller's default so it is never silently downgraded to "passed".
     */
    @Suppress("TooGenericExceptionCaught") // classifies by concrete subtype in the when below
    internal fun mapTransport(
        e: Exception,
        default: StepSignal,
    ): StepSignalException {
        val signal =
            when (e) {
                is StepSignalException -> return e
                is ServerNotEnrolledException -> StepSignal.SERVER_NOT_ENROLLED
                is ServerKeyMismatchException -> StepSignal.SERVER_KEY_MISMATCH
                is ConnectException, is SocketTimeoutException -> StepSignal.PORT_REFUSED
                is SSLException -> default
                is IOException ->
                    if (e.message.orEmpty().contains("A_AUTH")) {
                        StepSignal.AUTH_REJECTED
                    } else {
                        default
                    }
                else -> default
            }
        return StepSignalException(signal, e.message ?: e.javaClass.simpleName, e)
    }

    companion object {
        const val LOCALHOST = "127.0.0.1"
        const val PEER_LABEL = "EQO"
        const val DEFAULT_HANDSHAKE_TIMEOUT_MS = 10_000
    }
}
