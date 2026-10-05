// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51, path: app/src/main/java/com/opendroid/ai/actions/CallFlowExecutor.kt
package ai.eqo.actions.impl

import ai.eqo.actions.base.ActionResult
import ai.eqo.core.util.DeviceCapabilities
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** Executes a phone call without treating intent launch as proof that the call started. */
internal class CallFlowExecutor constructor(
    private val callFlowVerifier: CallFlowVerifier,
    private val launcher: GatedIntentLauncher,
) {
    suspend fun execute(
        phone: String,
        context: Context,
    ): ActionResult {
        requireRegistryExecution()?.let { return it }
        val cleanPhone = phone.replace(Regex("[\\s\\-()]"), "").trim()
        if (cleanPhone.isEmpty()) {
            return ActionResult.Failure("The requested phone number is empty.")
        }

        val callUri = "tel:$cleanPhone".toUri()
        val hasTelephony = DeviceCapabilities.canMakeCalls(context)
        val dialIntent = Intent(Intent.ACTION_DIAL, callUri).withNewTask()

        if (!hasTelephony && dialIntent.resolveActivity(context.packageManager) == null) {
            return ActionResult.Failure(
                "This device cannot place phone calls because no calling hardware or dialer is available.",
            )
        }

        return try {
            if (hasTelephony && canVerifyDirectCall(context)) {
                val wasAlreadyInProgress = callFlowVerifier.isCallInProgress(context)
                launcher.open(Intent(Intent.ACTION_CALL, callUri).withNewTask())

                if (callFlowVerifier.awaitNewCallStarted(context, wasAlreadyInProgress)) {
                    ActionResult.Success(mapOf("message" to "Call started."))
                } else {
                    ActionResult.Failure("The call could not be verified as started. No call was reported as active.")
                }
            } else {
                launcher.open(dialIntent)
                pendingDialerResult()
            }
        } catch (_: SecurityException) {
            openDialerAfterDirectCallFailure(dialIntent)
        } catch (_: Exception) {
            ActionResult.Failure("The call could not be started.")
        }
    }

    private fun openDialerAfterDirectCallFailure(dialIntent: Intent): ActionResult =
        try {
            launcher.open(dialIntent)
            pendingDialerResult()
        } catch (_: Exception) {
            ActionResult.Failure("The call could not be started.")
        }

    private fun pendingDialerResult(): ActionResult.PendingUserAction =
        ActionResult.PendingUserAction(
            message = "The dialer is open. Tap Call to place the call.",
            metadata = mapOf("action" to "MAKE_CALL", "requiresUserAction" to "true"),
        )

    /**
     * ACTION_CALL alone is not enough: without READ_PHONE_STATE the verifier can never
     * observe the call, so a call that really was placed would be reported as failed.
     * Users who upgrade keep CALL_PHONE but start without READ_PHONE_STATE, so both
     * permissions must be granted before taking the verified direct-call path.
     */
    private fun canVerifyDirectCall(context: Context): Boolean =
        isGranted(context, Manifest.permission.CALL_PHONE) &&
            isGranted(context, Manifest.permission.READ_PHONE_STATE)

    private fun isGranted(
        context: Context,
        permission: String,
    ): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun Intent.withNewTask(): Intent =
        apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
