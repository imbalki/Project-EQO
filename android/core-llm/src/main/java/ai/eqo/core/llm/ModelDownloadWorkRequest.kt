// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/core/llm/ModelDownloadWorkRequest.kt
package ai.eqo.core.llm

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import java.util.concurrent.TimeUnit

/** Schedules each model transfer with the constraints needed for safe resumable downloads. */
internal object ModelDownloadWorkRequest {
    internal const val RETRY_BACKOFF_SECONDS = 30L

    fun create(
        inputData: Data,
        modelId: String,
    ): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints
                    .Builder()
                    // Multi-GB downloads are allowed over connected networks (cellular or Wi-Fi).
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            ).setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            ).setInputData(inputData)
            .addTag("download_$modelId")
            .build()
}
