package dev.zeroinput.ime.clipboardguard

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

internal class ClipboardGuardPreferences(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("clipboard-guard-settings", Context.MODE_PRIVATE))

    var options: ClipboardGuardOptions
        get() {
            val snapshot = preferences.all
            return ClipboardGuardOptions(
                listening = snapshot["listening"] as? Boolean ?: false,
                keyboardReminder = snapshot["keyboard-reminder"] as? Boolean ?: false,
                notificationReminder = snapshot["notification-reminder"] as? Boolean ?: false,
                clearMode = ClipboardClearMode.entries.firstOrNull { it.name == snapshot["clear-mode"] }
                    ?: ClipboardClearMode.NONE,
                authenticate = snapshot["authenticate"] as? Boolean ?: false,
                overlayReminder = snapshot["overlay-reminder"] as? Boolean ?: false,
                overlayPosition = ClipboardOverlayPosition.entries.firstOrNull { it.name == snapshot["overlay-position"] }
                    ?: ClipboardOverlayPosition.TOP,
                overlaySeconds = snapshot["overlay-seconds"] as? Int ?: 5,
            ).normalized()
        }
        set(value) {
            val options = value.normalized()
            preferences.edit {
                putBoolean("listening", options.listening)
                putBoolean("keyboard-reminder", options.keyboardReminder)
                putBoolean("notification-reminder", options.notificationReminder)
                putString("clear-mode", options.clearMode.name)
                putBoolean("authenticate", options.authenticate)
                putBoolean("overlay-reminder", options.overlayReminder)
                putString("overlay-position", options.overlayPosition.name)
                putInt("overlay-seconds", options.overlaySeconds)
            }
        }

    fun observe(changed: () -> Unit): AutoCloseable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> changed() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return AutoCloseable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
