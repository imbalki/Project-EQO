package ai.eqo.explain

/** Read-only screen data. Never persist or include these objects in diagnostics. */
class ExplainScreen(
    val app: String,
    val text: String,
    val labels: Int,
    val hasPassword: Boolean = false,
    val completeTree: Boolean = true,
)

interface ExplainSource {
    fun read(): ExplainScreen

    suspend fun screenshot(): String?
}

interface ExplainModel {
    suspend fun supportsImages(): Boolean

    suspend fun answer(
        screen: ExplainScreen,
        image: String?,
        question: String,
    ): String
}

/** Next phase: protected-screen setting. Android may still refuse FLAG_SECURE captures. */
fun interface ScreenProtectionPolicy {
    fun allows(screen: ExplainScreen): Boolean
}

class ExplainSession(
    private val source: ExplainSource,
    private val model: ExplainModel,
    private val allowed: () -> Boolean,
    private val protection: ScreenProtectionPolicy = ScreenProtectionPolicy { true },
    private val ready: () -> Unit = {},
) {
    private var screen: ExplainScreen? = null
    private var image: String? = null
    private var closed = false
    var textOnlyReason: String? = null
        private set
    val sourceType: String get() = if (image == null) "text" else "screenshot"

    suspend fun ask(question: String): String {
        check(!closed) { "Session closed" }
        check(allowed()) { "Screen sharing is off" }
        val context = screen ?: source.read().also { screen = it }
        check(protection.allows(context)) { "Screen unavailable" }
        if (image == null && needsImage(context, question)) {
            val vision = model.supportsImages()
            check(!closed && allowed())
            if (!vision) {
                textOnlyReason = "vision"
            } else if (context.hasPassword || !context.completeTree) {
                textOnlyReason = "password"
            } else {
                image = source.screenshot()
                if (image == null) textOnlyReason = "capture"
            }
        }
        check(!closed && allowed())
        ready()
        val answer = model.answer(context, image, question.take(MAX_QUESTION))
        check(!closed && allowed())
        return answer.take(MAX_ANSWER)
    }

    fun close() {
        closed = true
        screen = null
        image = null
        textOnlyReason = null
    }

    companion object {
        const val MAX_TEXT = 12_000
        const val MAX_NODES = 500
        const val MAX_QUESTION = 2_000
        const val MAX_ANSWER = 12_000
        private const val MIN_LABELS = 4

        fun needsImage(
            screen: ExplainScreen,
            question: String,
        ): Boolean =
            screen.labels < MIN_LABELS ||
                listOf("image", "formula", "diagram", "picture", "graph", "equation").any {
                    question.contains(it, ignoreCase = true)
                }

        fun isScreenRequest(request: String): Boolean =
            request.trim().trimEnd('?', '.', '!').lowercase() in
                setOf("what is on my screen", "what's on my screen", "explain this screen", "explain screen")
    }
}

/** Bounded collector shared by Android traversal and fake-tree privacy tests. */
class ExplainTextCollector(
    private val ownPackage: String,
) {
    private val text = StringBuilder()
    private var labels = 0
    private var visited = 0
    private var password = false
    private var complete = true

    fun markIncomplete() {
        complete = false
    }

    fun add(
        packageName: String,
        label: String?,
        isPassword: Boolean,
        visible: Boolean = true,
    ) {
        if (!hasCapacity()) return
        visited++
        if (packageName == ownPackage || !visible) return
        if (isPassword) {
            password = true
        } else if (!label.isNullOrBlank()) {
            val bounded = label.take(ExplainSession.MAX_TEXT - text.length)
            text.append(bounded)
            if (text.length < ExplainSession.MAX_TEXT) text.append('\n')
            labels++
        }
    }

    fun hasCapacity(): Boolean = visited < ExplainSession.MAX_NODES && text.length < ExplainSession.MAX_TEXT

    fun result(app: String): ExplainScreen = ExplainScreen(app, text.toString(), labels, password, complete && hasCapacity())
}
