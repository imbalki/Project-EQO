// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/core/llm/ModelDownloadWorker.kt
package ai.eqo.core.llm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * D-009: local Gemma is parked in the study build. Keep the worker class name so persisted
 * donor requests fail closed rather than accessing an unsupported Hilt Application graph.
 * Construction and execution require no database, credentials, network, files or foreground service.
 * Restoring downloads requires an explicitly wired runtime and a separate local-model decision.
 */
class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = Result.failure()

    companion object {
        internal fun verificationFailureMessage(failure: ArtifactVerificationFailure?): String =
            when (failure) {
                ArtifactVerificationFailure.METADATA_UNAVAILABLE ->
                    "In-app download is unavailable until publisher integrity metadata is recorded."
                ArtifactVerificationFailure.SIZE_MISMATCH ->
                    "Downloaded model size does not match the published artifact."
                ArtifactVerificationFailure.HASH_MISMATCH -> "Downloaded model failed its integrity check."
                ArtifactVerificationFailure.FORMAT_INVALID -> "Downloaded model is not compatible with LiteRT."
                ArtifactVerificationFailure.LITERT_RUNTIME_INCOMPATIBLE ->
                    "This LiteRT model could not be initialized on this device: no supported backend could load it."
                else -> "Could not securely install the downloaded model."
            }
    }
}
