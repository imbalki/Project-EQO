// Origin: EQO TASK-078 (#20), persistence boundary for the notification, macro and routine executors.
package ai.eqo.actions.impl

import ai.eqo.core.security.AndroidProviderCredentialStore
import ai.eqo.data.db.EqoDatabase
import ai.eqo.data.db.dao.HabitDao
import ai.eqo.data.db.dao.MacroDao
import ai.eqo.data.db.dao.NotificationDao
import ai.eqo.data.models.AutoReplyConfig
import ai.eqo.data.repository.SettingsRepository
import android.content.Context
import kotlinx.coroutines.flow.first

/** The Room DAOs behind the batch 2 executors. The database is opened on first use, never at registry construction. */
internal interface AutomationDaos {
    val notifications: NotificationDao
    val macros: MacroDao
    val habits: HabitDao
}

internal class RoomAutomationDaos(
    private val database: Lazy<EqoDatabase>,
) : AutomationDaos {
    override val notifications: NotificationDao get() = database.value.notificationDao()
    override val macros: MacroDao get() = database.value.macroDao()
    override val habits: HabitDao get() = database.value.habitDao()

    companion object {
        fun forContext(context: Context) = RoomAutomationDaos(lazy { EqoDatabase.getInstance(context) })
    }
}

/** Reads and writes the auto-reply preferences; the registry never touches DataStore directly. */
internal interface AutoReplyConfigStore {
    suspend fun current(): AutoReplyConfig

    suspend fun update(config: AutoReplyConfig)
}

/** Backed by the app's one DataStore file; the repository is built only when auto-reply is first requested. */
internal class SettingsAutoReplyConfigStore(
    context: Context,
) : AutoReplyConfigStore {
    private val appContext = context.applicationContext
    private val settings by lazy { SettingsRepository(appContext, AndroidProviderCredentialStore(appContext)) }

    override suspend fun current(): AutoReplyConfig = settings.autoReplyConfig.first()

    override suspend fun update(config: AutoReplyConfig) = settings.updateAutoReplyConfig(config)
}
