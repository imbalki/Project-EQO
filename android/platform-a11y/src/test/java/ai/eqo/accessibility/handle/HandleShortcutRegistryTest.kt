// Origin: EQO edge-handle task; fake store/features, no Android windows.
package ai.eqo.accessibility.handle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandleShortcutRegistryTest {
    private class Store : HandleChoiceStore {
        var choice = HandleChoice()

        override fun load(): HandleChoice = choice

        override fun save(choice: HandleChoice) {
            this.choice = choice
        }
    }

    private class Shortcut(
        override val id: String,
        override val defaultEnabled: Boolean = false,
        var available: Boolean = true,
    ) : HandleShortcut<Unit> {
        override val labelRes = 1
        override val iconRes = 2

        override fun unavailableReason(context: Unit): Int? = if (available) null else 3

        override fun run(context: Unit) = Unit
    }

    @Test
    fun `new shortcuts are off unless marked default and reset restores defaults`() {
        val registry = HandleShortcutRegistry<Unit>(Store())
        registry.register(Shortcut("ask", true))
        registry.register(Shortcut("later"))
        assertTrue(registry.enabled("ask"))
        assertFalse(registry.enabled("later"))
        registry.setEnabled("ask", false)
        registry.setEnabled("later", true)
        registry.reset()
        assertEquals(listOf("ask"), registry.visible(Unit).map { it.id })
    }

    @Test
    fun `order and disabled defaults survive a new registry and app upgrade`() {
        val store = Store()
        val first = HandleShortcutRegistry<Unit>(store)
        first.register(Shortcut("ask", true))
        first.register(Shortcut("open", true))
        first.setEnabled("ask", false)
        first.move("open", -1)
        val upgraded = HandleShortcutRegistry<Unit>(store)
        upgraded.register(Shortcut("ask", true))
        upgraded.register(Shortcut("open", true))
        upgraded.register(Shortcut("explain"))
        upgraded.register(Shortcut("new_default", true))
        assertEquals(listOf("open", "ask", "explain", "new_default"), upgraded.ordered().map { it.id })
        assertFalse(upgraded.enabled("ask"))
        assertFalse(upgraded.enabled("explain"))
        assertTrue(upgraded.enabled("new_default"))
    }

    @Test
    fun `unavailable features are hidden without losing the owners choice`() {
        val registry = HandleShortcutRegistry<Unit>(Store())
        val pause = Shortcut("pause", true, false)
        registry.register(pause)
        assertTrue(registry.visible(Unit).isEmpty())
        assertTrue(registry.enabled("pause"))
        pause.available = true
        assertEquals(listOf(pause), registry.visible(Unit))
    }

    @Test
    fun `remove hides an entry and reordering skips removed entries`() {
        val registry = HandleShortcutRegistry<Unit>(Store())
        listOf("ask", "pause", "open").forEach { registry.register(Shortcut(it, true)) }
        registry.ordered()
        registry.remove("pause")
        assertFalse(registry.enabled("pause"))
        registry.move("open", -1)
        assertEquals(listOf("open", "ask"), registry.ordered().map { it.id })
        registry.register(Shortcut("pause", true))
        assertTrue(registry.enabled("pause"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate ids are rejected`() {
        val registry = HandleShortcutRegistry<Unit>(Store())
        registry.register(Shortcut("ask"))
        registry.register(Shortcut("ask"))
    }
}
