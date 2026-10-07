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
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

class StudyHelperHooks(
    private val binderWaitMs: Long = BINDER_WAIT_MS,
) : HelperHooks {
    /** The starter already ran over the pinned connection; nothing else to do before waiting. */
    override fun startHelper() = Unit

    override fun authorizeHelper() {
        waitForBinder()
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw StepSignalException(
                StepSignal.HELPER_NOT_AUTHORIZED,
                "the helper is running but has not authorized EQO",
            )
        }
    }

    override fun checkBinder() {
        if (!Shizuku.pingBinder()) {
            throw StepSignalException(StepSignal.BINDER_DEAD, "the helper binder does not answer")
        }
    }

    private fun waitForBinder() {
        val deadline = System.currentTimeMillis() + binderWaitMs
        while (!Shizuku.pingBinder()) {
            if (System.currentTimeMillis() >= deadline) {
                throw StepSignalException(StepSignal.HELPER_NOT_STARTED, "the helper did not report in time")
            }
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val BINDER_WAIT_MS = 15_000L
        const val POLL_MS = 250L
    }
}
