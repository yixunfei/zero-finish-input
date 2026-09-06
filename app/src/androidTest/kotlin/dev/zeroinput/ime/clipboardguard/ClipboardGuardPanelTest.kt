package dev.zeroinput.ime.clipboardguard

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.materialswitch.MaterialSwitch
import dev.zeroinput.ime.R
import dev.zeroinput.ime.ui.KeyboardPanel
import dev.zeroinput.ime.ui.ZeroInputView
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardGuardPanelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun separateControlsFitSmallAndLandscapeScreensBothThemesAndLanguages() = onMain {
        for (night in listOf(false, true)) for (locale in listOf(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE)) {
            for ((width, height) in listOf(320 to 600, 800 to 320)) {
                val context = themed(locale, night)
                val view = ClipboardGuardSettingsView(context)
                var changes = 0
                view.onOptions = { changes++ }
                view.render(ClipboardGuardOptions(), ClipboardGuardState(), notificationAllowed = false)
                measure(view, width, height)
                assertEquals(0, changes)
                val switches = descendants(view).filterIsInstance<MaterialSwitch>()
                assertEquals(4, switches.size)
                assertTrue(switches.none { it.isChecked })
                val radios = descendants(view).filterIsInstance<RadioButton>()
                assertEquals(3, radios.size)
                assertEquals(1, radios.count { it.isChecked })
                switches.first().performClick()
                assertEquals(1, changes)
                val enabled = ClipboardGuardOptions(listening = true, notificationReminder = true,
                    clearMode = ClipboardClearMode.CONFIRM, authenticate = true)
                view.render(enabled, ClipboardGuardState(enabled, ClipboardGuardStatus.CHANGED, ClipboardGuardTicket("fixture", 1)), false)
                measure(view, width, height)
                assertFalse(radios.single { it.text == context.getString(R.string.clipboard_guard_clear_automatic) }.isEnabled)
                assertTrue(radios.single { it.text == context.getString(R.string.clipboard_guard_clear_confirm) }.isChecked)
                saveFixture(view, "guard-${locale.language}-$night-$width-top.png")
                descendants(view).filterIsInstance<ScrollView>().single().scrollTo(0, view.height * 10)
                saveFixture(view, "guard-${locale.language}-$night-$width-bottom.png")
                verifyText(view)
            }
        }
    }

    @Test
    fun clipboardEventsKeepTheReminderAndKeyboardDimensionsStable() = onMain {
        for (locale in listOf(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE)) {
            val view = ZeroInputView(themed(locale, false))
            view.renderClipboardGuard(false, false)
            measureKeyboard(view)
            val disabledHeight = view.height
            view.renderClipboardGuard(true, false)
            measureKeyboard(view)
            val enabledHeight = view.height
            assertEquals(disabledHeight + dp(view, 48), enabledHeight)
            val keyboard = descendants(view).filterIsInstance<KeyboardPanel>().single()
            val keyboardTop = keyboard.top
            val keyboardHeight = keyboard.height
            view.renderClipboardGuard(true, true)
            measureKeyboard(view)
            assertEquals(enabledHeight, view.height)
            assertEquals(keyboardTop, keyboard.top)
            assertEquals(keyboardHeight, keyboard.height)
            verifyText(view)
            saveFixture(view, "guard-keyboard-${locale.language}.png")
            view.renderClipboardGuard(false, true)
            measureKeyboard(view)
            assertEquals(disabledHeight, view.height)
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun guardComponentsArePrivateAndNotificationPermissionIsExplicit() {
        val context = instrumentation.targetContext
        for (component in listOf(ClipboardClearActivity::class.java, ClipboardGuardSettingsActivity::class.java)) {
            val info = context.packageManager.getActivityInfo(ComponentName(context, component), 0)
            assertFalse(info.exported)
        }
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions.orEmpty())
    }

    @Test
    fun launchingAnExpiredClearRequestFinishesWithoutAuthenticationOrCleanup() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, ClipboardClearActivity::class.java)
            .putExtra(ClipboardClearActivity.EXTRA_TICKET, "expired-public-fixture")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ClipboardClearActivity
        onMain {
            assertTrue(activity.isFinishing)
            assertEquals(null, activity.intent.extras)
        }
    }

    private fun themed(locale: Locale, night: Boolean): ContextThemeWrapper {
        val target = instrumentation.targetContext
        val configuration = Configuration(target.resources.configuration).apply {
            setLocale(locale)
            uiMode = Configuration.UI_MODE_TYPE_NORMAL or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            fontScale = 1.3f
        }
        return ContextThemeWrapper(target.createConfigurationContext(configuration), R.style.Theme_ZeroInput)
    }

    private fun verifyText(view: View) {
        descendants(view).filterIsInstance<TextView>().filter { it.width > 0 && it.text.isNotEmpty() }.forEach { text ->
            val layout = text.layout ?: return@forEach
            for (line in 0 until layout.lineCount) {
                assertEquals("Fixture label must not be truncated: ${text.text}", 0, layout.getEllipsisCount(line))
                // Android's line width includes trailing spaces beyond the wrapped line.
                assertTrue("Fixture label must fit: ${text.text}",
                    layout.getLineMax(line) <= text.width - text.compoundPaddingLeft - text.compoundPaddingRight + 1)
            }
        }
    }

    private fun measure(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(dp(view, width), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(view, height), View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun measureKeyboard(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(dp(view, 320), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun saveFixture(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            descendants(view).forEach { it.viewTreeObserver.dispatchOnPreDraw(); it.jumpDrawablesToCurrentState() }
            view.setBackgroundColor(view.context.getColor(R.color.zero_surface))
            view.draw(Canvas(bitmap))
            File(checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)), name).outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }

    private fun descendants(view: View): List<View> = if (view is ViewGroup) {
        listOf(view) + (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else listOf(view)

    private fun dp(view: View, value: Int) = (value * view.resources.displayMetrics.density).toInt()

    private fun onMain(action: () -> Unit) {
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        checkNotNull(result).getOrThrow()
    }
}
