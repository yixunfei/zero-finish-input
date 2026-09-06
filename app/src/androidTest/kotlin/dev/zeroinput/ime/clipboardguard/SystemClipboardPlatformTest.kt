package dev.zeroinput.ime.clipboardguard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.zeroinput.ime.testing.InputFixtureActivity
import dev.zeroinput.ime.R
import dev.zeroinput.ime.ZeroInputApplication
import dev.zeroinput.ime.ZeroInputService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemClipboardPlatformTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun confirmationPageClearsOnlyAfterTheForegroundButtonIsPressed() = withObservedFixture(ClipboardClearMode.CONFIRM) { runtime, port ->
        await { runtime.state.ticket != null }
        val ticket = checkNotNull(runtime.state.ticket)
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, ClipboardClearActivity::class.java)
            .putExtra(ClipboardClearActivity.EXTRA_TICKET, ticket.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ClipboardClearActivity
        try {
            val label = context.getString(R.string.clipboard_guard_confirm_action)
            await { instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)?.isNotEmpty() == true }
            assertEquals(ticket.timestamp, port.timestamp())
            val button = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(label).single()
            assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await { runtime.state.status == ClipboardGuardStatus.CLEARED }
            assertNull(port.timestamp())
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test
    fun defaultImeAutomaticModeClearsANewPublicFixture() = withObservedFixture(ClipboardClearMode.AUTOMATIC) { runtime, port ->
        await { runtime.state.status == ClipboardGuardStatus.CLEARED }
        assertNull(port.timestamp())
        assertNull(runtime.state.ticket)
    }

    @Test
    fun publicFixtureTriggersMetadataCallbackAndCanBeClearedWithoutReadingItsBody() {
        val context = instrumentation.targetContext
        assumeTrue("Run with the real guard disabled", !ClipboardGuardPreferences(context).options.listening)
        val activity = instrumentation.startActivitySync(Intent(context, InputFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as InputFixtureActivity
        val port = AndroidSystemClipboard(context)
        var registration: AutoCloseable? = null
        var fixtureTimestamp: Long? = null
        try {
            val deadline = SystemClock.elapsedRealtime() + 5_000
            var focused = false
            while (!focused && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync { focused = activity.hasWindowFocus() }
                if (!focused) SystemClock.sleep(10)
            }
            assertTrue(focused)
            val manager = context.getSystemService(ClipboardManager::class.java)
            assumeTrue("Never replace an existing clipboard", manager.primaryClipDescription == null)
            val changed = CountDownLatch(1)
            registration = port.listen { changed.countDown() }
            manager.setPrimaryClip(ClipData.newPlainText("", "public guard fixture"))
            fixtureTimestamp = port.timestamp()
            assertNotNull(fixtureTimestamp)
            assertTrue(changed.await(5, TimeUnit.SECONDS))
            assumeTrue("Do not clear a replacement clipboard", fixtureTimestamp == port.timestamp())
            port.clear()
            assertNull(port.timestamp())
        } finally {
            registration?.close()
            if (fixtureTimestamp != null && port.timestamp() == fixtureTimestamp) port.clear()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun withObservedFixture(mode: ClipboardClearMode, test: (ClipboardGuardRuntime, SystemClipboardPort) -> Unit) {
        val context = instrumentation.targetContext
        val graph = (context.applicationContext as ZeroInputApplication).graph
        val original = graph.clipboardGuardPreferences.options
        assumeTrue("Run with the real guard disabled", !original.listening)
        assumeTrue("Select ZeroInput as the test device's default IME", ComponentName.unflattenFromString(
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty(),
        ) == ComponentName(context, ZeroInputService::class.java))
        val activity = instrumentation.startActivitySync(Intent(context, InputFixtureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as InputFixtureActivity
        val port = AndroidSystemClipboard(context)
        var fixtureTimestamp: Long? = null
        try {
            await {
                var focused = false
                instrumentation.runOnMainSync { focused = activity.hasWindowFocus() }
                focused
            }
            val manager = context.getSystemService(ClipboardManager::class.java)
            assumeTrue("Never replace an existing clipboard", manager.primaryClipDescription == null)
            instrumentation.runOnMainSync {
                graph.clipboardGuardPreferences.options = ClipboardGuardOptions(listening = true, clearMode = mode)
            }
            await { graph.clipboardGuard.state.status == ClipboardGuardStatus.WAITING }
            manager.setPrimaryClip(ClipData.newPlainText("", "public guard fixture"))
            fixtureTimestamp = port.timestamp()
            test(graph.clipboardGuard, port)
        } finally {
            instrumentation.runOnMainSync { graph.clipboardGuardPreferences.options = original }
            await { graph.clipboardGuard.state.status == ClipboardGuardStatus.OFF }
            if (fixtureTimestamp != null && port.timestamp() == fixtureTimestamp) port.clear()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
        assertTrue("Platform flow did not settle", condition())
    }
}
