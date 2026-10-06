// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: path: app/src/main/java/com/opendroid/ai/actions/CalendarActions.kt, RoutineActions.kt; EQO TASK-074 port.
package ai.eqo.actions.impl

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.UntrustedScreenText
import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Notes, tasks, preferences, memory, knowledge-graph and screen-memory executors. Everything that needs
 * persistence returns a typed refusal while no [ProductivityStore] adapter exists; success is never faked.
 */
internal class ProductivityMemoryActions(
    private val automation: () -> EqoAutomation?,
    private val store: ProductivityStore,
    private val extractor: ScreenMemoryExtractor?,
) {
    fun getActions(): List<Action> =
        listOf(
            RegisteredExecutor("ADD_NOTE") { params, _ -> addNote(params) },
            RegisteredExecutor("READ_NOTES") { params, _ -> readNotes(params, false) },
            RegisteredExecutor("CREATE_TASK") { params, _ -> createTask(params) },
            RegisteredExecutor("GET_MORNING_BRIEFING") { params, _ -> morningBriefing(params) },
            RegisteredExecutor("UPDATE_PREFERENCE") { params, _ -> updatePreference(params) },
            RegisteredExecutor("RECALL_MEMORY") { params, _ -> readNotes(params, true) },
            RegisteredExecutor("QUERY_KNOWLEDGE_GRAPH") { params, _ -> queryGraph(params) },
        ) + ScreenMemoryActions(automation, store, extractor).getActions()

    private suspend fun addNote(params: Map<String, String>): ActionResult {
        val title = params["title"] ?: params["name"] ?: "Quick Note"
        val content = params["content"] ?: params["text"] ?: params["body"] ?: ""
        return if (content.isBlank()) {
            ActionResult.Failure("Note content is empty.")
        } else {
            store.databaseRefusal() ?: saveNote(title, content)
        }
    }

    private suspend fun saveNote(
        title: String,
        content: String,
    ): ActionResult {
        val timestamp = System.currentTimeMillis()
        val key = "note_${safeKeyPart(title)}_$timestamp"
        store.saveSemantic(
            ProductivityMemory(key, "Title: $title\nCreated: ${formatDate(timestamp)}\n$content".trim(), timestamp),
        )
        return ActionResult.Success(mapOf("message" to "Note saved."))
    }

    private suspend fun readNotes(
        params: Map<String, String>,
        recall: Boolean,
    ): ActionResult =
        store.databaseRefusal() ?: run {
            val query = params["query"] ?: params["topic"] ?: ""
            val memories =
                store.readSemantic().filter {
                    (recall || isNote(it)) && matches(it, query)
                }
            val limit = if (recall) RECALL_LIMIT else NOTE_LIMIT
            val text =
                if (memories.isEmpty()) {
                    "No matching saved information was found."
                } else {
                    memories.sortedByDescending { it.timestamp }.take(limit).joinToString("\n\n---\n\n") { it.value }
                }
            ActionResult.Success(mapOf("message" to UntrustedScreenText.wrap(text.take(MAX_MEMORY_TEXT))))
        }

    private suspend fun createTask(params: Map<String, String>): ActionResult =
        store.databaseRefusal() ?: run {
            store.createTask(params["title"] ?: "New Task", params["description"] ?: "")
            ActionResult.Success(mapOf("message" to "Task saved."))
        }

    private suspend fun morningBriefing(params: Map<String, String>): ActionResult =
        store.databaseRefusal() ?: run {
            val text = store.morningBriefing(params["section"] ?: "full")
            ActionResult.Success(mapOf("message" to UntrustedScreenText.wrap(text.take(MAX_MEMORY_TEXT))))
        }

    private suspend fun updatePreference(params: Map<String, String>): ActionResult =
        store.databaseRefusal() ?: run {
            val requested = params["category"]?.uppercase(Locale.ROOT)
            store.updatePreference(
                params.getValue("key"),
                params.getValue("value"),
                if (requested in CATEGORIES) requested.orEmpty() else DEFAULT_CATEGORY,
            )
            ActionResult.Success(mapOf("message" to "Preference saved."))
        }

    private suspend fun queryGraph(params: Map<String, String>): ActionResult =
        store.databaseRefusal() ?: run {
            val result =
                store.queryGraph(
                    params["query"]?.trim().orEmpty(),
                    params["category"]?.uppercase(Locale.ROOT)?.trim() ?: "ALL",
                    params["tier"]?.uppercase(Locale.ROOT)?.trim() ?: "ALL",
                )
            ActionResult.Success(mapOf("message" to UntrustedScreenText.wrap(result.take(MAX_MEMORY_TEXT))))
        }
}

