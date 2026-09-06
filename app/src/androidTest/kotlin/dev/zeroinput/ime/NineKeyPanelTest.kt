package dev.zeroinput.ime

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.zeroinput.engine.api.Candidate
import dev.zeroinput.engine.api.ChineseKeyboardLayout
import dev.zeroinput.engine.api.EngineCapability
import dev.zeroinput.engine.api.EngineDescriptor
import dev.zeroinput.engine.api.EngineSnapshot
import dev.zeroinput.engine.api.InputLanguage
import dev.zeroinput.ime.core.InputSessionState
import dev.zeroinput.ime.ui.InputEngineStatus
import dev.zeroinput.ime.ui.KeyboardPanel
import dev.zeroinput.ime.ui.ZeroInputView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NineKeyPanelTest {
    @Test fun nineKeyReadingsAndToolbarFitSmallLargeAndLandscapeLayouts() = onMain {
        for (width in listOf(320, 411, 800)) for (night in listOf(false, true)) {
            val panel = panel(width, night)
            val fixture = fixture()
            panel.renderSession(fixture)
            panel.renderActiveLayout(ChineseKeyboardLayout.NINE_KEY)
            panel.renderEngineStatus(InputEngineStatus.READY)
            measure(panel, width)
            val originalHeight = panel.height
            val texts = visible(panel).filterIsInstance<TextView>().filter { it.text.isNotEmpty() }
            for (text in texts) {
                if (text.layout == null) continue
                val available = text.width - text.compoundPaddingLeft - text.compoundPaddingRight
                assertTrue("Visible keyboard labels must not overflow", (0 until text.layout.lineCount).all {
                    text.layout.getLineWidth(it) <= available + 1
                })
            }
            assertTrue(texts.any { it.text.toString() == "4 GHI" })
            assertTrue(texts.any { it.contentDescription == panel.context.getString(dev.zeroinput.ime.ui.R.string.select_pinyin_reading, "ni") })
            saveFixture(panel, "nine-${width}-${if (night) "dark" else "light"}.png")
            panel.renderSession(fixture.copy(snapshot = EngineSnapshot.Empty))
            measure(panel, width)
            assertEquals("Clearing readings cannot resize the keyboard", originalHeight, panel.height)
            panel.renderSession(fixture.copy(language = InputLanguage.ENGLISH, snapshot = EngineSnapshot.Empty))
            measure(panel, width)
            assertTrue(visible(panel).any { it.contentDescription == "q" })
            assertFalse(visible(panel).any { it.contentDescription == "4 GHI" })
            panel.renderSession(fixture.copy(engineDescriptor = fixture.engineDescriptor?.copy(capabilities = emptySet())))
            measure(panel, width)
            assertTrue("Unsupported engines keep the full keyboard", visible(panel).any { it.contentDescription == "q" })
            panel.cancelPendingGestures()
        }
    }

    private fun fixture() = InputSessionState(
        snapshot = EngineSnapshot("64426", "ni hao", listOf(Candidate("a", "你好"), Candidate("b", "你"),
            Candidate("c", "拟好"), Candidate("d", "泥")), readings = listOf("ni", "mi", "ming", "nin")),
        engineDescriptor = EngineDescriptor("fixture", "Fixture", "1", setOf(InputLanguage.CHINESE),
            capabilities = EngineCapability.entries.toSet()),
    )

    private fun panel(width: Int, night: Boolean): ZeroInputView {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(target.resources.configuration).apply {
            screenWidthDp = width
            orientation = if (width == 800) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            uiMode = Configuration.UI_MODE_TYPE_NORMAL or if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return ZeroInputView(ContextThemeWrapper(target.createConfigurationContext(configuration), R.style.Theme_ZeroInput_InputMethod))
    }

    private fun saveFixture(view: View, name: String) {
        // Only constructed public fixtures are drawn; never capture an editor or device screen.
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            visible(view).forEach { it.viewTreeObserver.dispatchOnPreDraw() }
            view.draw(Canvas(image))
            val root = checkNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null))
            File(root, name).outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { image.recycle() }
    }

    private fun measure(view: View, width: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec((width * view.resources.displayMetrics.density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun visible(view: View): List<View> = if (view.visibility != View.VISIBLE) emptyList() else if (view is ViewGroup) {
        listOf(view) + (0 until view.childCount).flatMap { visible(view.getChildAt(it)) }
    } else listOf(view)

    private fun onMain(action: () -> Unit) {
        var result: Result<Unit>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(action) }
        checkNotNull(result).getOrThrow()
    }
}
