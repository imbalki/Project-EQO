/*
 * EQO (TASK-012, issue #17): the raw, ungated operations of the one
 * accessibility service. Nothing outside GatedServiceActions may call these.
 */
package ai.eqo.accessibility

/**
 * The raw action surface of the one EQO accessibility service, BEFORE the
 * takeover gate.
 *
 * TASK-012 (SF-1): the ONLY legitimate caller of this interface is
 * [GatedServiceActions], which runs every call through
 * [EqoAutomation.runAction] (the takeover-gated path). Automators
 * (GenericAppAutomator, WhatsApp/Telegram/Sms) talk to [GatedServiceActions],
 * never to this — `AutomatorsUseGatedRouteTest` enforces it.
 */
interface ServiceActionOps {
    /** IME enter/search/go on the focused editable field. */
    fun performImeEnter(): Boolean

    /** GLOBAL_ACTION_BACK. */
    fun performGlobalBack(): Boolean

    /** GLOBAL_ACTION_HOME. */
    fun performGlobalHome(): Boolean

    fun performScroll(forward: Boolean): Boolean

    fun clickCoordinates(
        x: Float,
        y: Float,
    ): Boolean

    fun findAndClick(text: String): Boolean

    fun findAndClickById(viewId: String): Boolean

    fun findAndType(
        searchText: String,
        content: String,
    ): Boolean

    fun findAndTypeById(
        viewId: String,
        content: String,
    ): Boolean
}
