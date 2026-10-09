// Origin: EQO edge-handle task; internal feature registration and persisted user choice.
package ai.eqo.accessibility.handle

interface HandleShortcut<C> {
    val id: String
    val labelRes: Int
    val iconRes: Int
    val defaultEnabled: Boolean get() = false

    /** Null means available; otherwise a resource explaining why the shortcut cannot run. */
    fun unavailableReason(context: C): Int? = null

    fun run(context: C)
}

data class HandleChoice(
    val order: List<String> = emptyList(),
    val known: Set<String> = emptySet(),
    val enabled: Set<String> = emptySet(),
)

interface HandleChoiceStore {
    fun load(): HandleChoice

    fun save(choice: HandleChoice)
}

/** Features register with one line. Unknown/new non-default entries never silently turn on. */
class HandleShortcutRegistry<C>(
    private val store: HandleChoiceStore,
) {
    private val shortcuts = linkedMapOf<String, HandleShortcut<C>>()

    fun register(shortcut: HandleShortcut<C>) {
        require(shortcut.id.matches(Regex("[a-z][a-z0-9_]*")))
        require(shortcut.id !in shortcuts) { "Duplicate shortcut: ${shortcut.id}" }
        shortcuts[shortcut.id] = shortcut
    }

    fun remove(id: String) {
        shortcuts.remove(id)
    }

    fun ordered(): List<HandleShortcut<C>> {
        val choice = choices()
        return choice.order.mapNotNull(shortcuts::get)
    }

    fun enabled(id: String): Boolean = id in shortcuts && id in choices().enabled

    fun visible(context: C): List<HandleShortcut<C>> =
        ordered()
            .filter { enabled(it.id) && it.unavailableReason(context) == null }

    fun setEnabled(
        id: String,
        enabled: Boolean,
    ) {
        require(id in shortcuts)
        val choice = choices()
        store.save(choice.copy(enabled = if (enabled) choice.enabled + id else choice.enabled - id))
    }

    fun move(
        id: String,
        offset: Int,
    ) {
        val choice = choices()
        val order = choice.order.filter { it in shortcuts }.toMutableList()
        val from = order.indexOf(id)
        require(from >= 0)
        val to = (from + offset).coerceIn(0, order.lastIndex)
        order.add(to, order.removeAt(from))
        store.save(choice.copy(order = order + choice.order.filter { it !in shortcuts }))
    }

    fun reset() {
        store.save(
            HandleChoice(
                shortcuts.keys.toList(),
                shortcuts.keys.toSet(),
                shortcuts.values
                    .filter { it.defaultEnabled }
                    .map { it.id }
                    .toSet(),
            ),
        )
    }

    private fun choices(): HandleChoice {
        val saved = store.load()
        val newIds = shortcuts.keys - saved.known
        val choice =
            saved.copy(
                order = saved.order.distinct() + (shortcuts.keys - saved.order.toSet()),
                known = saved.known + shortcuts.keys,
                enabled = saved.enabled + newIds.filter { shortcuts.getValue(it).defaultEnabled },
            )
        if (choice != saved) store.save(choice)
        return choice
    }
}
