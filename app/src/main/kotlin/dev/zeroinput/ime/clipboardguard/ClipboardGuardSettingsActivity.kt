package dev.zeroinput.ime.clipboardguard

import android.Manifest
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.zeroinput.ime.R
import dev.zeroinput.ime.ZeroInputApplication
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

class ClipboardGuardSettingsActivity : AppCompatActivity() {
    private val graph by lazy { (application as ZeroInputApplication).graph }
    private lateinit var screen: ClipboardGuardSettingsView
    private var observer: AutoCloseable? = null
    private var foreground = AtomicBoolean(false)
    private var checking = false
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { render() }
    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = ClipboardGuardSettingsView(this)
        setContentView(screen)
        screen.onBack = ::finish
        screen.onOptions = ::changeOptions
        screen.onNotificationPermission = ::requestNotificationPermission
        screen.onClear = ::inspectCurrent
        screen.onRetry = { graph.clipboardGuard.retryMonitoring() }
        screen.onOverlayPermission = { explainOverlayPermission(graph.clipboardGuardPreferences.options) }
        screen.onOverlayPreview = {
            val owner = WeakReference(this)
            graph.clipboardGuard.previewOverlay { shown ->
                owner.get()?.takeIf { it.foreground.get() && !shown }?.let {
                    Toast.makeText(it, R.string.clipboard_guard_overlay_unavailable, Toast.LENGTH_LONG).show()
                }
            }
        }
        observer = graph.clipboardGuard.observe { render() }
    }

    override fun onResume() {
        super.onResume()
        foreground = AtomicBoolean(true)
        render()
    }

    override fun onPause() {
        foreground.set(false)
        super.onPause()
    }

    override fun onDestroy() {
        observer?.close()
        observer = null
        super.onDestroy()
    }

    private fun changeOptions(next: ClipboardGuardOptions) {
        if (!foreground.get() || isFinishing || isDestroyed) return
        val previous = graph.clipboardGuardPreferences.options
        if (next.overlayReminder && !previous.overlayReminder) {
            render()
            explainOverlayPermission(next)
        } else if (next.clearMode == ClipboardClearMode.AUTOMATIC && previous.clearMode != next.clearMode) {
            render()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clipboard_guard_clear_automatic)
                .setMessage(R.string.clipboard_guard_automatic_warning)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clipboard_guard_enable_automatic) { _, _ -> applyOptions(next) }
                .show()
        } else applyOptions(next)
    }

    private fun explainOverlayPermission(next: ClipboardGuardOptions) {
        val granted = Settings.canDrawOverlays(this)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clipboard_guard_overlay_permission_title)
            .setMessage(R.string.clipboard_guard_overlay_permission_reason)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(if (granted) R.string.clipboard_guard_overlay_enable else R.string.clipboard_guard_overlay_continue) { _, _ ->
                applyOptions(next)
                if (!granted) {
                    try {
                        overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this, R.string.clipboard_guard_overlay_unavailable, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show().getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).filterTouchesWhenObscured = true
    }

    private fun inspectCurrent() {
        if (checking || !foreground.get() || !window.decorView.hasWindowFocus()) return
        checking = true
        val active = foreground
        val owner = WeakReference(this)
        graph.clipboardGuard.inspectCurrent(active::get) { state ->
            owner.get()?.let { activity ->
                activity.checking = false
                if (active.get() && activity.window.decorView.hasWindowFocus()) {
                    if (state.ticket != null) {
                        activity.startActivity(Intent(activity, ClipboardClearActivity::class.java)
                            .putExtra(ClipboardClearActivity.EXTRA_TICKET, state.ticket.id))
                    } else Toast.makeText(activity, state.statusText(), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun applyOptions(next: ClipboardGuardOptions) {
        val requestPermission = next.notificationReminder && !graph.clipboardGuardPreferences.options.notificationReminder
        graph.clipboardGuardPreferences.options = next
        render()
        if (requestPermission && !ClipboardGuardNotifications(this).allowed()) requestNotificationPermission()
    }

    private fun requestNotificationPermission() {
        if (!graph.clipboardGuardPreferences.options.notificationReminder) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun render() {
        if (!::screen.isInitialized || isFinishing || isDestroyed) return
        screen.render(graph.clipboardGuardPreferences.options, graph.clipboardGuard.state,
            ClipboardGuardNotifications(this).allowed(), Settings.canDrawOverlays(this))
    }
}
