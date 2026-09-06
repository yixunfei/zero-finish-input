package dev.zeroinput.ime.clipboardguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.zeroinput.ime.R
import dev.zeroinput.ime.ZeroInputApplication

class ClipboardGuardSettingsActivity : AppCompatActivity() {
    private val graph by lazy { (application as ZeroInputApplication).graph }
    private lateinit var screen: ClipboardGuardSettingsView
    private var observer: AutoCloseable? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = ClipboardGuardSettingsView(this)
        setContentView(screen)
        screen.onBack = ::finish
        screen.onOptions = ::changeOptions
        screen.onNotificationPermission = ::requestNotificationPermission
        screen.onClear = {
            graph.clipboardGuard.state.ticket?.let { ticket ->
                startActivity(Intent(this, ClipboardClearActivity::class.java).putExtra(ClipboardClearActivity.EXTRA_TICKET, ticket.id))
            }
        }
        observer = graph.clipboardGuard.observe { render() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroy() {
        observer?.close()
        observer = null
        super.onDestroy()
    }

    private fun changeOptions(next: ClipboardGuardOptions) {
        val previous = graph.clipboardGuardPreferences.options
        if (next.clearMode == ClipboardClearMode.AUTOMATIC && previous.clearMode != next.clearMode) {
            render()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clipboard_guard_clear_automatic)
                .setMessage(R.string.clipboard_guard_automatic_warning)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clipboard_guard_enable_automatic) { _, _ -> applyOptions(next) }
                .show()
        } else applyOptions(next)
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
        screen.render(graph.clipboardGuardPreferences.options, graph.clipboardGuard.state, ClipboardGuardNotifications(this).allowed())
    }
}
