package com.opendroid.ai.actions

import com.opendroid.ai.actions.base.Action

interface SystemActions {
    fun getActions(): List<Action>
}
