package ai.eqo

import ai.eqo.accessibility.EQOAccessibilityService
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Owner-pending: enable EQO in Android Settings for the real study app first. */
class RealAccessibilityServiceSmokeTest {
    @Test
    fun realStudyApplicationHasABoundServiceWithoutATestHiltGraph() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("ai.eqo.app", target.packageName)
        assertEquals(EqoApplication::class.java, target.applicationContext.javaClass)
        // Android alone can bind this BIND_ACCESSIBILITY_SERVICE component. Never self-enable it.
        // Instrumentation may restart the app; allow the OS a bounded interval to rebind.
        val deadline = SystemClock.uptimeMillis() + BIND_TIMEOUT_MS
        var service = EQOAccessibilityService.getInstance()
        while (service == null && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(BIND_POLL_MS)
            service = EQOAccessibilityService.getInstance()
        }
        assertNotNull("Owner must enable accessibility for ai.eqo.app, not ai.eqo.test", service)
        assertSame(target.applicationContext, service!!.application)
        assertSame(service.automation, service.automation)
    }

    companion object {
        private const val BIND_TIMEOUT_MS = 10_000L
        private const val BIND_POLL_MS = 100L
    }
}
