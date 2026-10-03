package ai.eqo.accessibility

/**
 * Fake node-tree fixture for the TASK-009 unit tests: an in-memory [A11yNode]
 * tree that records every action performed on it, so observe/tap/scroll/type can
 * be asserted without any Android runtime or bound accessibility service.
 */
@Suppress("LongParameterList") // test fixture: named defaults, one builder
class FakeNode(
    override val text: CharSequence? = null,
    override val contentDescription: CharSequence? = null,
    override val viewIdResourceName: String? = null,
    override val isClickable: Boolean = false,
    override val isEditable: Boolean = false,
    override val isScrollable: Boolean = false,
    override val isPassword: Boolean = false,
) : A11yNode {
    override val className: CharSequence = "android.widget.FrameLayout"

    private val children = mutableListOf<A11yNode>()

    private var parentRef: FakeNode? = null

    /** Actions the platform would reject (performAction returns false). */
    var rejectActions: Boolean = false

    var clickCount: Int = 0
        private set

    var typedValue: String? = null
        private set

    var scrollCount: Int = 0
        private set

    override val childCount: Int get() = children.size

    override val parent: A11yNode? get() = parentRef

    /** Adds [node] as the last child and returns this node (chainable). */
    fun child(node: A11yNode): FakeNode {
        children.add(node)
        if (node is FakeNode) {
            node.parentRef = this
        }
        return this
    }

    override fun childAt(index: Int): A11yNode? = children.getOrNull(index)

    override fun click(): Boolean {
        if (rejectActions) return false
        clickCount++
        return true
    }

    override fun setText(value: CharSequence): Boolean {
        if (rejectActions) return false
        typedValue = value.toString()
        return true
    }

    override fun scroll(forward: Boolean): Boolean {
        if (rejectActions) return false
        scrollCount++
        return true
    }
}
