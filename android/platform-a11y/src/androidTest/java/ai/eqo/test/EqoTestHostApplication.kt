/*
 * EQO (TASK-009): the test host application for the single EQO accessibility
 * service. dagger.hilt.android.testing.HiltTestApplication only creates its
 * Hilt component inside a running HiltAndroidRule test, so when the Android
 * system created EQOAccessibilityService outside a test the service
 * crashed ("The component was not created") and the accessibility binding
 * wedged into the framework's crash backoff. This host builds the same test
 * component eagerly in onCreate() via EqoEagerTestComponentBootstrap, so the
 * system can bind and create the service at any time (install, rebind after
 * `am instrument` force-stop). Hilt's generated component supplier resolves
 * the application through ApplicationProvider, which needs a registered
 * instrumentation: outside a test run this host registers a minimal shim that
 * answers with the application itself. Under `am instrument` the real
 * instrumentation is already registered and is left untouched.
 *
 * Scope: androidTest of :platform-a11y only (lead-authorized test wiring).
 */
package ai.eqo.test

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.internal.testing.EqoEagerTestComponentBootstrap
import dagger.hilt.android.internal.testing.TestApplicationComponentManager
import dagger.hilt.android.internal.testing.TestApplicationComponentManagerHolder
import dagger.hilt.internal.GeneratedComponentManager

class EqoTestHostApplication :
    Application(),
    TestApplicationComponentManagerHolder {
    @Volatile
    private var manager: TestApplicationComponentManager? = null

    override fun onCreate() {
        super.onCreate()
        ensureInstrumentationForComponentLookup()
        manager = EqoEagerTestComponentBootstrap.bootstrap(this, EqoTestGraphAnchor::class.java)
    }

    override fun componentManager(): GeneratedComponentManager<*> = manager ?: error("TASK-009: eager component bootstrap did not run")

    override fun generatedComponent(): Any =
        manager?.generatedComponent()
            ?: error("TASK-009: eager component bootstrap did not run")

    /**
     * Hilt's generated TestComponentDataSupplier resolves the application via
     * ApplicationProvider -> InstrumentationRegistry. When the system starts
     * this process outside a test run no instrumentation is registered, so
     * register a shim answering with this application. Never replaces a real
     * instrumentation.
     */
    private fun ensureInstrumentationForComponentLookup() {
        val alreadyRegistered =
            runCatching { InstrumentationRegistry.getInstrumentation() }.isSuccess
        if (alreadyRegistered) return
        InstrumentationRegistry.registerInstance(
            object : Instrumentation() {
                override fun getContext(): Context = this@EqoTestHostApplication

                override fun getTargetContext(): Context = this@EqoTestHostApplication
            },
            Bundle(),
        )
    }
}
