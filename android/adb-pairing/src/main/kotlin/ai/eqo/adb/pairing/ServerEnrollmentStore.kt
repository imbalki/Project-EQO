/*
 * EQO (TASK-080, issue #20): the persisted server enrollment behind connect-plane pinning.
 *
 * The record lives in the app-private key store directory (never in shared storage), is
 * bound to the client keypair (a regenerated or copied client key invalidates it) and
 * carries an HMAC keyed from the client private key, so an edited or copied-in file is
 * rejected. It holds only a hash of a public key, and nothing here logs.
 */
package ai.eqo.adb.pairing

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ServerEnrollmentStore(
    private val baseDir: File,
    private val keys: AdbCryptoKeyStore,
) {
    /** Records the adbd this client just paired with. Replaces any earlier enrollment (re-pair). */
    @Synchronized
    fun enroll(
        key: ServerKeyFingerprint,
        peerGuid: String?,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val material = keys.loadOrCreate()
        val body =
            listOf(
                "v=1",
                "client=${clientBinding(material)}",
                "server=${key.encode()}",
                "guid=${sanitizeGuid(peerGuid)}",
                "at=$nowMs",
            ).joinToString("\n")
        val record = body + "\nmac=" + mac(material, body) + "\n"
        val tmp = File(baseDir, "$FILE_NAME.tmp")
        tmp.writeText(record, Charsets.UTF_8)
        Files.move(
            tmp.toPath(),
            File(baseDir, FILE_NAME).toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    /** The enrolled server, or null when none is enrolled or the record fails validation. */
    @Synchronized
    fun current(): EnrolledServer? {
        val file = File(baseDir, FILE_NAME)
        val readable = keys.isPersisted() && file.isFile
        val lines = if (readable) runCatching { file.readLines(Charsets.UTF_8) }.getOrNull() else null
        return lines?.let(::parseAndVerify)
    }

    /** Forget the enrolled server (the next connect refuses until the owner pairs again). */
    @Synchronized
    fun clear() {
        Files.deleteIfExists(File(baseDir, FILE_NAME).toPath())
    }

    private fun parseAndVerify(lines: List<String>): EnrolledServer? {
        val material = keys.loadOrCreate()
        val body = lines.filterNot { it.startsWith(MAC_PREFIX) || it.isEmpty() }.joinToString("\n")
        val fields =
            lines
                .mapNotNull { line ->
                    line.indexOf('=').takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 1) }
                }.toMap()
        val key = fields["server"]?.let(ServerKeyFingerprint::decode)
        val at = fields["at"]?.toLongOrNull()
        val macMatches =
            MessageDigest.isEqual(
                fields["mac"].orEmpty().toByteArray(Charsets.US_ASCII),
                mac(material, body).toByteArray(Charsets.US_ASCII),
            )
        val valid = fields["v"] == "1" && fields["client"] == clientBinding(material) && macMatches
        return if (valid && key != null && at != null) {
            EnrolledServer(key, fields["guid"]?.takeIf { it.isNotEmpty() }, at)
        } else {
            null
        }
    }

    private fun clientBinding(material: AdbCryptoKeyStore.Material): String =
        Base64
            .getEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(material.keyPair.public.encoded))

    private fun mac(
        material: AdbCryptoKeyStore.Material,
        body: String,
    ): String {
        val macKey = MessageDigest.getInstance("SHA-256").digest(MAC_CONTEXT + material.keyPair.private.encoded)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(macKey, "HmacSHA256"))
        return Base64.getEncoder().withoutPadding().encodeToString(mac.doFinal(body.toByteArray(Charsets.UTF_8)))
    }

    private fun sanitizeGuid(guid: String?): String =
        guid.orEmpty().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(MAX_GUID)

    companion object {
        const val FILE_NAME = "server-enrollment.v1"
        private const val MAC_PREFIX = "mac="
        private const val MAX_GUID = 128
        private val MAC_CONTEXT = "eqo-adb-server-enrollment-v1".toByteArray(Charsets.US_ASCII)
    }
}
