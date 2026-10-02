/*
 * ADAPTER (TASK-004 Phase 2) - minimal interface replacing the quarantined
 * upstream class com.opendroid.ai.actions.MacroActions (the upstream actions package, not in Phase
 * One scope). Declares exactly the members the moved code calls. The upstream
 * name and package are kept so the moved ActionDispatcher.kt stays
 * byte-identical.
 */
package com.opendroid.ai.actions

import com.opendroid.ai.actions.base.Action

interface MacroActions {
    fun getActions(): List<Action>
}
