// Origin: EQO TASK-073 (#20), explicit screen-analysis provider boundary replacing donor Lazy<VisionEngine>.
package ai.eqo.actions.impl

import ai.eqo.actions.base.ActionResult

/** Receives only takeover-gated, secure-window-refused, password-filtered screen text. */
fun interface ScreenAnalyzer {
    suspend fun analyze(
        question: String,
        screenText: String,
    ): ActionResult
}
