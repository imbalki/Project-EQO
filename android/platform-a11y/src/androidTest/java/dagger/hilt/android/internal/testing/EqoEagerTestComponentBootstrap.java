/*
 * EQO (TASK-009): eagerly builds the Hilt test component at application start.
 *
 * dagger.hilt.android.testing.HiltTestApplication only creates its component
 * once a HiltAndroidRule test is running, so when the Android system binds and
 * creates EQOAccessibilityService on its own (package replace, service
 * rebind after every `am instrument` force-stop) the service crashed with
 * "IllegalStateException: The component was not created. Check that you have
 * added the HiltAndroidRule." and the framework wedged the binding into its
 * crash backoff (observed on the Realme Narzo 20 as
 * "Scheduling restart of crashed service ... in 1800000ms").
 *
 * This helper drives the same TestApplicationComponentManager sequence that
 * Hilt's own test rule drives (setAutoAddModule -> setTestInstance ->
 * setHasHiltTestRule, whose last call triggers tryToCreateComponent()), but at
 * process start and outside any test, so the service can be created by the
 * system at any time. It lives in dagger.hilt.android.internal.testing because
 * the driving methods are package-private to that package; nothing outside
 * this test APK uses it. EqoTestHostApplication registers an instrumentation
 * shim first so the generated component supplier can resolve the application.
 */
package dagger.hilt.android.internal.testing;

import android.app.Application;

import org.junit.runner.Description;

import java.lang.Class;

/** TASK-009: eager Hilt test-component bootstrap for the EQO test host app. */
public final class EqoEagerTestComponentBootstrap {
    private EqoEagerTestComponentBootstrap() {
    }

    /**
     * Builds the Hilt test root component for {@code testClass} (a class
     * annotated with {@code @HiltAndroidTest}) immediately and returns the
     * manager holding it.
     */
    public static TestApplicationComponentManager bootstrap(
            Application application,
            Class<?> testClass) {
        try {
            TestApplicationComponentManager manager =
                    new TestApplicationComponentManager(application);
            manager.setAutoAddModule(true);
            manager.setTestInstance(testClass.getDeclaredConstructor().newInstance());
            manager.setHasHiltTestRule(
                    Description.createTestDescription(testClass, "eagerBoot"));
            Object component = manager.generatedComponent();
            android.util.Log.i(
                    "EqoEagerTestComponentBootstrap",
                    "TASK-009: eager test component ready: " + component.getClass().getName());
            return manager;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "TASK-009: cannot instantiate the @HiltAndroidTest anchor class", e);
        }
    }
}
