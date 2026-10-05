// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/actions/CalendarActions.kt lines 582-602
package ai.eqo.actions.impl

import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.security.SensitiveMemoryStore
import android.content.Context

/** Donor engine/Room graph replaced by the existing EQO AndroidKeyStore AES-GCM store. */
internal class SaveSensitiveInfoAction(
    private val store: SensitiveMemoryStore,
) : Action {
    override val name = "SAVE_SENSITIVE_INFO"

    override suspend fun execute(
        params: Map<String, String>,
        context: Context,
    ): ActionResult {
        requireRegistryExecution()?.let { return it }
        val key = params["key"] ?: return ActionResult.Failure("Sensitive key is required.")
        val secret = params["secret"] ?: return ActionResult.Failure("Sensitive secret value is required.")
        return try {
            if (store.write(key, secret)) {
                ActionResult.Success(mapOf("message" to "Sensitive information saved in encrypted storage."))
            } else {
                ActionResult.Failure("Failed to write to encrypted Keystore storage.")
            }
        } catch (_: Exception) {
            ActionResult.Failure("Couldn't encrypt and store sensitive data.")
        }
    }
}
