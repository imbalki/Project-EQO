// Origin: EQO TASK-074 (#20), explicit ports for donor persistence and screen extraction.
package ai.eqo.actions.impl

import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import android.content.Context

/** Registry-only executor wrapper: direct calls outside the registry's coroutine permit are refused. */
internal class RegisteredExecutor(
    override val name: String,
    private val handler: suspend (Map<String, String>, Context) -> ActionResult,
) : Action {
    override suspend fun execute(
        params: Map<String, String>,
        context: Context,
    ): ActionResult = requireRegistryExecution() ?: handler(params, context)
}

/** No database is constructed by the executor module. Batch 2 can implement this boundary. */
interface ProductivityStore {
    val availability: ProductivityAvailability

    suspend fun saveSemantic(memory: ProductivityMemory)

    suspend fun readSemantic(): List<ProductivityMemory>

    suspend fun queryGraph(
        query: String,
        category: String,
        tier: String,
    ): String

    suspend fun updatePreference(
        key: String,
        value: String,
        category: String,
    )

    suspend fun createTask(
        title: String,
        description: String,
    )

    suspend fun morningBriefing(section: String): String
}

sealed interface ProductivityAvailability {
    data object Ready : ProductivityAvailability

    data object NeedsDatabase : ProductivityAvailability
}

data class ProductivityMemory(
    val key: String,
    val value: String,
    val timestamp: Long,
)

/** Receives only bounded, fenced screen data; must never execute instructions/tools in it. */
fun interface ScreenMemoryExtractor {
    suspend fun extract(
        topic: String,
        untrustedScreen: String,
    ): String
}

internal object UnavailableProductivityStore : ProductivityStore {
    override val availability = ProductivityAvailability.NeedsDatabase

    override suspend fun saveSemantic(memory: ProductivityMemory): Unit = unavailable()

    override suspend fun readSemantic(): List<ProductivityMemory> = unavailable()

    override suspend fun queryGraph(
        query: String,
        category: String,
        tier: String,
    ): String = unavailable()

    override suspend fun updatePreference(
        key: String,
        value: String,
        category: String,
    ): Unit = unavailable()

    override suspend fun createTask(
        title: String,
        description: String,
    ): Unit = unavailable()

    override suspend fun morningBriefing(section: String): String = unavailable()

    private fun unavailable(): Nothing = error("Needs the database (batch 2).")
}

internal fun ProductivityStore.databaseRefusal(): ActionResult.Failure? =
    if (availability == ProductivityAvailability.NeedsDatabase) {
        ActionResult.Failure(
            "This action needs the database (batch 2); no information was read or saved.",
            "needs_database_batch_2",
        )
    } else {
        null
    }
