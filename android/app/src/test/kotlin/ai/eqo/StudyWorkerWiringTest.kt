package ai.eqo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** D-009: local models are parked; a library worker must not revive the donor Hilt graph. */
class StudyWorkerWiringTest {
    @Test
    fun `plain study application has no Hilt worker registration or application entry point`() {
        val root = File("..").canonicalFile
        val application = File(root, "app/src/main/kotlin/ai/eqo/EqoApplication.kt")
        assertTrue("Missing study Application", application.isFile)
        val plainApplication = !application.readText().contains("@HiltAndroidApp")
        assertTrue("This guard must exercise the plain study Application", plainApplication)

        val sources =
            root.listFiles().orEmpty().flatMap { module ->
                File(module, "src/main")
                    .walkTopDown()
                    .filter { it.isFile && it.extension in setOf("kt", "java") }
                    .toList()
            }
        assertTrue("No production sources found", sources.isNotEmpty())
        sources.forEach { file ->
            val source = file.readText()
            val workerTypes = listOf("CoroutineWorker", "ListenableWorker", "WorkerParameters")
            if (workerTypes.any(source::contains)) {
                assertFalse("Hilt worker without Hilt Application: $file", source.contains("@HiltWorker"))
                assertFalse("Assisted worker without Hilt Application: $file", source.contains("@AssistedInject"))
                assertFalse("Application entry point in worker: $file", source.contains("EntryPointAccessors"))
            }
            assertFalse(
                "Hilt worker factory registered without Hilt Application: $file",
                source.contains("HiltWorkerFactory"),
            )
        }
    }

    @Test
    fun `study app has no model download scheduler`() {
        val sources =
            File("src/main")
                .walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java") }
                .toList()
        assertTrue("No study sources found", sources.isNotEmpty())
        sources.forEach { file ->
            val source = file.readText()
            listOf("ModelDownloadWorker", "ModelDownloadWorkRequest", "WorkManager").forEach { name ->
                assertFalse("Parked local model scheduling in $file: $name", source.contains(name))
            }
        }
    }
}
