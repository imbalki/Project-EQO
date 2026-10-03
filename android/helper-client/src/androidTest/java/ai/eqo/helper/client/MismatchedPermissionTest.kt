package ai.eqo.helper.client

import android.util.Log
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rikka.shizuku.Shizuku

/**
 * EQO-authored (TASK-007, issue #12): device negative test - a client whose manifest
 * declares the MISMATCHED (pre-rename) permission string must not receive the helper
 * binder, and a privileged call must fail loudly.
 *
 * Host-side sequencing: run this alongside the EQO helper spike runs (the helper server
 * must be activated); see android/Phase-One/evidence/task-007-helper-spike.md.
 */
class MismatchedPermissionTest {
    companion object {
        private const val TAG = "EqoHelperSpikeNeg"
    }

    @Test
    fun mismatchedPermissionStringIsDeniedLoudly() {
        Log.i(TAG, "SPIKE NEG start: mismatched (pre-rename) permission string client")
        // Give the helper server time to enumerate this app's requested permissions.
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
        }
        assertFalse(
            "mismatched-permission client must NOT receive the helper binder",
            Shizuku.pingBinder(),
        )

        var thrown: Throwable? = null
        try {
            Shizuku.getUid()
        } catch (t: Throwable) {
            thrown = t
        }
        assertNotNull("privileged call must fail loudly without the binder", thrown)
        assertTrue("failure must be a typed runtime error", thrown is RuntimeException)
        Log.i(TAG, "SPIKE NEG ok: no binder delivered; privileged call failed loudly: $thrown")
    }
}
