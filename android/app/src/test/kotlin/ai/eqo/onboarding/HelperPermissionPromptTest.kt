package ai.eqo.onboarding

import ai.eqo.adb.pairing.StepSignal
import ai.eqo.adb.pairing.StepSignalException
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import moe.shizuku.server.IShizukuApplication
import moe.shizuku.server.IShizukuService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants.REQUEST_PERMISSION_REPLY_ALLOWED
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HelperPermissionPromptTest {
    private val controller = Robolectric.buildActivity(Activity::class.java)
    private val transport = Executors.newSingleThreadExecutor()
    private lateinit var prompt: HelperPermissionPrompt
    private var allowed = false
    private var revocations = 0
    private var requestGate: CountDownLatch? = null
    private val requestStarted = CountDownLatch(1)
    private val transportOnMain = mutableListOf<Boolean>()
    private var answer: Boolean? = null
    private lateinit var callback: IShizukuApplication

    @Before
    fun setup() {
        controller.setup().visible().windowFocusChanged(true)
        callback = field("SHIZUKU_APPLICATION").get(null) as IShizukuApplication
        field("serverUid").setInt(null, Binder.getCallingUid())
        field("permissionGranted").setBoolean(null, false)
        val service =
            Proxy.newProxyInstance(
                IShizukuService::class.java.classLoader,
                arrayOf(IShizukuService::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "requestPermission" -> {
                        transportOnMain.add(Looper.myLooper() == Looper.getMainLooper())
                        requestStarted.countDown()
                        requestGate?.await(5, TimeUnit.SECONDS)
                        callback.showPermissionConfirmation(
                            Process.myUid(),
                            Process.myPid(),
                            "ai.eqo.app",
                            args[0] as Int,
                        )
                        null
                    }
                    "dispatchPermissionConfirmationResult" -> {
                        transportOnMain.add(Looper.myLooper() == Looper.getMainLooper())
                        allowed = (args[3] as Bundle).getBoolean(REQUEST_PERMISSION_REPLY_ALLOWED)
                        callback.dispatchRequestPermissionResult(args[2] as Int, args[3] as Bundle)
                        null
                    }
                    "checkSelfPermission" -> allowed
                    "updateFlagsForUid" -> {
                        transportOnMain.add(Looper.myLooper() == Looper.getMainLooper())
                        allowed = false
                        revocations++
                        null
                    }
                    else -> null
                }
            } as IShizukuService
        field("service").set(null, service)
        field("binder").set(null, Binder())
        prompt = HelperPermissionPrompt(controller.get(), transport)
    }

    @After
    fun cleanup() {
        prompt.close()
        shadowOf(Looper.getMainLooper()).idle()
        drainTransport()
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
        transport.shutdown()
        assertTrue(transport.awaitTermination(5, TimeUnit.SECONDS))
        field("service").set(null, null)
        field("binder").set(null, null)
        field("serverUid").setInt(null, -1)
        field("permissionGranted").setBoolean(null, false)
    }

    @Test
    fun serverRequestShowsProtectedDialogAndAllowReturnsGrant() {
        val dialog = request()
        assertEquals("Allow", dialog.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
        assertEquals("Don't allow", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString())
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).filterTouchesWhenObscured)
        assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).filterTouchesWhenObscured)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        drainTransport()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(true, answer)
        assertTrue(prompt.isGranted())
        assertEquals(listOf(false, false), transportOnMain)
        prompt.close(authorized = true)
        prompt.close()
        assertEquals(0, revocations)
    }

    @Test
    fun denyAndCancellationNeverGrant() {
        val dialog = request()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        drainTransport()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, answer)
        assertFalse(allowed)
        assertEquals(PackageManager.PERMISSION_DENIED, Shizuku.checkSelfPermission())
    }

    @Test
    fun backCancelsAndClosedScreenAnswersDeniedWithoutRequesting() {
        request().cancel()
        shadowOf(Looper.getMainLooper()).idle()
        drainTransport()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, answer)
        prompt.close()
        answer = null
        prompt.request { answer = it }
        assertEquals(false, answer)
        assertFalse(allowed)
    }

    @Test
    fun staleGrantedCacheCannotOverrideDeniedServerPermission() {
        field("permissionGranted").setBoolean(null, true)
        assertEquals(PackageManager.PERMISSION_DENIED, Shizuku.checkSelfPermission())
    }

    @Test
    fun revokedPermissionCannotPassBinderHealthEvenWhenBinderIsAlive() {
        val error = assertThrows(StepSignalException::class.java) { StudyHelperHooks(prompt).checkBinder() }
        assertEquals(StepSignal.HELPER_NOT_AUTHORIZED, error.signal)
    }

    @Test
    fun obscuredTouchPoisonsGestureAndCannotApprove() {
        val dialog = request()
        val protected = dialog.findViewById<View>(android.R.id.content).parent as View
        val down = event(MotionEvent.ACTION_DOWN, MotionEvent.FLAG_WINDOW_IS_OBSCURED)
        val up = event(MotionEvent.ACTION_UP, 0)
        try {
            assertTrue(protected.dispatchTouchEvent(down))
            assertTrue(protected.dispatchTouchEvent(up))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(answer)
            assertFalse(allowed)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    @Test
    fun closingUnansweredPromptRevokesBeforeUiQueueAndDismisses() {
        val dialog = request()
        prompt.close()
        drainTransport()
        assertEquals(1, revocations)
        assertEquals(false, answer)
        assertFalse(allowed)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(dialog.isShowing)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertFalse(allowed)
    }

    @Test
    fun lifecycleCloseOrdersRevokeAfterDelayedRequestWithoutBlockingMain() {
        val gate = CountDownLatch(1)
        requestGate = gate
        prompt.request { answer = it }
        assertTrue(requestStarted.await(5, TimeUnit.SECONDS))
        prompt.close()
        assertEquals(false, answer)
        assertEquals(0, revocations)
        gate.countDown()
        shadowOf(Looper.getMainLooper()).idle()
        drainTransport()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, revocations)
        assertFalse(allowed)
        assertEquals(listOf(false, false), transportOnMain)
    }

    @Test
    fun workerCloseAwaitsAlreadyQueuedLifecycleRevocation() {
        val gate = CountDownLatch(1)
        requestGate = gate
        prompt.request { answer = it }
        assertTrue(requestStarted.await(5, TimeUnit.SECONDS))
        prompt.close()
        val closing = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val worker =
            Thread {
                closing.countDown()
                prompt.close()
                finished.countDown()
            }
        worker.start()
        assertTrue(closing.await(5, TimeUnit.SECONDS))
        assertFalse(finished.await(50, TimeUnit.MILLISECONDS))
        gate.countDown()
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        worker.join()
        assertEquals(1, revocations)
        assertFalse(allowed)
    }

    @Test
    fun immediateRetryUsesSameQueueAfterOldRequestAndRevocation() {
        val gate = CountDownLatch(1)
        requestGate = gate
        prompt.request { answer = it }
        assertTrue(requestStarted.await(5, TimeUnit.SECONDS))
        prompt.close()
        val retry = HelperPermissionPrompt(controller.get(), transport)
        var retryAnswer: Boolean? = null
        retry.request { retryAnswer = it }
        gate.countDown()
        shadowOf(Looper.getMainLooper()).idle()
        drainTransport()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, revocations)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        drainTransport()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(true, retryAnswer)
        assertTrue(retry.isGranted())
        retry.close(authorized = true)
        drainTransport()
        assertEquals(1, revocations)
    }

    private fun request(): AlertDialog {
        prompt.request { answer = it }
        shadowOf(Looper.getMainLooper()).idle()
        drainTransport()
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowAlertDialog.getLatestAlertDialog()
    }

    private fun drainTransport() {
        transport.submit {}.get(5, TimeUnit.SECONDS)
    }

    private fun field(name: String) = Shizuku::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun event(
        action: Int,
        flags: Int,
    ): MotionEvent =
        MotionEvent.obtain(
            0L,
            1L,
            action,
            1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0 }),
            arrayOf(
                MotionEvent.PointerCoords().apply {
                    x = 1f
                    y = 1f
                },
            ),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            flags,
        )
}
