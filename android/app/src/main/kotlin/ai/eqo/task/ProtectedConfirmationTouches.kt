package ai.eqo.task

import android.app.AlertDialog
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** Protect the whole approval/resume card and both button click paths after show(). */
internal fun protectConfirmationDialog(dialog: AlertDialog) {
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).filterTouchesWhenObscured = true
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).filterTouchesWhenObscured = true
    protectConfirmationTouches(dialog.findViewById(android.R.id.content))
}

/** Reject touch-point obscuration before child dispatch; overlays elsewhere do not block. */
internal fun protectConfirmationTouches(view: View) {
    view.filterTouchesWhenObscured = true
    val parent = view.parent as ViewGroup
    val index = parent.indexOfChild(view)
    val layout = view.layoutParams
    parent.removeView(view)
    val protected =
        object : FrameLayout(view.context) {
            private val guard = ConfirmationTouchGuard()

            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (!guard.accepts(event.flags, event.actionMasked == MotionEvent.ACTION_DOWN)) {
                    // Cancel an already-pressed child too: rejecting just UP/MOVE can leave a
                    // clean later UP capable of clicking. Never forward the obscured event.
                    val cancel =
                        MotionEvent.obtain(
                            event.downTime,
                            event.eventTime,
                            MotionEvent.ACTION_CANCEL,
                            event.x,
                            event.y,
                            0,
                        )
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    return true
                }
                return super.dispatchTouchEvent(event)
            }
        }
    protected.addView(view, FrameLayout.LayoutParams(layout.width, layout.height))
    parent.addView(protected, index, layout)
}
