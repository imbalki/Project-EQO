package ai.eqo.core.llm

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A persisted legacy request must fail closed even with a plain, non-Hilt Application. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ParkedModelDownloadWorkerTest {
    @Test
    fun `plain application can construct parked worker and work fails without side effects`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val worker =
                TestListenableWorkerBuilder<ModelDownloadWorker>(
                    context,
                    inputData = Data.Builder().putString("model_id", "gemma").build(),
                ).build()

            assertEquals(ListenableWorker.Result.failure(), worker.doWork())
        }
}
