// Origin: EQO vision-locate, offline privacy and geometry regressions.
package ai.eqo.core.agent

import ai.eqo.core.llm.ControlBox
import ai.eqo.core.llm.ControlLocator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionLocateRunTest {
    private val box = ControlBox(20, 30, 80, 70)
    private val initial = VisionScreenState(100, 200, "before", "test.app", ControlBox(0, 0, 100, 200))

    private class Screen(var snapshot: VisionScreenState) : VisionLocateScreen {
        var captures = 0
        var taps = 0
        var allowCapture = true
        var allowTap = true
        var change = true
        var afterCapture: (() -> Unit)? = null
        var tapped: ControlBox? = null

        override fun state(): VisionScreenState = snapshot

        override suspend fun capture(): VisionFrame? {
            captures++
            afterCapture?.invoke()
            return if (allowCapture) VisionFrame("synthetic-image", snapshot.width, snapshot.height) else null
        }

        override fun tap(box: ControlBox, expected: VisionScreenState): Boolean {
            taps++
            tapped = box
            return allowTap && snapshot == expected
        }

        override suspend fun settledState(): VisionScreenState {
            if (change) snapshot = snapshot.copy(signature = "after")
            return snapshot
        }
    }

    @Test
    fun foundTapsApprovedBoxOnceAndLogsOnlyFixedOutcome() = runTest {
        val screen = Screen(initial)
        val logs = mutableListOf<String>()
        var calls = 0
        val run = VisionLocateRun({ true }, {
            ControlLocator { label, image, width, height ->
                calls++
                assertEquals("Approved label", label)
                assertEquals("synthetic-image", image)
                assertEquals(100, width)
                assertEquals(200, height)
                box
            }
        }, screen, logs::add)
        assertTrue(run.locateAndTap("Approved label") is ExecuteResult.Success)
        assertEquals(1, screen.captures)
        assertEquals(1, calls)
        assertEquals(1, screen.taps)
        assertEquals(box, screen.tapped)
        assertEquals(listOf("vision locate: attempt 1, result found"), logs)
        assertFalse(VisionFrame("synthetic-image", 1, 1).toString().contains("synthetic-image"))
    }

    @Test
    fun notFoundDoesNotTap() = runTest {
        val screen = Screen(initial)
        val run = VisionLocateRun({ true }, { ControlLocator { _, _, _, _ -> null } }, screen)
        assertTrue(run.locateAndTap("Label") is ExecuteResult.Failure)
        assertEquals(1, screen.captures)
        assertEquals(0, screen.taps)
    }

    @Test
    fun outOfBoundsAndDegenerateBoxesAreRejected() = runTest {
        val boxes = listOf(ControlBox(-1, 0, 20, 20), ControlBox(0, 0, 101, 20), ControlBox(0, 0, 20, 201), ControlBox(20, 10, 20, 20), ControlBox(40, 20, 30, 40))
        for (candidate in boxes) {
            val screen = Screen(initial)
            val run = VisionLocateRun({ true }, { ControlLocator { _, _, _, _ -> candidate } }, screen)
            assertTrue(run.locateAndTap("Label") is ExecuteResult.Failure)
            assertEquals(0, screen.taps)
        }
    }

    @Test
    fun ownWindowOverlapAndOtherAppRegionAreRejected() = runTest {
        for (state in listOf(initial.copy(ownWindows = listOf(ControlBox(60, 60, 100, 100))), initial.copy(targetBounds = ControlBox(0, 0, 50, 200)))) {
            val screen = Screen(state)
            val run = VisionLocateRun({ true }, { ControlLocator { _, _, _, _ -> box } }, screen)
            assertTrue(run.locateAndTap("Label") is ExecuteResult.Failure)
            assertEquals(0, screen.taps)
        }
    }

    @Test
    fun secureScreenAndPasswordAndTakeoverRefuseBeforeCaptureOrModel() = runTest {
        for (reason in listOf("Protected screen", "A password field is visible", "User takeover")) {
            val screen = Screen(initial.copy(refusal = reason))
            val run = VisionLocateRun({ true }, { error("Must not resolve a model") }, screen)
            val result = run.locateAndTap("Label") as ExecuteResult.Failure
            assertEquals(reason, result.reason)
            assertEquals(0, screen.captures)
            assertEquals(0, screen.taps)
        }
    }

    @Test
    fun captureRefusalNeverSendsImage() = runTest {
        val screen = Screen(initial).apply { allowCapture = false }
        val run = VisionLocateRun({ true }, { ControlLocator { _, _, _, _ -> error("Must not send") } }, screen)
        assertTrue(run.locateAndTap("Label") is ExecuteResult.Failure)
        assertEquals(1, screen.captures)
        assertEquals(0, screen.taps)
    }

    @Test
    fun capIsSharedAcrossLabelsAndStopsAtSix() = runTest {
        val screen = Screen(initial)
        var calls = 0
        val run = VisionLocateRun({ true }, { ControlLocator { _, _, _, _ -> calls++; null } }, screen)
        repeat(7) { assertTrue(run.locateAndTap("Label $it") is ExecuteResult.Failure) }
        assertEquals(6, screen.captures)
        assertEquals(6, calls)
    }

    @Test
    fun modelWithoutVisionDoesNotCapture() = runTest {
        val screen = Screen(initial)
        val run = VisionLocateRun({ true }, { null }, screen)
        assertTrue(run.locateAndTap("Label") is ExecuteResult.Failure)
        assertEquals(0, screen.captures)
    }

    @Test
    fun settingOffDoesNotCheckScreenOrModel() = runTest {
        val screen = Screen(initial)
        val run = VisionLocateRun({ false }, { error("No model lookup") }, screen)
        assertTrue(run.locateAndTap("Label") is ExecuteResult.Failure)
        assertEquals(0, screen.captures)
        assertEquals(0, screen.taps)
    }

    @Test
    fun unchangedScreenIsFailedNotSuccess() = runTest {
        val screen = Screen(initial).apply { change = false }
        val logs = mutableListOf<String>()
        val run = VisionLocateRun({ true }, { ControlLocator { _, _, _, _ -> box } }, screen, logs::add)
        val result = run.locateAndTap("Label") as ExecuteResult.Failure
        assertFalse(result.transient)
        assertEquals(1, screen.taps)
        assertEquals(listOf("vision locate: attempt 1, result not-found"), logs)
    }

    @Test
    fun changedScreenDuringCaptureOrModelResponseNeverTaps() = runTest {
        val screen = Screen(initial)
        screen.afterCapture = { screen.snapshot = initial.copy(refusal = "Password") }
        val run = VisionLocateRun({ true }, { ControlLocator { _, _, _, _ -> error("Must not send") } }, screen)
        assertTrue(run.locateAndTap("Label") is ExecuteResult.Failure)
        assertEquals(0, screen.taps)
        val next = Screen(initial)
        val responseRace = VisionLocateRun({ true }, { ControlLocator { _, _, _, _ -> next.snapshot = initial.copy(signature = "changed"); box } }, next)
        assertTrue(responseRace.locateAndTap("Label") is ExecuteResult.Failure)
        assertEquals(0, next.taps)
    }

    @Test
    fun permissionRevokedDuringReplyNeverTaps() = runTest {
        val screen = Screen(initial)
        var enabled = true
        val run = VisionLocateRun({ enabled }, { ControlLocator { _, _, _, _ -> enabled = false; box } }, screen)
        assertTrue(run.locateAndTap("Label") is ExecuteResult.Failure)
        assertEquals(0, screen.taps)
    }
}
