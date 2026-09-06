package dev.zeroinput.ime

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InputWindowThemeTest {
    @Test fun inputWindowLeavesTheApplicationAboveTheKeyboardVisibleInBothThemes() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        for (mode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val config = Configuration(target.resources.configuration).apply {
                uiMode = uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or mode
            }
            val context = ContextThemeWrapper(target.createConfigurationContext(config), R.style.Theme_ZeroInput_InputMethod)
            val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground, android.R.attr.backgroundDimEnabled))
            try {
                val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                val background = attributes.getDrawable(0)
                background?.setBounds(0, 0, 8, 8)
                background?.draw(Canvas(bitmap))
                assertEquals("IME window background must not cover the host application", 0, Color.alpha(bitmap.getPixel(4, 4)))
                assertFalse("IME must not dim the host application", attributes.getBoolean(1, false))
                bitmap.recycle()
            } finally { attributes.recycle() }
        }
    }
}
