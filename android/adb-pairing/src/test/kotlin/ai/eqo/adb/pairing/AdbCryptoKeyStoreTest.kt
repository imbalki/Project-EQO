package ai.eqo.adb.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

/**
 * Host-JVM test for the extracted persistent RSA key material: the exact blob adbd must
 * accept into /data/misc/adb/adb_keys after a successful pair.
 */
class AdbCryptoKeyStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(): AdbCryptoKeyStore = AdbCryptoKeyStore(File(tmp.root, "adb-keys"))

    @Test
    fun loadOrCreatePersistsAndReloadsTheSameKey() {
        val first = store()
        first.loadOrCreate()
        assertTrue(first.isPersisted())
        val pubkey = first.androidPubkeyBase64()

        val second = store()
        second.loadOrCreate()
        assertTrue(second.isPersisted())
        assertEquals(pubkey, second.androidPubkeyBase64())
    }

    @Test
    fun androidPubkeyBlobIsThe524ByteAospFormat() {
        val blob = Base64.getDecoder().decode(store().androidPubkeyBase64())
        assertEquals(AndroidPubkey.ENCODED_SIZE, blob.size)
    }

    @Test
    fun resetErasesTheKeyMaterial() {
        val first = store()
        first.loadOrCreate()
        first.reset()
        assertFalse(first.isPersisted())
    }

    @Test
    fun mainSourcesCarryNoUpstreamProductNameOutsideComments() {
        // Mirrors scripts/check-branding.sh at unit level: no upstream product name may
        // appear outside comment lines in this module's production sources.
        val mainDir = File("src/main/kotlin")
        assertTrue("module sources must exist", mainDir.isDirectory)
        val banned = Regex("closepaw|opendroid", RegexOption.IGNORE_CASE)
        val offenders =
            mainDir
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file -> file.readLines().map { line -> file.name to line } }
                .filter { (_, line) ->
                    val trimmed = line.trimStart()
                    val isComment = trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")
                    !isComment && banned.containsMatchIn(line)
                }.toList()
        assertEquals("non-comment upstream names in module sources: $offenders", emptyList<Any>(), offenders)
    }
}
