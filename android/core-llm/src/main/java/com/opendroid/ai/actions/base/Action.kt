// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/actions/base/Action.kt
package com.opendroid.ai.actions.base

import android.content.Context

interface Action {
    val name: String

    suspend fun execute(
        params: Map<String, String>,
        context: Context,
    ): ActionResult
}
