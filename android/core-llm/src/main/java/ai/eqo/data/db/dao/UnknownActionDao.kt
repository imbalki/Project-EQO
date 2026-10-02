// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/data/db/dao/UnknownActionDao.kt
package ai.eqo.data.db.dao

import ai.eqo.data.db.entities.UnknownActionEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UnknownActionDao {
    @Query("SELECT * FROM unknown_actions ORDER BY timestamp DESC")
    fun getAllUnknownActions(): Flow<List<UnknownActionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnknownAction(unknownAction: UnknownActionEntity)

    @Query("SELECT COUNT(*) FROM unknown_actions")
    suspend fun getUnknownActionCount(): Int

    @Query("DELETE FROM unknown_actions")
    suspend fun clearAll()
}
