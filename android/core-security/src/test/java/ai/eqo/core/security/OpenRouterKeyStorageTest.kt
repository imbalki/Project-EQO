package ai.eqo.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TASK-006 acceptance: the OpenRouter key is stored encrypted and never
 * appears in log output.
 *
 * The storage half re-uses the audited upstream envelope already covered by
 * [ProviderCredentialStoreTest]; this class adds the acceptance-shaped check
 * for the OpenRouter credential specifically: write it, then scan everything
 * a log surface could plausibly render for the raw key text.
 */
class OpenRouterKeyStorageTest {
    private val openRouterKey = "sk-or-v1-task006-openrouter-key-material-8f3a1c"

    @Test
    fun `openrouter key at rest is a versioned ciphertext envelope and never plaintext`() {
        val records = EnvelopeCaptureRecords()
        val store =
            ProviderCredentialStoreImpl(
                records = KeystoreSecretRecords(records, TestCipher()),
                legacyCredentials = EmptyLegacyCredentials(),
            )

        assertTrue(
            store.write(ProviderCredentialId.ApiKey("OpenRouter"), openRouterKey) is
                CredentialStoreResult.Success,
        )

        val rawEnvelope = records.records.values.single()
        assertTrue("record is versioned envelope", rawEnvelope.startsWith("v1."))
        assertFalse("envelope contains plaintext key", rawEnvelope.contains(openRouterKey))

        assertEquals(
            openRouterKey,
            (store.read(ProviderCredentialId.ApiKey("OpenRouter")) as CredentialStoreResult.Success).value,
        )
    }

    @Test
    fun `openrouter key text never appears in loggable store output`() {
        val records = EnvelopeCaptureRecords()
        val cipher = TestCipher()
        val store =
            ProviderCredentialStoreImpl(
                records = KeystoreSecretRecords(records, cipher),
                legacyCredentials = EmptyLegacyCredentials(),
            )

        store.write(ProviderCredentialId.ApiKey("OpenRouter"), openRouterKey)

        // Collect every string the at-rest storage layer could put into a log
        // line: storage keys, envelope strings, cipher metadata. The decrypted
        // value is only ever returned to callers in-process and is covered by
        // the LogRedactor acceptance in :core-llm — here we assert the
        // persistence and log-side boundaries never render key text.
        val loggable =
            buildList {
                records.records.keys.forEach(::add)
                records.records.values.forEach(::add)
                add(cipher.lastIvBase64.orEmpty())
                add(store.recoveryState.value.toString())
            }.joinToString(" ")

        assertFalse("loggable storage output contains the raw key", loggable.contains(openRouterKey))
        assertFalse("loggable storage output contains the key head", loggable.contains(openRouterKey.take(8)))
        assertFalse("loggable storage output contains the key tail", loggable.contains(openRouterKey.takeLast(8)))
    }

    @Test
    fun `failure results never carry the key`() {
        val store =
            ProviderCredentialStoreImpl(
                records = KeystoreSecretRecords(EnvelopeCaptureRecords(), TestCipher().apply { keyAvailable = false }),
                legacyCredentials = EmptyLegacyCredentials(),
            )

        val failed = store.write(ProviderCredentialId.ApiKey("OpenRouter"), openRouterKey)
        assertTrue(failed is CredentialStoreResult.CredentialsMustBeReentered)
        assertFalse(failed.toString().contains(openRouterKey))
    }

    private companion object {
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private class TestCipher : SecretAeadCipher {
        private val key = javax.crypto.spec.SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        private val random = java.security.SecureRandom()
        private val base64 =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
        var keyAvailable = true
        var lastIvBase64: String? = null

        override fun encrypt(
            plaintext: ByteArray,
            aad: ByteArray,
        ): EncryptedSecret {
            checkKeyAvailable()
            val iv = ByteArray(12).also(random::nextBytes)
            lastIvBase64 = base64.encodeToString(iv)
            val cipher = javax.crypto.Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            return EncryptedSecret(iv, cipher.doFinal(plaintext))
        }

        override fun decrypt(
            iv: ByteArray,
            ciphertext: ByteArray,
            aad: ByteArray,
        ): ByteArray {
            checkKeyAvailable()
            val cipher = javax.crypto.Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            return cipher.doFinal(ciphertext)
        }

        override fun resetForReentry() {
            keyAvailable = true
        }

        private fun checkKeyAvailable() {
            if (!keyAvailable) throw SecretKeyUnavailableException()
        }
    }

    private class EnvelopeCaptureRecords : SecretRecordStorage {
        val records = linkedMapOf<String, String>()

        override fun read(key: String): String? = records[key]

        override fun write(
            key: String,
            value: String,
        ): Boolean {
            records[key] = value
            return true
        }

        override fun remove(key: String): Boolean {
            records.remove(key)
            return true
        }

        override fun keys(): Set<String> = records.keys.toSet()
    }

    private class EmptyLegacyCredentials : LegacySecretSource {
        override fun keys(): SecretRecordResult<Set<String>> = SecretRecordResult.Success(emptySet())

        override fun readString(key: String): SecretRecordResult<String?> = SecretRecordResult.Success(null)

        override fun readBoolean(key: String): SecretRecordResult<Boolean?> = SecretRecordResult.Success(null)

        override fun remove(key: String): SecretRecordResult<Unit> = SecretRecordResult.Success(Unit)
    }
}
