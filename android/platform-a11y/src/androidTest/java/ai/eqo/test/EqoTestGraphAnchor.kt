/*
 * EQO (TASK-009): the @HiltAndroidTest anchor. Hilt only generates the test
 * root component (and its TestComponentData) for classes annotated
 * @HiltAndroidTest; before this anchor existed the androidTest APK had NO
 * generated component at all, so EQOAccessibilityService could never be
 * injected when the system bound it. EqoTestHostApplication builds this
 * anchor's component eagerly at process start; this test checks the eager
 * component really exposes what the service's own component manager needs
 * (ServiceComponentBuilderEntryPoint - the ServiceC subcomponent built from it
 * implements EQOAccessibilityService_GeneratedInjector).
 */
package ai.eqo.test

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.internal.managers.ServiceComponentManager
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.internal.GeneratedComponentManagerHolder
import org.junit.Assert.assertTrue
import org.junit.Test

@HiltAndroidTest
class EqoTestGraphAnchor {
    @Test
    fun eagerComponentCanBuildTheAccessibilityServiceComponent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val component = (app as GeneratedComponentManagerHolder).generatedComponent()
        assertTrue(
            "eager test component cannot build the service component: $component",
            component is ServiceComponentManager.ServiceComponentBuilderEntryPoint,
        )
    }
}
