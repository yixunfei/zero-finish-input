package dev.zeroinput.ime.ui

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Reserves its row while opted in; an event never shifts the keys below it. */
internal class ClipboardGuardReminderView(context: Context) : LinearLayout(context) {
    var onRequested: () -> Unit = {}
    private val status = TextView(context).apply {
        textSize = 14f
        maxLines = 2
        setPadding(dp(12), 0, dp(8), 0)
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(48))
        addView(status, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(panelIconButton(context, android.R.drawable.ic_menu_manage, R.string.clipboard_guard_open) {
            onRequested()
        }, LayoutParams(dp(48), dp(48)))
        visibility = GONE
    }

    fun render(enabled: Boolean, changed: Boolean) {
        visibility = if (enabled) VISIBLE else GONE
        status.setText(if (changed) R.string.clipboard_guard_changed else R.string.clipboard_guard_open)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
