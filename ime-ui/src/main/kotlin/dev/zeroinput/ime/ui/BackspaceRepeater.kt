package dev.zeroinput.ime.ui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

internal class BackspaceRepeater(
    private val key: View,
    private val clearComposition: () -> Boolean,
    private val backspace: () -> Unit,
) : View.OnTouchListener {
    private val handler = Handler(Looper.getMainLooper())
    private var holding = false
    private var longPressed = false
    private val repeat = object : Runnable {
        override fun run() {
            if (!holding) return
            backspace()
            if (holding) handler.postDelayed(this, REPEAT_MILLIS)
        }
    }
    private val start = Runnable {
        if (holding) {
            longPressed = true
            if (!clearComposition() && holding) repeat.run()
        }
    }

    init {
        key.setOnTouchListener(this)
        key.setOnLongClickListener {
            if (!clearComposition()) backspace()
            true
        }
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancel()
                holding = true
                longPressed = false
                view.isPressed = true
                handler.postDelayed(start, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.x < 0 || event.y < 0 || event.x >= view.width || event.y >= view.height) cancel()
            }
            MotionEvent.ACTION_UP -> {
                val click = holding && !longPressed
                cancel()
                if (click) view.performClick()
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> cancel()
        }
        return true
    }

    fun cancel() {
        holding = false
        handler.removeCallbacks(start)
        handler.removeCallbacks(repeat)
        key.isPressed = false
    }

    private companion object { const val REPEAT_MILLIS = 65L }
}
