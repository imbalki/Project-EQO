package ai.eqo

import ai.eqo.accessibility.EQOAccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises onCreate under the real production Application, never a Hilt test app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = EqoApplication::class)
class ProductionServiceStartupTest {
    @Test
    fun serviceOnCreateDoesNotRequireTheDonorHiltGraph() {
        val controller = Robolectric.buildService(EQOAccessibilityService::class.java).create()
        try {
            val service = controller.get()
            assertEquals(EqoApplication::class.java, service.application.javaClass)
            assertSame(service.automation, service.automation)
        } finally {
            controller.destroy()
        }
    }
}
