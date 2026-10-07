// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: path: app/src/main/java/com/opendroid/ai/data/db/dao/MacroDao.kt
package ai.eqo.data.db.dao

import ai.eqo.data.db.entities.MacroEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros")
    fun getAllMacrosFlow(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros")
    suspend fun getAllMacros(): List<MacroEntity>

    @Query("SELECT * FROM macros WHERE id = :id LIMIT 1")
    suspend fun getMacroById(id: String): MacroEntity?

    @Query("SELECT * FROM macros WHERE name = :name LIMIT 1")
    suspend fun getMacroByName(name: String): MacroEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMacro(macro: MacroEntity)

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun deleteMacro(id: String)

    @Query("DELETE FROM macros")
    suspend fun clearAllMacros()
}
