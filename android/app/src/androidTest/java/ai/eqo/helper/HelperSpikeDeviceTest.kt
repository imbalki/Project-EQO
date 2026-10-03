package ai.eqo.helper

import ai.eqo.helper.client.HelperActivationState
import android.util.Log
import moe.shizuku.server.IShizukuService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rikka.shizuku.Shizuku

/**
 * EQO-authored (TASK-007, issue #12): device spike harness for the EQO privileged helper.
 *
 * Runs inside the real EQO app (applicationId ai.eqo.app) via the standard
 * AndroidJUnitRunner, so "EQO receives the binder" is tested from EQO's own process.
 *
 * Host-side sequencing (see android/Phase-One/evidence/task-007-helper-spike.md):
 *   - activationAndShellUid:      host activates the helper, then this method asserts binder + shell uid
 *   - survivesAppRestart:         a FRESH app process (new instrumentation run) gets the binder again
 *                                 without any new activation
 *   - binderDeathAndReactivation: host kills the server mid-run, then re-activates it
 *   - revocationExitsCleanly:     this method revokes activation (manager exit()), server must die cleanly
 */
class HelperSpikeDeviceTest {
    companion object {
        private const val TAG = "EqoHelperSpike"
        private const val SHELL_UID = 2000
    }

    private fun awaitBinder(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!Shizuku.pingBinder() && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
        }
        return Shizuku.pingBinder()
    }

    private fun awaitState(
        state: HelperActivationState,
        expected: HelperActivationState.State,
        timeoutMs: Long,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (state.state != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
        }
        return state.state == expected
    }

    @Test
    fun activationAndShellUid() {
        Log.i(TAG, "SPIKE A start: waiting for helper binder (host activates the helper in parallel)")
        assertTrue("helper binder not received within 120s of activation", awaitBinder(120_000))
        val uid = Shizuku.getUid()
        Log.i(TAG, "SPIKE A binder received; getUid() returned $uid (expected $SHELL_UID)")
        assertEquals("privileged test call must return the shell uid", SHELL_UID, uid)
    }

    @Test
    fun survivesAppRestart() {
        Log.i(TAG, "SPIKE B start: fresh EQO process, no re-activation; server must still be up")
        assertTrue("helper binder not re-delivered to the restarted app", awaitBinder(120_000))
        val uid = Shizuku.getUid()
        Log.i(TAG, "SPIKE B binder re-delivered to fresh app process; getUid()=$uid")
        assertEquals(SHELL_UID, uid)
    }

    @Test
    fun binderDeathAndReactivation() {
        val transitions = mutableListOf<HelperActivationState.State>()
        val state = HelperActivationState { transitions.add(it) }
        state.attach()
        try {
            Log.i(TAG, "SPIKE C start: waiting for ACTIVE, then host kills the helper server")
            assertTrue(awaitBinder(120_000))
            assertTrue(
                "activation state must reach ACTIVE after binder receipt",
                awaitState(state, HelperActivationState.State.ACTIVE, 15_000),
            )

            assertTrue(
                "binder death must lead to NEEDS_REACTIVATION",
                awaitState(state, HelperActivationState.State.NEEDS_REACTIVATION, 120_000),
            )
            assertFalse("binder must be dead after the server was killed", Shizuku.pingBinder())
            assertNotNull("re-activation prompt must exist", state.reActivationPrompt)
            assertTrue("re-activation prompt must not be blank", state.reActivationPrompt.isNotBlank())
            Log.i(
                TAG,
                "SPIKE C binder death -> clean state NEEDS_REACTIVATION, prompt: ${state.reActivationPrompt}",
            )

            val reactivateDeadline = System.currentTimeMillis() + 120_000
            while (state.state != HelperActivationState.State.ACTIVE &&
                System.currentTimeMillis() < reactivateDeadline
            ) {
                Thread.sleep(500)
            }
            assertEquals("re-activation must restore ACTIVE", HelperActivationState.State.ACTIVE, state.state)
            Log.i(TAG, "SPIKE C re-activation restored ACTIVE; transitions=$transitions")
        } finally {
            state.detach()
        }
    }

    @Test
    fun revocationExitsCleanly() {
        Log.i(TAG, "SPIKE D start: waiting for helper binder, then revoking activation (manager exit())")
        assertTrue(awaitBinder(120_000))
        val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
        try {
            service.exit()
            Log.i(TAG, "SPIKE D exit() returned")
        } catch (t: Throwable) {
            Log.i(TAG, "SPIKE D exit() call ended with $t (expected if the server exited first)")
        }
        val deadline = System.currentTimeMillis() + 30_000
        while (Shizuku.pingBinder() && System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
        }
        assertFalse("server must be gone after activation is revoked", Shizuku.pingBinder())
        Log.i(TAG, "SPIKE D revocation: server exited and binder is dead (clean state)")
    }
}
