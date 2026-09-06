package dev.zeroinput.ime.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatTextView

/** Immediate, rectangular touch target; Android splits different pointers between keys. */
internal class KeyboardKeyView(context: Context, baseColor: Int, pressedColor: Int, lineColor: Int) : AppCompatTextView(context) {
    private var holding = false

    init {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        isSoundEffectsEnabled = false
        letterSpacing = 0f
        setPadding(0, 0, 0, 0)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), tile(pressedColor, lineColor))
            addState(intArrayOf(), tile(baseColor, lineColor))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { holding = true; isPressed = true }
            MotionEvent.ACTION_MOVE -> if (!inside(event)) cancelTouch()
            MotionEvent.ACTION_UP -> {
                val click = holding && inside(event)
                cancelTouch()
                // View's posted PerformClick can sit behind another frame and queued keys.
                if (click) performClick()
            }
            MotionEvent.ACTION_CANCEL -> cancelTouch()
        }
        return true
    }

    fun cancelTouch() { holding = false; isPressed = false }

    override fun getAccessibilityClassName(): CharSequence = android.widget.Button::class.java.name

    private fun inside(event: MotionEvent): Boolean = event.x >= 0 && event.y >= 0 && event.x < width && event.y < height

    private fun tile(color: Int, line: Int) = GradientDrawable().apply {
        setColor(color)
        setStroke(1, line)
    }
}
