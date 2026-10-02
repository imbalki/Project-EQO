package ai.eqo.actions

import ai.eqo.actions.base.Action

interface SystemActions {
    fun getActions(): List<Action>
}
