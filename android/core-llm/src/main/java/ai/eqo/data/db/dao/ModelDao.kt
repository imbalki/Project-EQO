// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/data/db/dao/ModelDao.kt
package ai.eqo.data.db.dao

import ai.eqo.data.db.entities.ModelEntity
import ai.eqo.data.db.entities.ModelStatus
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY installedAt DESC")
    fun getAllModels(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE id = :id")
    suspend fun getModelById(id: String): ModelEntity?

    @Query("SELECT * FROM models WHERE id = :id")
    fun getModelByIdFlow(id: String): Flow<ModelEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity)

    @Query("UPDATE models SET status = :status WHERE id = :id")
    suspend fun updateModelStatus(
        id: String,
        status: ModelStatus,
    )

    @Query("UPDATE models SET downloadProgress = :progress, status = :status WHERE id = :id")
    suspend fun updateModelProgress(
        id: String,
        progress: Int,
        status: ModelStatus,
    )

    @Query(
        "UPDATE models SET downloadProgress = :progress, downloadedSize = :downloadedSize, downloadSpeed = :downloadSpeed, etaString = :eta, status = :status WHERE id = :id",
    )
    suspend fun updateDownloadProgressDetails(
        id: String,
        progress: Int,
        downloadedSize: Long,
        downloadSpeed: String,
        eta: String,
        status: ModelStatus,
    )

    @Query("UPDATE models SET lastUsed = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(
        id: String,
        timestamp: Long,
    )

    @Query("DELETE FROM models WHERE id = :id")
    suspend fun deleteModel(id: String)

    @Query("SELECT * FROM models WHERE status = 'READY' ORDER BY lastUsed DESC LIMIT 1")
    suspend fun getRecentlyUsedModel(): ModelEntity?
}

/** The failure detail is surfaced through the etaString column the download UI renders. */
suspend fun ModelDao.markDownloadFailed(
    id: String,
    message: String,
) = updateDownloadProgressDetails(id, 0, 0L, "", message, ModelStatus.FAILED)

suspend fun ModelDao.markDownloadReady(
    id: String,
    downloadedSize: Long,
) = updateDownloadProgressDetails(id, 100, downloadedSize, "", "", ModelStatus.READY)

suspend fun ModelDao.clearDownloadState(id: String) = updateDownloadProgressDetails(id, 0, 0L, "", "", ModelStatus.NOT_DOWNLOADED)
