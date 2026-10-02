/*
 * ADAPTER (TASK-004 Phase 2) - minimal interface replacing the quarantined
 * upstream class com.opendroid.ai.actions.AdvancedControlActions (the upstream actions package, not in Phase
 * One scope). Declares exactly the members the moved code calls. The upstream
 * name and package are kept so the moved ActionDispatcher.kt stays
 * byte-identical.
 */
package ai.eqo.actions

import ai.eqo.actions.base.Action

interface AdvancedControlActions {
    fun getActions(): List<Action>
}
