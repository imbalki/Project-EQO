/*
 * EQO (TASK-009): on-device test records for the acceptance criteria, driven
 * through the single EQO accessibility service and its typed EqoAutomation
 * layer. Run AFTER the owner has enabled EQO in Android's Accessibility
 * settings (EQO never grants itself). Every record line is printed with the
 * [EQO_RECORD] tag so the device log is the evidence artifact.
 *
 * Test order (MethodSorters.NAME_ASCENDING) matters: 3 leaves the service
 * disabled until the owner re-enables it, so 4 waits for the re-enable.
 */
package ai.eqo.test

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.GenericAppAutomator
import ai.eqo.accessibility.TakeoverDetector
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EqoDeviceRecordsTest {
    private fun record(line: String) {
        // Print to the instrumentation output stream: the device test record.
        println("[EQO_RECORD] $line")
    }

    private fun await(
        what: String,
        timeoutMs: Long,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        record("TIMEOUT waiting for: $what")
        return condition()
    }

    private fun awaitServiceOrFail(): EQOAccessibilityService {
        val bound =
            await("EQO accessibility service binding", SERVICE_TIMEOUT_MS) {
                EQOAccessibilityService.getInstance() != null
            }
        assertTrue(
            "EQO accessibility service is not bound. Enable it in " +
                "Settings > Accessibility > EQO and re-run this test. " +
                "EQO never grants this permission itself.",
            bound,
        )
        val service = EQOAccessibilityService.getInstance()
        assertNotNull("service instance vanished after binding", service)
        return service!!
    }

    private fun launchTargetActivity(): EqoTestTargetActivity {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(
            Intent(context, EqoTestTargetActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val up =
            await("test target activity", ACTIVITY_TIMEOUT_MS) {
                EqoTestTargetActivity.lastInstance != null
            }
        assertTrue("test target activity did not come up", up)
        return EqoTestTargetActivity.lastInstance!!
    }

    private fun awaitWindowMarker(
        automation: EqoAutomation,
        marker: String,
    ): Boolean =
        await("window marker '$marker'", WINDOW_TIMEOUT_MS) {
            val result = automation.observe()
            result is A11yResult.Success && result.detail.contains(marker)
        }

    /**
     * Criterion 2: observe, tap, scroll and text input proven on the test app
     * (EqoTestTargetActivity) through the single EQO accessibility service.
     */
    @Test
    fun test1ObserveTapScrollTypeOnTestApp() {
        val activity = launchTargetActivity()
        val service = awaitServiceOrFail()
        val automation = service.automation

        assertTrue(
            "observe() never saw the test app window",
            awaitWindowMarker(automation, "EQO TEST TARGET"),
        )

        // ── observe ──────────────────────────────────────────────────────────
        val observed = automation.observe()
        assertTrue("observe() failed: $observed", observed is A11yResult.Success)
        val screen = (observed as A11yResult.Success).detail
        listOf("EQO TEST TARGET", "PRESS ME", "SUBMIT TEXT", "eqo_item_00").forEach {
            assertTrue("observe() screen text missing '$it'", screen.contains(it))
        }
        record("OBSERVE ok: test app screen text observed through EQO service")

        // ── tap ──────────────────────────────────────────────────────────────
        val tapped = automation.tap("PRESS ME")
        assertTrue("tap('PRESS ME') failed: $tapped", tapped is A11yResult.Success)
        val pressed =
            await("button side effect", ACTION_TIMEOUT_MS) {
                activity.statusText() == "BUTTON PRESSED MARKER"
            }
        assertTrue("tap did not activate the button", pressed)
        record("TAP ok: tap('PRESS ME') activated the test button")

        // ── type ─────────────────────────────────────────────────────────────
        val typed = automation.typeById("eqo_test_input", TYPE_PAYLOAD)
        assertTrue("typeById failed: $typed", typed is A11yResult.Success)
        val submitted = automation.tapById("eqo_test_submit")
        assertTrue("tapById(eqo_test_submit) failed: $submitted", submitted is A11yResult.Success)
        val statusOk =
            await("typed text side effect", ACTION_TIMEOUT_MS) {
                activity.statusText() == "SUBMITTED: $TYPE_PAYLOAD"
            }
        assertTrue("typed text never reached the input", statusOk)
        record("TYPE ok: typed '$TYPE_PAYLOAD' through EQO service into the test input")

        // ── scroll ───────────────────────────────────────────────────────────
        val before = activity.scrollOffset()
        val scrolled = automation.scroll(forward = true)
        assertTrue("scroll(forward) failed: $scrolled", scrolled is A11yResult.Success)
        val moved =
            await("scroll offset change", ACTION_TIMEOUT_MS) {
                activity.scrollOffset() > before
            }
        assertTrue("scroll action did not move the test list", moved)
        record("SCROLL ok: list scrolled $before -> ${activity.scrollOffset()} through EQO service")
    }

    /**
     * Criterion 3: a user touch during an agent action is detected and the loop
     * pauses. A touch is injected mid-action; if the build attributes injected
     * touches differently the test falls back to waiting for the owner's real
     * finger (prompt on screen) and records which mode proved it.
     */
    @Test
    fun test2UserTouchDuringActionPausesLoop() {
        val activity = launchTargetActivity()
        val service = awaitServiceOrFail()
        val automation = service.automation
        val takeover = TakeoverDetector.shared
        assertTrue("window not ready", awaitWindowMarker(automation, "EQO TEST TARGET"))

        takeover.resume(
            ai.eqo.core.agent.UserResumeConfirmation
                .forExplicitUserConfirmation(android.os.SystemClock.elapsedRealtime()),
        )
        assertEquals("takeover detector not reset", 0, takeover.takeoverCount)

        // Keep a real agent action in flight continuously for the touch window.
        val stop =
            java.util.concurrent.atomic
                .AtomicBoolean(false)
        val worker =
            Thread {
                while (!stop.get()) {
                    automation.scroll(forward = true)
                    automation.scroll(forward = false)
                    automation.observe()
                }
            }
        worker.start()

        var mode = "injected-touch"
        try {
            Thread.sleep(INJECT_DELAY_MS)
            injectTouch(activity)
            val latched =
                await("takeover latch from injected touch", INJECT_WINDOW_MS) {
                    takeover.isPaused
                }
            if (!latched) {
                mode = "physical-owner-touch"
                activity.setPrompt("TOUCH THE SCREEN NOW (owner)")
                record("AWAITING physical owner touch on screen (injected touch did not latch)")
                await("takeover latch from physical owner touch", OWNER_TOUCH_WINDOW_MS) {
                    takeover.isPaused
                }
            }
        } finally {
            stop.set(true)
            worker.join(WORKER_JOIN_MS)
        }

        assertTrue(
            "no user touch during an agent action was detected as takeover " +
                "(mode=$mode); see evidence notes for OEM attribution limits",
            takeover.isPaused,
        )
        record("TAKEOVER ok (mode=$mode): user touch during agent action latched takeover")

        // The loop pauses: while latched, every action is refused with the typed
        // TakeoverDetected error (the plan loop halts on the same gate).
        val refused = automation.observe()
        assertTrue(
            "action during takeover not refused: $refused",
            refused is A11yResult.Failure && refused.error is A11yError.TakeoverDetected,
        )
        val refusedFast = GenericAppAutomator.scrapeScreen()
        assertTrue(
            "GenericAppAutomator did not surface typed TakeoverDetected: $refusedFast",
            refusedFast is A11yResult.Failure && refusedFast.error is A11yError.TakeoverDetected,
        )
        record("PAUSE ok: plan-loop gate refused actions with typed TakeoverDetected")

        takeover.resume(
            ai.eqo.core.agent.UserResumeConfirmation
                .forExplicitUserConfirmation(android.os.SystemClock.elapsedRealtime()),
        )
        activity.setPrompt("none")
        record("takeover resumed by test for the remaining records")
    }

    /**
     * Criterion 4: accessibility disabled mid-task gives a typed error with no
     * silent retry. The OWNER turns EQO off in Settings > Accessibility while
     * this test is running (EQO never toggles its own permission).
     */
    @Test
    fun test3DisabledMidTaskGivesTypedErrorNoRetry() {
        val activity = launchTargetActivity()
        val service = awaitServiceOrFail()
        val automation = service.automation
        val takeover = TakeoverDetector.shared
        assertTrue("window not ready", awaitWindowMarker(automation, "EQO TEST TARGET"))

        // Mid-task: two successful actions before the disable.
        assertTrue(automation.tap("PRESS ME") is A11yResult.Success)
        assertTrue(automation.observe() is A11yResult.Success)
        record("mid-task actions running; prompt the owner to turn EQO OFF now")

        activity.setPrompt("TURN EQO OFF: Settings > Accessibility > EQO (owner)")
        await("typed AccessibilityDisabled after owner disables EQO", DISABLE_WINDOW_MS) {
            val result = automation.observe()
            if (result is A11yResult.Failure && result.error is A11yError.TakeoverDetected) {
                // The owner's own touches while navigating Settings are expected
                // here; they are not a takeover record. Clear and keep polling.
                takeover.resume(
                    ai.eqo.core.agent.UserResumeConfirmation
                        .forExplicitUserConfirmation(android.os.SystemClock.elapsedRealtime()),
                )
                false
            } else {
                result is A11yResult.Failure && result.error is A11yError.AccessibilityDisabled
            }
        }

        // The failing call must be the typed error, fast (one attempt, no retry).
        val start = SystemClock.elapsedRealtime()
        val result = automation.observe()
        val elapsed = SystemClock.elapsedRealtime() - start
        assertTrue(
            "expected typed A11yError.AccessibilityDisabled, got $result",
            result is A11yResult.Failure && result.error is A11yError.AccessibilityDisabled,
        )
        assertTrue(
            "disabled call took ${elapsed}ms: silent retry suspected (must fail fast)",
            elapsed < FAST_FAIL_MS,
        )

        // No silent retry through the retry-capable wrapper either.
        val start2 = SystemClock.elapsedRealtime()
        val wrapped = runBlocking { GenericAppAutomator.clickText("PRESS ME") }
        val elapsed2 = SystemClock.elapsedRealtime() - start2
        assertTrue(
            "GenericAppAutomator did not return typed AccessibilityDisabled: $wrapped",
            wrapped is A11yResult.Failure && wrapped.error is A11yError.AccessibilityDisabled,
        )
        assertTrue(
            "GenericAppAutomator took ${elapsed2}ms: silent retry suspected (must fail fast)",
            elapsed2 < FAST_FAIL_MS,
        )
        record("DISABLED ok: typed AccessibilityDisabled in ${elapsed}ms / wrapper ${elapsed2}ms, zero retries")

        // Leave the device restorable: prompt the owner to re-enable and wait
        // until the service is bound again (same gate test 4 depends on).
        activity.setPrompt("TURN EQO ON AGAIN: Settings > Accessibility > EQO (owner)")
        val back =
            await("EQO re-enabled by owner", REENABLE_WINDOW_MS) {
                EQOAccessibilityService.getInstance() != null
            }
        assertTrue("owner did not re-enable EQO within ${REENABLE_WINDOW_MS}ms", back)
        takeover.resume(
            ai.eqo.core.agent.UserResumeConfirmation
                .forExplicitUserConfirmation(android.os.SystemClock.elapsedRealtime()),
        )
    }

    /**
     * Criterion 5: another accessibility app enabled does not break EQO.
     * Read-only probe of enabled_accessibility_services; if no second
     * accessibility app is enabled on this device the record says 'not tested'.
     */
    @Test
    fun test4SecondAccessibilityAppCoexistence() {
        val activity = launchTargetActivity()
        val service = awaitServiceOrFail()
        val automation = service.automation
        assertTrue("window not ready", awaitWindowMarker(automation, "EQO TEST TARGET"))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val enabled =
            android.provider.Settings.Secure
                .getString(context.contentResolver, "enabled_accessibility_services")
                .orEmpty()
        record("enabled_accessibility_services (read-only probe): '$enabled'")

        val others =
            enabled
                .split(':')
                .filter { it.isNotBlank() }
                .filterNot { it.startsWith("${context.packageName}/") }

        if (others.isEmpty()) {
            record("COEXISTENCE not tested: no second accessibility app enabled on this device")
        } else {
            record("COEXISTENCE partners enabled: ${others.joinToString(", ")}")
            val observed = automation.observe()
            assertTrue(
                "EQO broken with second accessibility app enabled: $observed",
                observed is A11yResult.Success && observed.detail.contains("EQO TEST TARGET"),
            )
            assertTrue(automation.tap("PRESS ME") is A11yResult.Success)
            record("COEXISTENCE ok: EQO observe+tap work with ${others.size} other a11y app(s) enabled")
        }
        activity.setPrompt("none")
    }

    /** Injects a center-screen tap through the instrumentation input pipeline. */
    private fun injectTouch(activity: EqoTestTargetActivity) {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val x = activity.resources.displayMetrics.widthPixels / 2f
        val y = activity.resources.displayMetrics.heightPixels / 2f
        val downTime = SystemClock.uptimeMillis()
        val down =
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
        val up =
            MotionEvent
                .obtain(
                    downTime,
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_UP,
                    x,
                    y,
                    0,
                ).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                }
        uiAutomation.injectInputEvent(down, true)
        uiAutomation.injectInputEvent(up, true)
        down.recycle()
        up.recycle()
    }

    companion object {
        private const val TYPE_PAYLOAD = "hello from eqo"
        private const val POLL_INTERVAL_MS = 500L
        private const val SERVICE_TIMEOUT_MS = 90000L
        private const val ACTIVITY_TIMEOUT_MS = 15000L
        private const val WINDOW_TIMEOUT_MS = 15000L
        private const val ACTION_TIMEOUT_MS = 5000L
        private const val INJECT_DELAY_MS = 2000L
        private const val INJECT_WINDOW_MS = 5000L
        private const val OWNER_TOUCH_WINDOW_MS = 60000L
        private const val WORKER_JOIN_MS = 5000L
        private const val DISABLE_WINDOW_MS = 180000L
        private const val FAST_FAIL_MS = 1000L
        private const val REENABLE_WINDOW_MS = 180000L
    }
}
