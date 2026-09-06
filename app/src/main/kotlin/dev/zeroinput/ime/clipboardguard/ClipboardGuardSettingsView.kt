package dev.zeroinput.ime.clipboardguard

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import dev.zeroinput.ime.R

internal class ClipboardGuardSettingsView(context: Context) : LinearLayout(context) {
    var onOptions: (ClipboardGuardOptions) -> Unit = {}
    var onBack: () -> Unit = {}
    var onNotificationPermission: () -> Unit = {}
    var onClear: () -> Unit = {}
    private var options = ClipboardGuardOptions()
    private var rendering = false
    private val status = label(16f)
    private val listen = toggle(R.string.clipboard_guard_listen) { options.copy(listening = it) }
    private val keyboard = toggle(R.string.clipboard_guard_keyboard) { options.copy(keyboardReminder = it) }
    private val notifications = toggle(R.string.clipboard_guard_notifications) { options.copy(notificationReminder = it) }
    private val authentication = toggle(R.string.clipboard_guard_authenticate) { options.copy(authenticate = it).normalized() }
    private val permissionStatus = label(14f).apply { setText(R.string.clipboard_guard_permission_missing) }
    private val permission = command(R.string.clipboard_guard_permission) { onNotificationPermission() }
    private val clear = command(R.string.clipboard_guard_clear_current) { onClear() }
    private val modes = ClipboardClearMode.entries.associateWith { mode ->
        MaterialRadioButton(context).apply {
            id = View.generateViewId()
            isSaveEnabled = false
            minHeight = dp(48)
            filterTouchesWhenObscured = true
            setText(when (mode) {
                ClipboardClearMode.NONE -> R.string.clipboard_guard_clear_none
                ClipboardClearMode.CONFIRM -> R.string.clipboard_guard_clear_confirm
                ClipboardClearMode.AUTOMATIC -> R.string.clipboard_guard_clear_automatic
            })
        }
    }
    private val modeGroup = RadioGroup(context).apply {
        orientation = VERTICAL
        modes.values.forEach(::addView)
        setOnCheckedChangeListener { _, id ->
            if (!rendering) modes.entries.firstOrNull { it.value.id == id }?.let {
                onOptions(options.copy(clearMode = it.key).normalized())
            }
        }
    }

    init {
        orientation = VERTICAL
        isSaveEnabled = false
        addView(MaterialToolbar(context).apply {
            title = context.getString(R.string.clipboard_guard_title)
            setTitleTextAppearance(context, R.style.TextAppearance_ZeroInput_GuardTitle)
            setNavigationIcon(R.drawable.ic_arrow_back)
            navigationContentDescription = context.getString(R.string.navigate_up)
            setNavigationOnClickListener { onBack() }
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))
        addView(ScrollView(context).apply {
            isFillViewport = true
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(20), dp(12), dp(20), dp(24))
                addView(label(14f).apply { setText(R.string.clipboard_guard_limit) }, row())
                addView(status, row())
                addView(listen, row())
                addView(keyboard, row())
                addView(notifications, row())
                addView(permissionStatus, row())
                addView(permission, row())
                addView(label(14f).apply { setText(R.string.clipboard_guard_clear_mode) }, row())
                addView(modeGroup, row())
                addView(authentication, row())
                addView(clear, row())
            })
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    fun render(value: ClipboardGuardOptions, state: ClipboardGuardState, notificationAllowed: Boolean) {
        options = value
        rendering = true
        try {
            listen.isChecked = value.listening
            keyboard.isChecked = value.keyboardReminder
            notifications.isChecked = value.notificationReminder
            authentication.isChecked = value.authenticate
            modeGroup.check(modes.getValue(value.clearMode).id)
            modes.getValue(ClipboardClearMode.AUTOMATIC).isEnabled = !value.authenticate
            permission.visibility = if (value.notificationReminder && !notificationAllowed) VISIBLE else GONE
            permissionStatus.visibility = permission.visibility
            clear.isEnabled = value == state.options && value.listening && value.clearMode != ClipboardClearMode.NONE && state.ticket != null
            val visibleStatus = when {
                !value.listening -> ClipboardGuardStatus.OFF
                value != state.options -> ClipboardGuardStatus.UNAVAILABLE
                else -> state.status
            }
            status.setText(when (visibleStatus) {
                ClipboardGuardStatus.OFF -> R.string.clipboard_guard_off
                ClipboardGuardStatus.UNAVAILABLE -> R.string.clipboard_guard_unavailable
                ClipboardGuardStatus.WAITING -> R.string.clipboard_guard_waiting
                ClipboardGuardStatus.CHANGED -> R.string.clipboard_guard_changed
                ClipboardGuardStatus.CLEARED -> R.string.clipboard_guard_cleared
                ClipboardGuardStatus.FAILED -> R.string.clipboard_guard_failed
            })
        } finally { rendering = false }
    }

    private fun toggle(text: Int, change: (Boolean) -> ClipboardGuardOptions) = MaterialSwitch(context).apply {
        setText(text)
        textSize = 16f
        minHeight = dp(52)
        isSaveEnabled = false
        filterTouchesWhenObscured = true
        setOnCheckedChangeListener { _, checked -> if (!rendering) onOptions(change(checked)) }
    }

    private fun command(text: Int, action: () -> Unit) = MaterialButton(context).apply {
        setText(text)
        isAllCaps = false
        letterSpacing = 0f
        minHeight = dp(48)
        filterTouchesWhenObscured = true
        setOnClickListener { action() }
    }

    private fun label(size: Float) = TextView(context).apply { textSize = size }
    private fun row() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
