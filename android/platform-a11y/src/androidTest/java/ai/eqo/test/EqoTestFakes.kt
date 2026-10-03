/*
 * EQO (TASK-009): androidTest-side stub bindings, authorized by the lead to
 * live INSIDE :platform-a11y scope only. These fakes satisfy the leaf
 * interfaces of the service's Hilt graph so the instrumentation/test APK can
 * host the single EQO accessibility service on a real device. They are inert:
 * no network, no persistence, no side effects beyond in-memory no-ops.
 */
package ai.eqo.test

import ai.eqo.NotificationTapTarget
import ai.eqo.actions.ActionAutoMapper
import ai.eqo.actions.AdvancedControlActions
import ai.eqo.actions.CalendarActions
import ai.eqo.actions.CommunicationActions
import ai.eqo.actions.FinanceActions
import ai.eqo.actions.FoodShoppingActions
import ai.eqo.actions.InformationActions
import ai.eqo.actions.MacroActions
import ai.eqo.actions.MediaActions
import ai.eqo.actions.NotificationActions
import ai.eqo.actions.RoutineActions
import ai.eqo.actions.SmartHomeActions
import ai.eqo.actions.SocialActions
import ai.eqo.actions.SystemActions
import ai.eqo.actions.TransportActions
import ai.eqo.actions.base.Action
import ai.eqo.core.agent.Contact
import ai.eqo.core.memory.MemoryStore
import ai.eqo.core.routine.HabitRoutineTracker
import ai.eqo.core.security.CredentialStoreResult
import ai.eqo.core.security.ProviderCredentialId
import ai.eqo.core.security.ProviderCredentialRecoveryState
import ai.eqo.core.security.ProviderCredentialStore
import ai.eqo.core.service.ServiceBridge
import ai.eqo.data.db.dao.ModelDao
import ai.eqo.data.db.dao.UnknownActionDao
import ai.eqo.data.db.entities.ModelEntity
import ai.eqo.data.db.entities.ModelStatus
import ai.eqo.data.db.entities.UnknownActionEntity
import ai.eqo.data.models.ChatMessage
import ai.eqo.data.models.Plan
import ai.eqo.data.repository.ChatHistoryStore
import ai.eqo.data.repository.ModelStore
import ai.eqo.data.repository.PlanStore
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** Inert [MemoryStore]: records nothing, recalls nothing. */
class FakeMemoryStore : MemoryStore {
    override suspend fun storeMessage(
        message: ChatMessage,
        sessionId: String,
    ) = Unit

    override suspend fun getRelevantContext(currentGoal: String): String = ""

    override suspend fun logTaskExecution(
        stepId: String,
        planId: String,
        description: String,
        actionType: String,
        params: Map<String, String>,
        success: Boolean,
        resultData: String?,
        errorMessage: String?,
    ) = Unit

    override suspend fun storeContactPreference(
        query: String,
        contact: Contact,
    ) = Unit

    override suspend fun recallContactPreference(query: String): Contact? = null
}

/** Inert [ChatHistoryStore]: an empty in-memory history. */
class FakeChatHistoryStore : ChatHistoryStore {
    override fun getMessages(sessionId: String): Flow<List<ChatMessage>> = flowOf(emptyList())

    override suspend fun insertMessage(
        sessionId: String,
        message: ChatMessage,
    ) = Unit

    override suspend fun getLastMessages(
        sessionId: String,
        limit: Int,
    ): List<ChatMessage> = emptyList()

    override suspend fun ensureCurrentSessionId(): String = "eqo-test-session"
}

/** Inert [PlanStore]: stores nothing. */
class FakePlanStore : PlanStore {
    override suspend fun getPlanById(planId: String): Plan? = null

    override suspend fun savePlan(plan: Plan) = Unit
}

/** Inert [ServiceBridge]: never starts a recording service. */
class FakeServiceBridge : ServiceBridge {
    override fun triggerRecord(context: Context) = Unit
}

/** Inert [HabitRoutineTracker]: records nothing. */
class FakeHabitRoutineTracker : HabitRoutineTracker {
    override fun recordAppOpen(
        packageName: String,
        appName: String?,
        metadata: Map<String, String>,
    ) = Unit
}

