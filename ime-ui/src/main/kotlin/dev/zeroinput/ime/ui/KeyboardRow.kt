package dev.zeroinput.ime.ui

import android.content.Context
import android.view.ViewGroup

/** Cumulative pixel boundaries ensure fractional key weights leave no untouchable pixels. */
internal class KeyboardRow(context: Context, private val weights: List<Float>) : ViewGroup(context) {
    init { isMotionEventSplittingEnabled = true }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val total = weights.sumOf(Float::toDouble)
        var accumulated = 0.0
        var left = 0
        for (index in 0 until childCount) {
            accumulated += weights[index]
            val right = if (index == childCount - 1) width else (width * accumulated / total).toInt()
            getChildAt(index).measure(MeasureSpec.makeMeasureSpec(right - left, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            left = right
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var x = 0
        for (index in 0 until childCount) {
            val key = getChildAt(index)
            key.layout(x, 0, x + key.measuredWidth, height)
            x += key.measuredWidth
        }
    }
}
