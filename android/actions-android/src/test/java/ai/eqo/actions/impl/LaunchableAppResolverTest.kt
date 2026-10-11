// Origin: EQO intent v1; synthetic launcher inventory and process-only caching regression.
package ai.eqo.actions.impl

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LaunchableAppResolverTest {
    @Test fun installedLabelsAndResolutionReuseOneSnapshotWithoutTelemetry() {
        var queries = 0
        val apps = listOf("example.keep" to "Keep", "example.calc" to "Calculator", "example.keep" to "Keep")
        val resolver =
            LaunchableAppResolver(
                { pkg -> if (apps.any { it.first == pkg }) Intent().setPackage(pkg) else null },
                {
                    queries++
                    apps
                },
            )
        assertEquals(listOf("Calculator", "Keep"), resolver.installedLabels())
        assertEquals("example.calc", resolver.resolve("Calculator")!!.getPackage())
        assertEquals(listOf("Calculator", "Keep"), resolver.installedLabels())
        assertNull(resolver.resolve("Absent"))
        assertEquals(1, queries)
    }

    @Test fun ambiguousInstalledLabelsNeverSelectOneAtRandom() {
        val apps = listOf("example.one" to "Notes One", "example.two" to "Notes Two")
        val resolver =
            LaunchableAppResolver(
                { pkg -> if (apps.any { it.first == pkg }) Intent().setPackage(pkg) else null },
                { apps },
            )
        assertNull(resolver.resolve("Notes"))
        assertEquals(listOf("Notes One", "Notes Two"), resolver.installedLabels())
    }
}
