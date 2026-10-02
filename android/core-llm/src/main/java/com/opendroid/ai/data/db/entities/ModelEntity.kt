// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/data/db/entities/ModelEntity.kt
package com.opendroid.ai.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    LOADING,
    READY,
    FAILED,
    PAUSED,
}

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val size: Long, // in bytes
    val downloadUrl: String,
    val localPath: String,
    val status: ModelStatus,
    val downloadProgress: Int, // 0..100
    val lastUsed: Long,
    val installedAt: Long,
    val downloadedSize: Long = 0L,
    val downloadSpeed: String = "",
    val etaString: String = "",
)
