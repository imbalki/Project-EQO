// Origin: imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536,
//   path: app/src/main/kotlin/ai/closepaw/browser/cdp/wireless/TlsExporter.kt
// TASK-008 (issue #13): extracted into :adb-pairing for the guided on-phone pairing flow.
// Changes vs donor: package renamed to ai.eqo.adb.pairing; default peer label rebranded to
// EQO. Provenance record: android/Phase-One/evidence/task-008-wireless-adb-pairing.md.
package ai.eqo.adb.pairing

import org.conscrypt.Conscrypt
import java.io.IOException
import javax.net.ssl.SSLSocket

/**
 * Wraps Conscrypt's RFC 5705 TLS exporter. We bundle org.conscrypt:conscrypt-android as a
 * dependency rather than reflect at the platform Conscrypt: HiddenApiBypass is unreliable
 * on some Android builds (the class loader can't see hidden API bytecode at all on certain
 * vendors), and the bundled AAR is ~3 MB — small price for a stable handshake.
 */
internal object TlsExporter {
    fun export(
        socket: SSLSocket,
        label: String,
        context: ByteArray?,
        length: Int,
    ): ByteArray =
        try {
            Conscrypt.exportKeyingMaterial(socket, label, context, length)
        } catch (t: Throwable) {
            throw IOException(
                "TLS exporter call failed: ${t.message}. SSLSocket impl=${socket.javaClass.name}; " +
                    "Conscrypt.isConscrypt=${runCatching { Conscrypt.isConscrypt(socket) }.getOrNull()}",
                t,
            )
        }
}
