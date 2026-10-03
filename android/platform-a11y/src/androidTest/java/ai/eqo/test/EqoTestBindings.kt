/*
 * EQO (TASK-009): androidTest-side Hilt bindings, authorized by the lead to
 * live INSIDE :platform-a11y scope only (no :app changes, no new module).
 * Binds the leaf interfaces of EQOAccessibilityService's graph to the
 * inert fakes in EqoTestFakes.kt; every other dependency is @Inject-constructed.
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
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.ActionSequenceExecutor
import ai.eqo.core.memory.MemoryStore
import ai.eqo.core.routine.HabitRoutineTracker
import ai.eqo.core.security.ProviderCredentialStore
import ai.eqo.core.service.ServiceBridge
import ai.eqo.data.db.dao.ModelDao
import ai.eqo.data.db.dao.UnknownActionDao
import ai.eqo.data.repository.ChatHistoryStore
import ai.eqo.data.repository.ModelStore
import ai.eqo.data.repository.PlanStore
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EqoTestBindings {
    @Provides
    @Singleton
    fun provideMemoryStore(): MemoryStore = FakeMemoryStore()

    @Provides
    @Singleton
    fun provideChatHistoryStore(): ChatHistoryStore = FakeChatHistoryStore()

    @Provides
    @Singleton
    fun providePlanStore(): PlanStore = FakePlanStore()

    @Provides
    @Singleton
    fun provideServiceBridge(): ServiceBridge = FakeServiceBridge()

    @Provides
    @Singleton
    fun provideHabitRoutineTracker(): HabitRoutineTracker = FakeHabitRoutineTracker()

    @Provides
    @Singleton
    fun provideNotificationTapTarget(): NotificationTapTarget = FakeNotificationTapTarget()

    @Provides
    @Singleton
    fun provideActionAutoMapper(): ActionAutoMapper = FakeActionAutoMapper()

    @Provides
    @Singleton
    fun provideUnknownActionDao(): UnknownActionDao = FakeUnknownActionDao()

    @Provides
    @Singleton
    fun provideModelDao(): ModelDao = FakeModelDao()

    @Provides
    @Singleton
    fun provideModelStore(): ModelStore = FakeModelStore()

    @Provides
    @Singleton
    fun provideProviderCredentialStore(): ProviderCredentialStore = FakeProviderCredentialStore()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    // Plain android.content.Context: several @Inject constructors in core-llm
    // request the unqualified type, which only the production app graph binds.
    @Provides
    @Singleton
    fun provideContext(
        @ApplicationContext context: Context,
    ): Context = context

    @Provides
    @Singleton
    fun provideActionSequenceExecutor(): ActionSequenceExecutor =
        ActionSequenceExecutor(
            executeAction = { _, _, _ ->
                ActionResult.Failure("TASK-009 test stub: no actions registered in the test graph")
            },
            hasAction = { false },
        )

    // The 14 action-set interfaces consumed by ActionDispatcher: one no-op set.
    @Provides
    @Singleton
    fun provideSystemActions(): SystemActions = NoOpActionSet()

    @Provides
    @Singleton
    fun provideCommunicationActions(): CommunicationActions = NoOpActionSet()

    @Provides
    @Singleton
    fun provideCalendarActions(): CalendarActions = NoOpActionSet()

    @Provides
    @Singleton
    fun provideTransportActions(): TransportActions = NoOpActionSet()

    @Provides
    @Singleton
    fun provideInformationActions(): InformationActions = NoOpActionSet()

    @Provides
    @Singleton
    fun provideMediaActions(): MediaActions = NoOpActionSet()

    @Provides
    @Singleton
    fun provideFoodShoppingActions(): FoodShoppingActions = NoOpActionSet()

    @Provides
    @Singleton
    fun provideSmartHomeActions(): SmartHomeActions = NoOpActionSet()

    @Provides
    @Singleton
    fun provideFinanceActions(): FinanceActions = NoOpActionSet()

    @Provides
    @Singleton
    fun provideMacroActions(): MacroActions = NoOpActionSet()

    @Provides
    @Singleton
    fun provideAdvancedControlActions(): AdvancedControlActions = NoOpActionSet()

    @Provides
    @Singleton
    fun provideNotificationActions(): NotificationActions = NoOpActionSet()

    @Provides
    @Singleton
    fun provideRoutineActions(): RoutineActions = NoOpActionSet()

    @Provides
    @Singleton
    fun provideSocialActions(): SocialActions = NoOpActionSet()
}
