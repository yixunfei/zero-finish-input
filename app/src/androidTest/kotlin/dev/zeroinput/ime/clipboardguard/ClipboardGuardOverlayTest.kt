package dev.zeroinput.ime.clipboardguard

import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.materialswitch.MaterialSwitch
import dev.zeroinput.ime.R
import dev.zeroinput.ime.ZeroInputApplication
import dev.zeroinput.ime.clipboardguard.ClipboardDeviceTestSupport as Device
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardGuardOverlayTest {
    @Test
    fun permissionExplanationPrecedesSettingsAndCancellationDoesNotEnableTheOverlay() = Device.withOverlayPermission(false) {
        val graph = (Device.context.applicationContext as ZeroInputApplication).graph
        val original = graph.clipboardGuardPreferences.options
        Device.onMain { graph.clipboardGuardPreferences.options = ClipboardGuardOptions() }
        val activity = Device.instrumentation.startActivitySync(Intent(Device.context, ClipboardGuardSettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as ClipboardGuardSettingsActivity
        val monitor = Device.instrumentation.addMonitor(IntentFilter(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { addDataScheme("package") },
            Instrumentation.ActivityResult(0, null), true)
        try {
            toggleOverlay(activity.window.decorView)
            Device.await { Device.findText(Device.context.getString(R.string.clipboard_guard_overlay_permission_reason)) != null }
            assertEquals(0, monitor.hits)
            assertFalse(graph.clipboardGuardPreferences.options.overlayReminder)
            Device.click(Device.context.getString(R.string.cancel))
            assertFalse(graph.clipboardGuardPreferences.options.overlayReminder)
            assertEquals(0, monitor.hits)
            toggleOverlay(activity.window.decorView)
            Device.click(Device.context.getString(R.string.clipboard_guard_overlay_continue))
            Device.await { monitor.hits == 1 }
            assertTrue(graph.clipboardGuardPreferences.options.overlayReminder)
            assertFalse(Settings.canDrawOverlays(Device.context))
            assertTrue(Device.overlayWindows().isEmpty())
            Device.onMain {
                descendants(activity.window.decorView).filterIsInstance<android.widget.TextView>()
                    .single { it.text == Device.context.getString(R.string.clipboard_guard_overlay_permission) }.performClick()
            }
            Device.await { Device.findText(Device.context.getString(R.string.clipboard_guard_overlay_permission_reason)) != null }
            assertEquals(1, monitor.hits)
            Device.click(Device.context.getString(R.string.cancel))
        } finally {
            Device.instrumentation.removeMonitor(monitor)
            Device.onMain { activity.finish(); graph.clipboardGuardPreferences.options = original }
        }
    }

    @Test
    fun queuedControlChangesCannotRestoreSettingsAfterLeavingThePage() {
        val graph = (Device.context.applicationContext as ZeroInputApplication).graph
        val original = graph.clipboardGuardPreferences.options
        val activity = Device.instrumentation.startActivitySync(Intent(Device.context, ClipboardGuardSettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as ClipboardGuardSettingsActivity
        try {
            Device.onMain {
                val screen = descendants(activity.window.decorView).filterIsInstance<ClipboardGuardSettingsView>().single()
                activity.finish()
                graph.clipboardGuardPreferences.options = ClipboardGuardOptions()
                screen.onOptions(ClipboardGuardOptions(listening = true, clearMode = ClipboardClearMode.AUTOMATIC))
                assertEquals(ClipboardGuardOptions(), graph.clipboardGuardPreferences.options)
            }
        } finally { Device.onMain { activity.finish(); graph.clipboardGuardPreferences.options = original } }
    }

    @Test
    fun overlaysReplaceOneWindowDismissWithoutResurfacingAndExpire() = withOverlay { overlay, options ->
        var state = ClipboardGuardState(options, ClipboardGuardStatus.CLEARED, eventId = "first")
        Device.onMain { overlay.render(state, true) }
        Device.await { Device.overlayWindows().size == 1 }
        state = state.copy(eventId = "second")
        Device.onMain { overlay.render(state, true) }
        Device.await { Device.overlayWindows().size == 1 }
        val close = Device.overlayWindows().single().root
            .findAccessibilityNodeInfosByText(Device.context.getString(R.string.clipboard_guard_dismiss)).single()
        assertTrue(close.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        Device.await { Device.overlayWindows().isEmpty() }
        Device.onMain { overlay.render(state, true) }
        assertTrue(Device.overlayWindows().isEmpty())
        Device.onMain { overlay.render(state.copy(eventId = "third"), true) }
        Device.await { Device.overlayWindows().size == 1 }
        Device.await { Device.overlayWindows().isEmpty() }
    }

    @Test
    fun permissionRevocationScreenOffAndOwnActivityRemoveTheWindow() = withOverlay { overlay, options ->
        val state = ClipboardGuardState(options, ClipboardGuardStatus.CLEARED, eventId = "allowed")
        Device.onMain { overlay.render(state, true) }
        Device.await { Device.overlayWindows().size == 1 }
        Device.setOverlayPermission("deny")
        Device.await { Device.overlayWindows().isEmpty() }
        Device.onMain { assertFalse(overlay.preview(options)) }
        Device.setOverlayPermission("allow")
        Device.onMain { overlay.render(state.copy(eventId = "screen"), true) }
        Device.await { Device.overlayWindows().size == 1 }
        try {
            Device.shell("input keyevent KEYCODE_SLEEP")
            Device.await { Device.overlayWindows().isEmpty() }
            Device.onMain { assertFalse(overlay.preview(options)) }
        } finally {
            Device.shell("input keyevent KEYCODE_WAKEUP")
            Device.shell("wm dismiss-keyguard")
        }
        Device.startSource()
        Device.onMain { overlay.render(state.copy(eventId = "foreground"), true) }
        Device.await { Device.overlayWindows().size == 1 }
        val activity = Device.instrumentation.startActivitySync(Intent(Device.context, ClipboardGuardSettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as ClipboardGuardSettingsActivity
        try {
            Device.await { Device.overlayWindows().isEmpty() }
            Device.onMain { overlay.render(state.copy(eventId = "own-page"), true) }
            assertTrue(Device.overlayWindows().isEmpty())
            Device.onMain { assertTrue(overlay.preview(options)) }
            Device.await { Device.overlayWindows().size == 1 }
        } finally { Device.onMain { activity.finish() } }
    }

    @Test
    fun disabledOverlayAndMissingPermissionCannotShowAPreview() = Device.withOverlayPermission(false) {
        val overlay = createOverlay()
        try {
            Device.onMain {
                assertFalse(overlay.preview(ClipboardGuardOptions(overlayReminder = true)))
                assertFalse(overlay.preview(ClipboardGuardOptions()))
            }
            assertTrue(Device.overlayWindows().isEmpty())
        } finally { Device.onMain { overlay.close() } }
    }

    private fun withOverlay(test: (ClipboardGuardOverlay, ClipboardGuardOptions) -> Unit) = Device.withOverlayPermission(true) {
        Device.startSource()
        val overlay = createOverlay()
        try {
            test(overlay, ClipboardGuardOptions(listening = true, overlayReminder = true, overlaySeconds = 3))
        } finally {
            Device.onMain { overlay.close() }
            Device.findText("Close public fixture")?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun createOverlay(): ClipboardGuardOverlay {
        var overlay: ClipboardGuardOverlay? = null
        Device.onMain { overlay = ClipboardGuardOverlay(Device.context) { ClipboardGuardState() } }
        return checkNotNull(overlay)
    }

    private fun toggleOverlay(root: View) = Device.onMain {
        descendants(root).filterIsInstance<MaterialSwitch>()
            .single { it.text == Device.context.getString(R.string.clipboard_guard_overlay) }.performClick()
    }

    private fun descendants(view: View): List<View> = if (view is ViewGroup) {
        listOf(view) + (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else listOf(view)
}
