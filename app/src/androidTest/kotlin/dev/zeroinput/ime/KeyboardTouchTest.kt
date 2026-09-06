package dev.zeroinput.ime

import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.zeroinput.engine.api.ChineseKeyboardLayout
import dev.zeroinput.ime.ui.KeyboardAction
import dev.zeroinput.ime.ui.KeyboardPanel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardTouchTest {
    @Test fun everyRowHasContinuousTouchTargetsAndDispatchesBeforeReleaseReturns() = onMain {
        for (layout in ChineseKeyboardLayout.entries) {
            val panel = panel(layout)
            val actions = mutableListOf<KeyboardAction>()
            panel.onAction = { actions += it }
            var bottom = 0
            for (rowIndex in 0 until panel.childCount) {
                val row = panel.getChildAt(rowIndex) as ViewGroup
                assertEquals(bottom, row.top)
                bottom = row.bottom
                var right = 0
                for (index in 0 until row.childCount) {
                    val key = row.getChildAt(index)
                    assertEquals("No unhandled horizontal gap", right, key.left)
                    right = key.right
                    assertEquals(0, key.top)
                    assertEquals(row.height, key.bottom)
                }
                assertEquals(panel.width, right)
            }
            assertEquals(panel.height, bottom)
            val row = panel.getChildAt(0) as ViewGroup
            val key = row.getChildAt(1)
            tap(panel, key.left.toFloat(), row.top + row.height / 2f)
            assertEquals("UP must deliver input without waiting for another message or frame", 1, actions.size)
        }
    }

    @Test fun overlappingPointersDeliverBothKeysAndCancellationDeliversNeither() = onMain {
        val panel = panel(ChineseKeyboardLayout.FULL)
        val actions = mutableListOf<KeyboardAction>()
        panel.onAction = { actions += it }
        val row = panel.getChildAt(0) as ViewGroup
        val first = row.getChildAt(0)
        val second = row.getChildAt(1)
        val x0 = first.left + first.width / 2f
        val x1 = second.left + second.width / 2f
        val y = row.height / 2f
        pointers(panel, MotionEvent.ACTION_DOWN, listOf(0 to x0), y)
        pointers(panel, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(0 to x0, 1 to x1), y)
        pointers(panel, MotionEvent.ACTION_POINTER_UP, listOf(0 to x0, 1 to x1), y)
        pointers(panel, MotionEvent.ACTION_UP, listOf(1 to x1), y)
        assertEquals(listOf(KeyboardAction.Text("q"), KeyboardAction.Text("w")), actions)
        actions.clear()
        pointers(panel, MotionEvent.ACTION_DOWN, listOf(0 to x0), y)
        panel.cancelPendingGestures()
        pointers(panel, MotionEvent.ACTION_UP, listOf(0 to x0), y)
        assertTrue(actions.isEmpty())
        pointers(panel, MotionEvent.ACTION_DOWN, listOf(0 to x0), y)
        pointers(panel, MotionEvent.ACTION_CANCEL, listOf(0 to x0), y)
        pointers(panel, MotionEvent.ACTION_UP, listOf(0 to x0), y)
        assertTrue(actions.isEmpty())
    }

    @Test fun symbolDigitsRemainLiteralInNineKeyMode() = onMain {
        val panel = panel(ChineseKeyboardLayout.NINE_KEY)
        val actions = mutableListOf<KeyboardAction>()
        panel.onAction = { actions += it }
        val bottomRow = panel.getChildAt(3) as ViewGroup
        bottomRow.getChildAt(0).performClick()
        measure(panel)
        val digitRow = panel.getChildAt(0) as ViewGroup
        digitRow.getChildAt(3).performClick()
        assertEquals(listOf(KeyboardAction.LiteralText("4")), actions)
    }

    private fun tap(panel: View, x: Float, y: Float) {
        pointers(panel, MotionEvent.ACTION_DOWN, listOf(0 to x), y)
        pointers(panel, MotionEvent.ACTION_UP, listOf(0 to x), y)
    }

    private fun pointers(panel: View, action: Int, points: List<Pair<Int, Float>>, y: Float) {
        val properties = points.map { (id, _) -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coordinates = points.map { (_, x) -> MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f } }
        val now = SystemClock.uptimeMillis()
        MotionEvent.obtain(now, now, action, points.size, properties.toTypedArray(), coordinates.toTypedArray(),
            0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0).also {
            panel.dispatchTouchEvent(it)
            it.recycle()
        }
    }

    private fun panel(layout: ChineseKeyboardLayout): KeyboardPanel {
        val context = ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().targetContext, R.style.Theme_ZeroInput_InputMethod)
        return KeyboardPanel(context).apply { setKeyboardLayout(layout); measure(this) }
    }

    private fun measure(view: View) {
        val width = (320 * view.resources.displayMetrics.density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun onMain(action: () -> Unit) {
        var result: Result<Unit>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(action) }
        checkNotNull(result).getOrThrow()
    }
}
