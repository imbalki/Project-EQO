// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: path: app/src/main/java/com/opendroid/ai/data/db/OpenDroidDatabase.kt
// Origin: EQO TASK-078 (#20): only the tables the batch 2 executors need.
package ai.eqo.data.db

import ai.eqo.data.db.dao.HabitDao
import ai.eqo.data.db.dao.MacroDao
import ai.eqo.data.db.dao.NotificationDao
import ai.eqo.data.db.entities.HabitEventEntity
import ai.eqo.data.db.entities.HabitRoutineEntity
import ai.eqo.data.db.entities.MacroEntity
import ai.eqo.data.db.entities.NotificationEntity
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * EQO's local Room database. Version 1 is a fresh schema (the donor's migrations 1 to 9 covered tables EQO does not
 * ship). Add a table or column by raising [version] with an explicit migration; destructive fallback is never enabled.
 * The file lives in the app's private storage, which is excluded from backup (allowBackup=false).
 */
@Database(
    entities = [
        MacroEntity::class,
        NotificationEntity::class,
        HabitEventEntity::class,
        HabitRoutineEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class EqoDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao

    abstract fun notificationDao(): NotificationDao

    abstract fun habitDao(): HabitDao

    companion object {
        const val FILE_NAME = "eqo_database"

        @Volatile
        private var instance: EqoDatabase? = null

        fun getInstance(context: Context): EqoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, EqoDatabase::class.java, FILE_NAME)
                    .build()
                    .also { instance = it }
            }
    }
}