/** [NotificationTapTarget] that opens the androidTest target activity. */
class FakeNotificationTapTarget : NotificationTapTarget {
    override fun launchIntent(context: Context): Intent =
        Intent(context, EqoTestTargetActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Inert [ActionAutoMapper]: identity mapping, no auto-mapping. */
class FakeActionAutoMapper : ActionAutoMapper {
    override fun normalizeActionName(raw: String): String = raw

    override fun mapAction(
        action: String,
        params: Map<String, String>,
        registeredActions: Set<String>,
    ): ActionAutoMapper.MappingResult =
        ActionAutoMapper.MappingResult(
            originalAction = action,
            mappedAction = null,
            wasMapped = false,
            mappedParams = params,
        )
}

/** Inert [UnknownActionDao] (Room DAO): stores nothing. */
class FakeUnknownActionDao : UnknownActionDao {
    override fun getAllUnknownActions(): Flow<List<UnknownActionEntity>> = flowOf(emptyList())

    override suspend fun insertUnknownAction(unknownAction: UnknownActionEntity) = Unit

    override suspend fun getUnknownActionCount(): Int = 0

    override suspend fun clearAll() = Unit
}

/** Inert [ModelDao] (Room DAO): no model rows in the test graph. */
class FakeModelDao : ModelDao {
    override fun getAllModels(): Flow<List<ModelEntity>> = flowOf(emptyList())

    override suspend fun getModelById(id: String): ModelEntity? = null

    override fun getModelByIdFlow(id: String): Flow<ModelEntity?> = flowOf(null)

    override suspend fun insertModel(model: ModelEntity) = Unit

    override suspend fun updateModelStatus(
        id: String,
        status: ModelStatus,
    ) = Unit

    override suspend fun updateModelProgress(
        id: String,
        progress: Int,
        status: ModelStatus,
    ) = Unit

    override suspend fun updateDownloadProgressDetails(
        id: String,
        progress: Int,
        downloadedSize: Long,
        downloadSpeed: String,
        eta: String,
        status: ModelStatus,
    ) = Unit

    override suspend fun updateLastUsed(
        id: String,
        timestamp: Long,
    ) = Unit

    override suspend fun deleteModel(id: String) = Unit

    override suspend fun getRecentlyUsedModel(): ModelEntity? = null
}

/** Inert [ModelStore]: no on-device models in the test graph. */
class FakeModelStore : ModelStore {
    override val allModelsFlow: Flow<List<ModelEntity>> = flowOf(emptyList())

    override fun resolveLiteRTSpec(modelId: String): ai.eqo.core.llm.OnDeviceModelSpec? = null
}

/** Inert [ProviderCredentialStore]: no credentials in the test graph. */
class FakeProviderCredentialStore : ProviderCredentialStore {
    override val recoveryState: StateFlow<ProviderCredentialRecoveryState> =
        MutableStateFlow(ProviderCredentialRecoveryState.Ready)

    override fun read(credential: ProviderCredentialId): CredentialStoreResult<String?> = CredentialStoreResult.Success(null)

    override fun readProviderApiKeys(): CredentialStoreResult<Map<String, String>> = CredentialStoreResult.Success(emptyMap())

    override fun write(
        credential: ProviderCredentialId,
        value: String,
    ): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)

    override fun remove(credential: ProviderCredentialId): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)

    override fun migrateLegacyCredentials(): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)

    override fun resetForReentry(): CredentialStoreResult<Unit> = CredentialStoreResult.Success(Unit)
}

/**
 * One no-op implementation for the 14 action-set interfaces [ai.eqo.actions.ActionDispatcher]
 * consumes. The test graph registers zero actions; device records drive
 * [ai.eqo.accessibility.EqoAutomation] directly.
 */
class NoOpActionSet :
    SystemActions,
    CommunicationActions,
    CalendarActions,
    TransportActions,
    InformationActions,
    MediaActions,
    FoodShoppingActions,
    SmartHomeActions,
    FinanceActions,
    MacroActions,
    AdvancedControlActions,
    NotificationActions,
    RoutineActions,
    SocialActions {
    override fun getActions(): List<Action> = emptyList()
}