private const val MAX_MEMORY_TEXT = 16000
private const val MAX_TOPIC_CHARS = 500
private const val MAX_KEY_CHARS = 30
private const val RECALL_LIMIT = 5
private const val NOTE_LIMIT = 10
private const val DEFAULT_CATEGORY = "USER_PREFERENCE"
private val UNUSABLE_PREFIXES = listOf("Could not capture", "Please ensure the Accessibility")
private val CATEGORIES =
    setOf(
        "CONTACT",
        "APP_PREFERENCE",
        "TASK_ROUTINE",
        "SCHEDULE",
        "PROJECT",
        "RESOURCE",
        "NOTE_FACT",
        DEFAULT_CATEGORY,
    )

private fun safeKeyPart(text: String): String = text.replace(Regex("[^a-zA-Z0-9_]"), "_").take(MAX_KEY_CHARS)

private fun formatDate(timestamp: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return format.format(Date(timestamp))
}

private fun matches(
    memory: ProductivityMemory,
    query: String,
): Boolean = query.isBlank() || memory.key.contains(query, true) || memory.value.contains(query, true)

private fun isNote(memory: ProductivityMemory): Boolean =
    memory.key.startsWith("note_") ||
        memory.key.startsWith("screen_note_") ||
        memory.value.contains("Title:", true) ||
        memory.value.contains("Topic:", true)

/** Screen-to-memory executor. It refuses before observing the screen when no storage or extractor exists. */
private class ScreenMemoryActions(
    private val automation: () -> EqoAutomation?,
    private val store: ProductivityStore,
    private val extractor: ScreenMemoryExtractor?,
) {
    fun getActions(): List<Action> {
        val executor = RegisteredExecutor("READ_AND_REMEMBER_SCREEN") { params, _ -> rememberScreen(params) }
        return listOf(executor)
    }

    private suspend fun rememberScreen(params: Map<String, String>): ActionResult {
        val analyzer = extractor
        return store.databaseRefusal()
            ?: if (analyzer == null) {
                ActionResult.Failure("Screen information extraction is not configured; nothing was saved.")
            } else {
                observeAndRemember(params, analyzer)
            }
    }

    private suspend fun observeAndRemember(
        params: Map<String, String>,
        analyzer: ScreenMemoryExtractor,
    ): ActionResult {
        val screen = automation()?.observe() ?: A11yResult.failure(A11yError.AccessibilityDisabled)
        return if (screen is A11yResult.Success) {
            extractAndSave(params, analyzer, screen.detail)
        } else {
            screen.toActionResult()
        }
    }

    private suspend fun extractAndSave(
        params: Map<String, String>,
        analyzer: ScreenMemoryExtractor,
        screenText: String,
    ): ActionResult {
        val topic = (params["topic"] ?: params["query"] ?: "important information").take(MAX_TOPIC_CHARS)
        val fenced = UntrustedScreenText.wrap(screenText.take(MAX_MEMORY_TEXT))
        val extracted = analyzer.extract(topic, fenced).take(MAX_MEMORY_TEXT)
        if (extracted.isBlank() || UNUSABLE_PREFIXES.any { extracted.startsWith(it) }) {
            return ActionResult.Failure("Screen extraction returned no usable information; nothing was saved.")
        }
        // Do not persist after takeover during a suspended extraction.
        val gate =
            automation()?.runAction { A11yResult.success("Ready.") }
                ?: A11yResult.failure(A11yError.AccessibilityDisabled)
        return if (gate.isSuccess) saveScreenNote(topic, extracted) else gate.toActionResult()
    }

    private suspend fun saveScreenNote(
        topic: String,
        extracted: String,
    ): ActionResult {
        val timestamp = System.currentTimeMillis()
        val key = "screen_note_${safeKeyPart(topic)}_$timestamp"
        store.saveSemantic(
            ProductivityMemory(key, "Topic: $topic\nDate Recorded: ${formatDate(timestamp)}\n\n$extracted", timestamp),
        )
        return ActionResult.Success(
            mapOf(
                "message" to "Screen information saved.\n${UntrustedScreenText.wrap(extracted)}",
                "key" to key,
                "saved" to "true",
            ),
        )
    }
}
