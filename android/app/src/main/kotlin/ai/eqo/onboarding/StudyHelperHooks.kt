/*
 * EQO (TASK-080, issue #20): the helper side of the wireless activation sequence.
 *
 * The wireless connection only runs the validated helper start command (see
 * `HelperStartCommand`). Whether the helper then really is up, authorized and alive is
 * answered by the helper binder itself, never inferred from the ADB checks.
 */
package ai.eqo.onboarding

import ai.eqo.adb.pairing.HelperHooks
import ai.eqo.adb.pairing.StepSignal
import ai.eqo.adb.pairing.StepSignalException
import rikka.shizuku.Shizuku

internal class StudyHelperHooks(
    private val permissionPort: HelperPermissionPort,
    private val binderWaitMs: Long = BINDER_WAIT_MS,
) : HelperHooks {
    /** The starter already ran over the pinned connection; nothing else to do before waiting. */
    override fun startHelper() = Unit

    override fun authorizeHelper() {
        waitForBinder()
        HelperAuthorization(permissionPort).await()
    }

    override fun checkBinder() {
        if (!Shizuku.pingBinder()) {
            throw StepSignalException(StepSignal.BINDER_DEAD, "the helper binder does not answer")
        }
        if (!permissionPort.isGranted()) {
            throw StepSignalException(
                StepSignal.HELPER_NOT_AUTHORIZED,
                "helper permission was revoked before health check",
            )
        }
    }

    private fun waitForBinder() {
        val started = System.nanoTime()
        while (!Shizuku.isBinderReady()) {
            if ((System.nanoTime() - started) / NANOS_PER_MS >= binderWaitMs) {
                throw StepSignalException(StepSignal.HELPER_NOT_STARTED, "the helper did not report in time")
            }
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val BINDER_WAIT_MS = 15_000L
        const val POLL_MS = 250L
        const val NANOS_PER_MS = 1_000_000L
    }
}
