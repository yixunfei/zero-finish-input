package dev.zeroinput.ime.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatTextView

/** A recycled row cannot turn a gesture begun on another expression into a selection. */
internal class ExpressionCellView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AppCompatTextView(context, attrs) {
    var bindingRevision = -1L
    private var gestureRevision = -1L
    private var gesturePending = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            gestureRevision = bindingRevision
            gesturePending = true
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            gesturePending = false
        }
        return super.dispatchTouchEvent(event)
    }

    override fun performClick(): Boolean {
        val current = !gesturePending || gestureRevision == bindingRevision
        gesturePending = false
        return current && super.performClick()
    }

    override fun performLongClick(): Boolean =
        (!gesturePending || gestureRevision == bindingRevision) && super.performLongClick()
}
