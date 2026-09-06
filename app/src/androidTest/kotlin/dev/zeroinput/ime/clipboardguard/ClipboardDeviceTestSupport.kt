package dev.zeroinput.ime.clipboardguard

import android.app.AppOpsManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.graphics.Rect
import android.view.ViewConfiguration
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import dev.zeroinput.ime.R
import org.junit.Assert.assertTrue

internal object ClipboardDeviceTestSupport {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context get() = instrumentation.targetContext

    init {
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    fun onMain(action: () -> Unit) {
        var result: Result<Unit>? = null
        instrumentation.runOnMainSync { result = runCatching(action) }
        checkNotNull(result).getOrThrow()
    }

    fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 6_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
        assertTrue("Public fixture flow did not settle", condition())
    }

    fun flushGuardPreferences() {
        check(context.getSharedPreferences("clipboard-guard-settings", android.content.Context.MODE_PRIVATE).edit().commit())
    }

    fun startSource() {
        val component = ComponentName(instrumentation.context.packageName, ExternalClipboardFixture::class.java.name)
        shell("am start -W -n ${component.flattenToString()} -f 0x10008000")
        await { findText("Copy public fixture") != null }
        await { findText("Source ready") != null }
        @Suppress("DEPRECATION")
        val source = context.packageManager.getApplicationInfo(instrumentation.context.packageName, 0)
        assertTrue("The source must have a different UID", source.uid != Process.myUid())
    }

    fun roots(): List<AccessibilityNodeInfo> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) instrumentation.uiAutomation.clearCache()
        return listOfNotNull(instrumentation.uiAutomation.rootInActiveWindow) +
            instrumentation.uiAutomation.windows.mapNotNull { it.root }
    }

    fun findText(text: String): AccessibilityNodeInfo? = roots().firstNotNullOfOrNull { root ->
        root.findAccessibilityNodeInfosByText(text).firstOrNull { it.text?.toString() == text }
    }

    fun click(text: String) {
        await { findText(text) != null }
        var node = checkNotNull(findText(text))
        while (!node.isClickable && node.parent != null) node = node.parent
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    fun closeSource() {
        // A process-exit action cannot acknowledge an accessibility IPC after execution.
        checkNotNull(findText("Close public fixture")).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        await { findText("Close public fixture") == null }
    }

    fun longPressPublicText() {
        val bounds = Rect()
        checkNotNull(findText("public guard fixture")).getBoundsInScreen(bounds)
        val x = bounds.left + minOf(bounds.width() / 2, (32 * context.resources.displayMetrics.density).toInt())
        val y = bounds.centerY()
        shell("input touchscreen swipe $x $y $x $y ${ViewConfiguration.getLongPressTimeout() + 300}")
    }

    fun overlayWindows(): List<AccessibilityWindowInfo> = instrumentation.uiAutomation.windows.filter {
        it.type == AccessibilityWindowInfo.TYPE_SYSTEM &&
            it.root?.findAccessibilityNodeInfosByText(context.getString(R.string.clipboard_guard_title))?.isNotEmpty() == true
    }

    fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }

    @Suppress("DEPRECATION")
    fun withOverlayPermission(granted: Boolean, test: () -> Unit) {
        val manager = context.getSystemService(AppOpsManager::class.java)
        val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            manager.unsafeCheckOpRawNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, Process.myUid(), context.packageName)
        } else manager.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, Process.myUid(), context.packageName)
        try {
            setOverlayPermission(if (granted) "allow" else "deny")
            test()
        } finally {
            setOverlayPermission(when (original) {
                AppOpsManager.MODE_ALLOWED -> "allow"
                AppOpsManager.MODE_IGNORED -> "ignore"
                AppOpsManager.MODE_ERRORED -> "deny"
                AppOpsManager.MODE_FOREGROUND -> "foreground"
                else -> "default"
            })
        }
    }

    fun setOverlayPermission(mode: String) {
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $mode")
    }
}
