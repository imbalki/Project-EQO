/*
 * EQO (TASK-009): in-process device records driver for the acceptance
 * criteria, run on the test target activity's background thread and logged
 * with the [EQO_RECORD] tag. This is the path that works on the Realme
 * Narzo 20 (Android 11): `am instrument` force-stops the package before every
 * run, which this OEM's AccessibilityManagerService records as a crashed
 * service and refuses to rebind for the rest of the run (see evidence). With
 * this driver the app process (and the system-bound EQO service) simply stays
 * alive for the whole record run.
 *
 * Record order matters: 3 leaves the service disabled until the owner
 * re-enables it, so 4 runs after the re-enable. The owner-assisted moments
 * (one screen touch, one EQO off/on cycle) show as prompts on the test screen.
 * EQO never grants itself anything: every enable/disable step is the owner's.
 */
package ai.eqo.test

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.GenericAppAutomator
import ai.eqo.accessibility.TakeoverDetector
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean

object EqoDeviceRecordsDriver {
    /** Starts the records on a background thread; returns immediately. */
    fun start(activity: EqoTestTargetActivity) {
        Thread({ runAll(activity) }, "eqo-device-records").start()
    }

    private fun record(line: String) {
        Log.i(TAG, line)
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

    /**
     * Runs an EQO action that may be preceded by our own dispatchGesture's
     * late TYPE_TOUCH_INTERACTION_START event (event latency: the touch start
     * arrives after the dispatch ended and is misattributed as a user
     * takeover - the documented detector limitation). Such latches are our own
     * noise, not user takeovers: clear and retry the action once. Never used
     * for the timed no-retry measurements of record 3.
     */
    private fun gestureNoiseRetry(
        takeover: TakeoverDetector,
        what: String,
        block: () -> A11yResult,
    ): A11yResult {
        takeover.resume(
            ai.eqo.core.agent.UserResumeConfirmation
                .forExplicitUserConfirmation(android.os.SystemClock.elapsedRealtime()),
        )
        val first = block()
        if (first is A11yResult.Failure && first.error is A11yError.TakeoverDetected) {
            record("NOTE: cleared a post-gesture takeover latch (own gesture noise) before $what")
            takeover.resume(
                ai.eqo.core.agent.UserResumeConfirmation
                    .forExplicitUserConfirmation(android.os.SystemClock.elapsedRealtime()),
            )
            return block()
        }
        return first
    }

    /** On-screen prompt for the owner; always applied on the UI thread. */
    private fun prompt(
        activity: EqoTestTargetActivity,
        message: String,
    ) {
        activity.runOnUiThread { activity.setPrompt(message) }
    }

    private fun awaitService(): EQOAccessibilityService? {
        val bound =
            await("EQO accessibility service binding", SERVICE_TIMEOUT_MS) {
                EQOAccessibilityService.getInstance() != null
            }
        if (!bound) {
            record("FAIL: EQO accessibility service is not bound (owner grant check needed)")
            return null
        }
        return EQOAccessibilityService.getInstance()
    }

    private fun runAll(activity: EqoTestTargetActivity) {
        // Owner touches that landed before this run are not test events: reset
        // the takeover detector before any record counts a touch (lead rule).
        TakeoverDetector.shared.resume(
            ai.eqo.core.agent.UserResumeConfirmation
                .forExplicitUserConfirmation(android.os.SystemClock.elapsedRealtime()),
        )
        val selected =
            activity.intent
                .getStringExtra(EqoTestTargetActivity.EXTRA_RECORDS)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()

        fun want(id: String): Boolean = selected == null || id in selected
        record("DRIVER start: device records, physical Realme Narzo 20, Android 11, selected=${selected ?: "all"}")
        try {
            val ok1 = if (want("1")) record1ObserveTapScrollType(activity) else true
            if (want("2")) record2UserTouchDuringActionPausesLoop(activity)
            val ok3 = if (want("3")) record3DisabledMidTaskGivesTypedErrorNoRetry(activity) else true
            if (want("4")) {
                if (ok1 && ok3) {
                    record4SecondAccessibilityAppCoexistence(activity)
                } else {
                    record("SKIP: record4 needs records 1 and 3 to pass first")
                }
            }
            // TASK-012: the action-loop device scenario (run, user taps, loop
            // pauses, resume, stop) lives in its own driver.
            if (want("5")) EqoActionLoopScenarioDriver.run(activity)
        } catch (t: Throwable) {
            record("DRIVER FAILED: $t")
        }
        prompt(activity, "none")
        record("DRIVER done")
    }

    /** Criterion 2: observe, tap, scroll and text input on the test app. */
    private fun record1ObserveTapScrollType(activity: EqoTestTargetActivity): Boolean {
        val service = awaitService() ?: return false
        val automation = service.automation
        val windowUp =
            await("test app window marker", WINDOW_TIMEOUT_MS) {
                val result = automation.observe()
                result is A11yResult.Success && result.detail.contains("EQO TEST TARGET")
            }
        if (!windowUp) {
            record("FAIL: observe() never saw the test app window")
            return false
        }

        val observed = automation.observe()
        val screen = (observed as? A11yResult.Success)?.detail ?: ""
        val missing =
            listOf("EQO TEST TARGET", "PRESS ME", "SUBMIT TEXT", "eqo_item_00").filterNot {
                screen.contains(it)
            }
        if (observed !is A11yResult.Success || missing.isNotEmpty()) {
            record("FAIL: observe() incomplete: result=$observed missing=$missing")
            return false
        }
        record("OBSERVE ok: test app screen text observed through EQO service")

        val takeover = TakeoverDetector.shared
        val tapped = gestureNoiseRetry(takeover, "tap('PRESS ME')") { automation.tap("PRESS ME") }
        val pressed =
            tapped is A11yResult.Success &&
                await("button side effect", ACTION_TIMEOUT_MS) {
                    activity.statusText() == "BUTTON PRESSED MARKER"
                }
        if (!pressed) {
            record("FAIL: tap('PRESS ME') result=$tapped status=${activity.statusText()}")
            return false
        }
        record("TAP ok: tap('PRESS ME') activated the test button")

        val typed =
            gestureNoiseRetry(takeover, "typeById") {
                automation.typeById("eqo_test_input", TYPE_PAYLOAD)
            }
        val submitted =
            gestureNoiseRetry(takeover, "tapById(eqo_test_submit)") {
                automation.tapById("eqo_test_submit")
            }
        val typeOk =
            typed is A11yResult.Success &&
                submitted is A11yResult.Success &&
                await("typed text side effect", ACTION_TIMEOUT_MS) {
                    activity.statusText() == "SUBMITTED: $TYPE_PAYLOAD"
                }
        if (!typeOk) {
            record("FAIL: type: typed=$typed submitted=$submitted status=${activity.statusText()}")
            return false
        }
        record("TYPE ok: typed '$TYPE_PAYLOAD' through EQO service into the test input")

        val before = activity.scrollOffset()
        // Own dispatchGesture touches can arrive as TYPE_TOUCH_INTERACTION_START
        // AFTER the dispatch ends (event latency) and latch the detector; that
        // is our own noise, not a user takeover (documented limitation).
        val scrolled = gestureNoiseRetry(takeover, "scroll") { automation.scroll(forward = true) }
        val moved =
            scrolled is A11yResult.Success &&
                await("scroll offset change", ACTION_TIMEOUT_MS) {
                    activity.scrollOffset() > before
                }
        if (!moved) {
            record("FAIL: scroll: result=$scrolled offset=$before -> ${activity.scrollOffset()}")
            return false
        }
        record("SCROLL ok: list scrolled $before -> ${activity.scrollOffset()} through EQO service")
        return true
    }

    /**
     * Criterion 3: a user touch during an agent action is detected and the
     * loop pauses. In-process there is no instrumentation input pipeline, so
     * the record is proven by the owner's real finger (the takeover detector's
     * documented primary path: TYPE_TOUCH_INTERACTION_START plus the overlay
     * probe where the OEM allows the overlay).
     */
    private fun record2UserTouchDuringActionPausesLoop(activity: EqoTestTargetActivity) {
        val service = awaitService() ?: return
        val automation = service.automation
        val takeover = TakeoverDetector.shared
        takeover.resume(
            ai.eqo.core.agent.UserResumeConfirmation
                .forExplicitUserConfirmation(android.os.SystemClock.elapsedRealtime()),
        )
        if (takeover.takeoverCount != 0) {
            record("NOTE: takeoverCount=${takeover.takeoverCount} before the window (pre-reset)")
        }

        // Keep a real agent action in flight continuously for the touch window.
        // observe() only: it brackets an agent action (so a user touch counts)
        // but dispatches NO gesture, so no own-gesture TYPE_TOUCH_INTERACTION_
        // START noise can latch the detector - any latch here is a real finger.
        val stop = AtomicBoolean(false)
        val worker =
            Thread {
                while (!stop.get()) {
                    automation.observe()
                    Thread.sleep(OBSERVE_LOOP_INTERVAL_MS)
                }
            }
        worker.start()
        try {
            prompt(activity, "TAP THE BLANK SCREEN ONCE NOW (do NOT tap the round floating icon)")
            record("AWAITING physical owner touch on screen (agent actions in flight)")
            await("takeover latch from physical owner touch", OWNER_TOUCH_WINDOW_MS) {
                takeover.isPaused
            }
        } finally {
            stop.set(true)
            worker.join(WORKER_JOIN_MS)
        }

        if (!takeover.isPaused) {
            record("FAIL: no user touch during an agent action was detected as takeover")
            return
        }
        record("TAKEOVER ok (mode=physical-owner-touch): user touch during agent action latched takeover")

        val refused = automation.observe()
        val refusedFast = GenericAppAutomator.scrapeScreen()
        val okRefusal =
            refused is A11yResult.Failure &&
                refused.error is A11yError.TakeoverDetected &&
                refusedFast is A11yResult.Failure &&
                refusedFast.error is A11yError.TakeoverDetected
        if (!okRefusal) {
            record("FAIL: pause gate did not refuse with typed TakeoverDetected: $refused / $refusedFast")
            return
        }
        record("PAUSE ok: plan-loop gate refused actions with typed TakeoverDetected")

        takeover.resume(
            ai.eqo.core.agent.UserResumeConfirmation
                .forExplicitUserConfirmation(android.os.SystemClock.elapsedRealtime()),
        )
        record("takeover resumed by the driver for the remaining records")
    }

    /**
     * Criterion 4: accessibility disabled mid-task gives a typed error with no
     * silent retry. The owner turns EQO off in Settings > Accessibility while
     * the driver keeps acting (EQO never toggles its own permission).
     */
    private fun record3DisabledMidTaskGivesTypedErrorNoRetry(activity: EqoTestTargetActivity): Boolean {
        val service = awaitService() ?: return false
        val automation = service.automation
        val takeover = TakeoverDetector.shared

        val midTap = gestureNoiseRetry(takeover, "mid-task tap") { automation.tap("PRESS ME") }
        if (midTap !is A11yResult.Success ||
            automation.observe() !is A11yResult.Success
        ) {
            record("FAIL: mid-task actions before the disable did not succeed")
            return false
        }
        record("mid-task actions running; owner turns EQO OFF now")

        prompt(activity, "TURN EQO OFF: Settings > Accessibility > EQO (owner)")
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

        val start = SystemClock.elapsedRealtime()
        val result = automation.observe()
        val elapsed = SystemClock.elapsedRealtime() - start
        if (result !is A11yResult.Failure || result.error !is A11yError.AccessibilityDisabled) {
            record("FAIL: expected typed A11yError.AccessibilityDisabled, got $result")
            return false
        }
        if (elapsed >= FAST_FAIL_MS) {
            record("FAIL: disabled call took ${elapsed}ms: silent retry suspected (must fail fast)")
            return false
        }

        val start2 = SystemClock.elapsedRealtime()
        val wrapped = runBlocking { GenericAppAutomator.clickText("PRESS ME") }
        val elapsed2 = SystemClock.elapsedRealtime() - start2
        if (wrapped !is A11yResult.Failure || wrapped.error !is A11yError.AccessibilityDisabled) {
            record("FAIL: GenericAppAutomator did not return typed AccessibilityDisabled: $wrapped")
            return false
        }
        if (elapsed2 >= FAST_FAIL_MS) {
            record("FAIL: GenericAppAutomator took ${elapsed2}ms: silent retry suspected (must fail fast)")
            return false
        }
        record("DISABLED ok: typed AccessibilityDisabled in ${elapsed}ms / wrapper ${elapsed2}ms, zero retries")

        prompt(activity, "TURN EQO ON AGAIN: Settings > Accessibility > EQO (owner)")
        val back =
            await("EQO re-enabled by owner", REENABLE_WINDOW_MS) {
                EQOAccessibilityService.getInstance() != null
            }
        if (!back) {
            record("FAIL: owner did not re-enable EQO within ${REENABLE_WINDOW_MS}ms")
            return false
        }
        takeover.resume(
            ai.eqo.core.agent.UserResumeConfirmation
                .forExplicitUserConfirmation(android.os.SystemClock.elapsedRealtime()),
        )
        return true
    }

    /**
     * Criterion 5: another accessibility app enabled does not break EQO.
     * Read-only probe of enabled_accessibility_services; if no second
     * accessibility app is enabled on this device the record says 'not tested'.
     */
    private fun record4SecondAccessibilityAppCoexistence(activity: EqoTestTargetActivity) {
        val service = awaitService() ?: return
        val automation = service.automation
        val enabled =
            Settings.Secure
                .getString(activity.contentResolver, "enabled_accessibility_services")
                .orEmpty()
        record("enabled_accessibility_services (read-only probe): '$enabled'")

        val others =
            enabled
                .split(':')
                .filter { it.isNotBlank() }
                .filterNot { it.startsWith("${activity.packageName}/") }

        if (others.isEmpty()) {
            record("COEXISTENCE not tested: no second accessibility app enabled on this device")
            return
        }
        record("COEXISTENCE partners enabled: ${others.joinToString(", ")}")
        val observed = automation.observe()
        if (observed !is A11yResult.Success || !observed.detail.contains("EQO TEST TARGET")) {
            record("FAIL: EQO broken with second accessibility app enabled: $observed")
            return
        }
        val tapped =
            gestureNoiseRetry(TakeoverDetector.shared, "coexistence tap") {
                automation.tap("PRESS ME")
            }
        if (tapped !is A11yResult.Success) {
            record("FAIL: EQO tap broken with second accessibility app enabled: $tapped")
            return
        }
        record("COEXISTENCE ok: EQO observe+tap work with ${others.size} other a11y app(s) enabled")
    }

    private const val TAG = "EQO_RECORD"
    private const val TYPE_PAYLOAD = "hello from eqo"
    private const val POLL_INTERVAL_MS = 500L
    private const val SERVICE_TIMEOUT_MS = 30000L
    private const val WINDOW_TIMEOUT_MS = 15000L
    private const val ACTION_TIMEOUT_MS = 5000L
    private const val OWNER_TOUCH_WINDOW_MS = 120000L
    private const val WORKER_JOIN_MS = 5000L
    private const val OBSERVE_LOOP_INTERVAL_MS = 50L
    private const val DISABLE_WINDOW_MS = 180000L
    private const val FAST_FAIL_MS = 1000L
    private const val REENABLE_WINDOW_MS = 180000L
}
