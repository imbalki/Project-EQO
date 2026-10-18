// Origin: EQO-authored regression for production event delivery and drag-safe placement.
package ai.eqo.handle

import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.handle.EdgeHandleOverlay
import ai.eqo.accessibility.handle.HandlePreferences
import ai.eqo.accessibility.handle.HandleWindowGuard
import android.graphics.Rect
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.edit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowAccessibilityNodeInfo

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [HandlePlacementDeliveryTest.FocusedNodeShadow::class])
class HandlePlacementDeliveryTest {
    private lateinit var service: EQOAccessibilityService
    private lateinit var overlay: EdgeHandleOverlay
    private lateinit var handle: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var input: AccessibilityNodeInfo
    private var topInset = 0

    @Before
    fun prepare() {
        service = Robolectric.buildService(EQOAccessibilityService::class.java).create().get()
        val preferences = HandlePreferences(service)
        preferences.preferences.edit { clear() }
        preferences.enabled = true
        preferences.hintShown = true
        val manager = service.getSystemService(WindowManager::class.java)
        topInset =
            manager.currentWindowMetrics.windowInsets
                .getInsets(WindowInsets.Type.systemBars())
                .top
        overlay = EdgeHandleOverlay(service, manager)
        service.javaClass
            .getDeclaredField("edgeHandle")
            .apply { isAccessible = true }
            .set(service, overlay)
        overlay.setProbeAvailable(true)
        shadowOf(Looper.getMainLooper()).idle()
        handle =
            overlay.javaClass
                .getDeclaredField("view")
                .apply { isAccessible = true }
                .get(overlay) as View
        // Give the real drag listener a measured grip; no layout helper is called directly by these tests.
        handle.layout(0, 0, 48, 72)
        params = handle.layoutParams as WindowManager.LayoutParams
        input = AccessibilityNodeInfo.obtain()
        shadowOf(service).setRootInActiveWindow(input)
    }

    @After
    fun cleanup() {
        overlay.destroy()
        HandleWindowGuard.shared.clear()
        HandlePreferences(service).preferences.edit { clear() }
    }

    @Test
    fun `focus event delivered to production service refits grip around changed input`() {
        verifyEventRefits(AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }

    @Test
    fun `window event delivered to production service refits grip around changed input`() {
        verifyEventRefits(AccessibilityEvent.TYPE_WINDOWS_CHANGED)
    }

    @Test
    fun `drag listener avoids focused input and hides if neither side has room`() {
        params.y = 0
        setInput(150, 350)
        dragTo(200f)
        assertEquals(View.VISIBLE, handle.visibility)
        assertEquals(150 - handle.height, params.y)
        assertTrue(params.y + handle.height <= 150)
        setInput(0, Int.MAX_VALUE - topInset)
        dragTo(250f)
        assertEquals(View.INVISIBLE, handle.visibility)
        assertEquals(null, HandleWindowGuard.shared.bounds)
    }

    @Test
    fun `drag without collision still updates window position`() {
        params.y = 0
        setInput(0, 100)
        dragTo(250f)
        assertEquals(View.VISIBLE, handle.visibility)
        assertEquals(250, params.y)
        assertEquals(250, (handle.layoutParams as WindowManager.LayoutParams).y)
    }

    private fun verifyEventRefits(type: Int) {
        params.y = 200
        setInput(150, 350)
        val event = AccessibilityEvent.obtain(type)
        service.onAccessibilityEvent(event)
        event.recycle()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.VISIBLE, handle.visibility)
        assertEquals(150 - handle.height, params.y)
        // A window/focus change can remove every safe slot, then expose space again.
        setInput(0, Int.MAX_VALUE - topInset)
        val blocked = AccessibilityEvent.obtain(type)
        service.onAccessibilityEvent(blocked)
        blocked.recycle()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.INVISIBLE, handle.visibility)
        assertEquals(null, HandleWindowGuard.shared.bounds)
        setInput(150, 350)
        val restored = AccessibilityEvent.obtain(type)
        service.onAccessibilityEvent(restored)
        restored.recycle()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.VISIBLE, handle.visibility)
    }

    private fun setInput(
        top: Int,
        bottom: Int,
    ) {
        input.setBoundsInScreen(Rect(0, top + topInset, 500, bottom + topInset))
    }

    private fun dragTo(delta: Float) {
        listOf(MotionEvent.ACTION_DOWN to 0f, MotionEvent.ACTION_MOVE to delta).forEach { (action, y) ->
            val event = MotionEvent.obtain(0, 0, action, 10f, y, 0)
            handle.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    // Robolectric does not implement findFocus; only that platform lookup is faked.
    // The production service, overlay, input bounds, event dispatch and drag listener are exercised unchanged.
    @Implements(AccessibilityNodeInfo::class)
    class FocusedNodeShadow : ShadowAccessibilityNodeInfo() {
        @RealObject
        private lateinit var node: AccessibilityNodeInfo

        @Implementation
        fun findFocus(type: Int): AccessibilityNodeInfo? = if (type == AccessibilityNodeInfo.FOCUS_INPUT) node else null
    }
}
