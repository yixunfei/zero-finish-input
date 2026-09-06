package dev.zeroinput.ime.clipboardguard

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import dev.zeroinput.ime.R

/** Contains generic status only. This window never owns an editor or a secret preview. */
internal class ClipboardGuardOverlayView @JvmOverloads constructor(
    context: Context,
    message: Int = R.string.clipboard_guard_overlay_preview_message,
    canClear: Boolean = false,
    onOpen: () -> Unit = {},
    onDismiss: () -> Unit = {},
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
        elevation = dp(6).toFloat()
        isSaveEnabled = false
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        background = GradientDrawable().apply {
            setColor(context.getColor(R.color.zero_surface))
            setStroke(dp(1), context.getColor(R.color.zero_outline))
            cornerRadius = dp(8).toFloat()
        }
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                setText(R.string.clipboard_guard_title)
                textSize = 16f
                setTextColor(context.getColor(R.color.zero_on_surface))
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                imageTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.zero_on_surface))
                background = null
                contentDescription = context.getString(R.string.clipboard_guard_dismiss)
                filterTouchesWhenObscured = true
                setOnClickListener { onDismiss() }
            }, LayoutParams(dp(48), dp(48)))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(TextView(context).apply {
            setText(message)
            textSize = 14f
            setTextColor(context.getColor(R.color.zero_on_surface))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(MaterialButton(context).apply {
            setText(if (canClear) R.string.clipboard_guard_go_clear else R.string.clipboard_guard_open_status)
            isAllCaps = false
            letterSpacing = 0f
            minHeight = dp(48)
            cornerRadius = dp(6)
            filterTouchesWhenObscured = true
            setOnClickListener { onOpen() }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
