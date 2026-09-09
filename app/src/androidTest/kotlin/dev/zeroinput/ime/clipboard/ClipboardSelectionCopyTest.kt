package dev.zeroinput.ime.clipboard

import android.content.ComponentName
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import dev.zeroinput.ime.R
import dev.zeroinput.ime.ZeroInputApplication
import dev.zeroinput.ime.clipboardguard.AndroidSystemClipboard
import dev.zeroinput.ime.clipboardguard.ClipboardDeviceTestSupport as Device
import dev.zeroinput.ime.testing.InputFixtureActivity
import dev.zeroinput.security.AuthenticationGrant
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ClipboardSelectionCopyTest {
    @Test
    fun keyboardSelectionSavesAndPastesOnlyAfterAuthenticationAndExplicitConfirmation() {
        val pin = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("clipboardTestPin")
        assumeTrue("Requires a disposable emulator with an explicitly configured test PIN", pin == "2468")
        val graph = (Device.context.applicationContext as ZeroInputApplication).graph
        assumeTrue("Use an empty fixture vault", graph.secureClipboard.count() == 0)
        val enabled = graph.settings.secureClipboardEnabled
        val clipboard = AndroidSystemClipboard(Device.context)
        val activity = Device.instrumentation.startActivitySync(Intent(Device.context, InputFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as InputFixtureActivity
        val reviewMonitor = Device.instrumentation.addMonitor(ClipboardSelectionImportActivity::class.java.name, null, false)
        var createdId: String? = null
        try {
            Device.showFixtureKeyboard(activity)
            val baseline = clipboard.timestamp()
            Device.onMain { activity.editor.setText("public selection fixture"); activity.editor.selectAll() }
            Device.instrumentation.waitForIdleSync()
            openPanel()
            Device.click(Device.context.getString(dev.zeroinput.ime.ui.R.string.secure_clipboard_copy_selection))
            val action = Device.context.getString(if (enabled) R.string.clipboard_import_authenticate else R.string.enable_secure_clipboard)
            Device.await { Device.findText(action)?.window?.isFocused == true }
            assertNull(Device.findText("public selection fixture")?.takeIf { it.window?.isFocused == true })
            assertEquals(0, graph.secureClipboard.count())
            Device.click(action)
            authenticateFixture()
            val review = checkNotNull(reviewMonitor.lastActivity)
            Device.await("Authorized selection should keep its review page") {
                var ready = false
                Device.onMain {
                    assertFalse("Authentication must not dismiss the captured draft", review.isFinishing)
                    ready = review.hasWindowFocus() && descendants(review.window.decorView)
                        .filterIsInstance<android.widget.TextView>().any { it.text == Device.context.getString(R.string.save) }
                }
                ready
            }
            assertEquals("Authentication alone cannot save", 0, graph.secureClipboard.count())
            Device.click(Device.context.getString(R.string.save))
            Device.await { graph.secureClipboard.count() == 1 }
            val entry = graph.secureClipboard.summaries().single()
            createdId = entry.id
            assertEquals("public selection fixture", graph.secureClipboard.read(entry.id, grant()))
            assertEquals(baseline, clipboard.timestamp())
            Device.showFixtureKeyboard(activity)
            Device.onMain { activity.editor.setText("") }
            Device.instrumentation.waitForIdleSync()
            if (Device.findText(entry.displayName) == null) openPanel()
            Device.click(entry.displayName)
            authenticateFixture()
            val confirm = Device.context.getString(dev.zeroinput.ime.ui.R.string.secure_clipboard_confirm_paste)
            Device.await("Returning from authentication must show a fresh paste confirmation") { Device.findText(confirm) != null }
            Device.onMain { assertEquals("", activity.editor.text.toString()) }
            Device.click(confirm)
            Device.await("Explicit confirmation must paste into the current fixture") {
                var pasted = false
                Device.onMain { pasted = activity.editor.text.toString() == "public selection fixture" }
                pasted
            }
            assertEquals(baseline, clipboard.timestamp())
        } finally {
            Device.instrumentation.removeMonitor(reviewMonitor)
            createdId?.let { graph.secureClipboard.remove(it, grant()) }
            Device.onMain { graph.settings.secureClipboardEnabled = enabled; activity.finish() }
        }
    }

    @Test
    fun transferIsOneUseAndReplacingOrClosingItWipesTheOldDraft() = Device.onMain {
        val transfer = ClipboardSelectionTransfer()
        try {
            val first = ClipboardImportRequest("fixture".toCharArray())
            val old = transfer.offer(ClipboardImportDraft(first, 7))
            val second = ClipboardImportRequest("fixture".toCharArray())
            val token = transfer.offer(ClipboardImportDraft(second, 8))
            assertEquals(ClipboardImportRequest.Phase.CLOSED, first.phase)
            assertNull(transfer.take(old))
            val taken = checkNotNull(transfer.take(token))
            assertEquals(8L, taken.generation)
            assertSame(second, taken.request)
            assertNull(transfer.take(token))
            taken.request.close()
            val third = ClipboardImportRequest("fixture".toCharArray())
            transfer.offer(ClipboardImportDraft(third, 9))
            transfer.close()
            assertEquals(ClipboardImportRequest.Phase.CLOSED, third.phase)
        } finally { transfer.close() }
    }

    @Test
    fun selectionImportCannotBeLaunchedByOtherApplications() {
        @Suppress("DEPRECATION")
        val info = Device.context.packageManager.getActivityInfo(
            ComponentName(Device.context, ClipboardSelectionImportActivity::class.java), 0)
        assertFalse(info.exported)
    }

    private fun openPanel() {
        val description = Device.context.getString(dev.zeroinput.ime.ui.R.string.secure_clipboard_open)
        Device.await { Device.roots().any { root -> findDescription(root, description) != null } }
        val button = checkNotNull(Device.roots().firstNotNullOfOrNull { findDescription(it, description) })
        assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun findDescription(node: AccessibilityNodeInfo, description: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString() == description) return node
        return (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let { child -> findDescription(child, description) } }
    }

    private fun descendants(view: android.view.View): List<android.view.View> = listOf(view) +
        if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun authenticateFixture() {
        Device.await("System device-credential input should be visible") {
            Device.roots().any { it.packageName?.toString() in setOf("com.android.systemui", "com.android.settings") &&
                it.window?.isFocused == true && hasPasswordField(it) }
        }
        Device.instrumentation.uiAutomation.waitForIdle(100, 3_000)
        Device.shell("input text 2468")
        Device.shell("input keyevent KEYCODE_ENTER")
    }

    private fun hasPasswordField(node: AccessibilityNodeInfo): Boolean = (node.isPassword && node.isFocused) ||
        (0 until node.childCount).any { node.getChild(it)?.let(::hasPasswordField) == true }

    private fun grant() = AuthenticationGrant.afterSuccessfulSystemAuthentication()
}
