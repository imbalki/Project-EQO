package ai.eqo.onboarding

import ai.eqo.EqoApplication
import ai.eqo.core.security.CredentialStoreResult
import ai.eqo.core.security.ProviderCredentialId
import ai.eqo.core.security.ProviderCredentialRecoveryState
import ai.eqo.core.security.ProviderCredentialStore
import ai.eqo.task.StudyModelChoice
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = EqoApplication::class)
class ModelKeyRestoreTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private var previous = ModelKeyState.NOT_SET

    @Before fun remember() {
        previous = StudySetup.modelKey
        StudySetup.modelKey = ModelKeyState.NOT_SET
    }

    @After fun restore() {
        StudySetup.modelKey = previous
    }

    private class FakeStore(
        private val result: CredentialStoreResult<String?>,
    ) : ProviderCredentialStore {
        override val recoveryState: StateFlow<ProviderCredentialRecoveryState> =
            MutableStateFlow(ProviderCredentialRecoveryState.Ready)

        override fun read(credential: ProviderCredentialId) = result

        override fun readProviderApiKeys() = CredentialStoreResult.Success(emptyMap<String, String>())

        override fun write(
            credential: ProviderCredentialId,
            value: String,
        ) = CredentialStoreResult.Success(Unit)

        override fun remove(credential: ProviderCredentialId) = CredentialStoreResult.Success(Unit)

        override fun migrateLegacyCredentials() = CredentialStoreResult.Success(Unit)

        override fun resetForReentry() = CredentialStoreResult.Success(Unit)
    }

    private fun restoreWith(result: CredentialStoreResult<String?>): ModelKeyState {
        StudySetup.restoreModelKey(context, FakeStore(result))
        return StudySetup.modelKey
    }

    @Test fun savedKeyAndTestedModelKeepTheRowReadyAfterARestart() {
        StudyModelChoice.save(context, "example/model")
        assertEquals(ModelKeyState.CONNECTED, restoreWith(CredentialStoreResult.Success("stored")))
    }

    @Test fun missingOrUnreadableKeyStaysNotSetSoTheOwnerIsToldTheTruth() {
        StudyModelChoice.save(context, "example/model")
        assertEquals(ModelKeyState.NOT_SET, restoreWith(CredentialStoreResult.Success(null)))
        assertEquals(ModelKeyState.NOT_SET, restoreWith(CredentialStoreResult.Success("")))
        assertEquals(ModelKeyState.NOT_SET, restoreWith(CredentialStoreResult.CredentialsMustBeReentered))
        assertEquals(ModelKeyState.NOT_SET, restoreWith(CredentialStoreResult.StorageUnavailable))
    }

    @Test fun anInFlightOrFailedStateIsNeverOverwritten() {
        StudyModelChoice.save(context, "example/model")
        StudySetup.modelKey = ModelKeyState.FAILED
        assertEquals(ModelKeyState.FAILED, restoreWith(CredentialStoreResult.Success("stored")))
    }
}
