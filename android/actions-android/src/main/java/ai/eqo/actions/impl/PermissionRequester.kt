// Origin: EQO TASK-069 (#20), explicit just-in-time permission boundary replacing donor permission checks.
package ai.eqo.actions.impl

/** A requested grant is not an approval to send, buy, delete or otherwise act. */
sealed interface ActionPermission {
    data class Runtime(
        val name: String,
        val explanation: String,
    ) : ActionPermission

    /** Later groups can supply the exact settings intent, and a grant recheck. */
    data class SpecialAccess(
        val settingsAction: String,
        val packageScoped: Boolean,
        val explanation: String,
        val isGranted: () -> Boolean,
    ) : ActionPermission
}

fun interface PermissionRequester {
    /** Suspends until Android responds or the owner returns from the exact settings screen. */
    suspend fun request(permission: ActionPermission): Boolean
}

/** Unknown-action telemetry intentionally receives no parameter values or secrets. */
fun interface UnknownActionSink {
    fun record(actionName: String)
}
