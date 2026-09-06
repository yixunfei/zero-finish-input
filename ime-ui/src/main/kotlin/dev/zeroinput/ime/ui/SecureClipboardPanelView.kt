package dev.zeroinput.ime.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton

data class SecureClipboardItemUi(
    val id: String,
    val displayName: String,
)

class SecureClipboardPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ScrollView(context, attrs) {
    var onItemSelected: (String) -> Unit = {}
    var onManageRequested: () -> Unit = {}

    private val list = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    init {
        isFillViewport = true
        addView(list)
    }

    fun render(enabled: Boolean, items: List<SecureClipboardItemUi>) {
        list.removeAllViews()
        when {
            !enabled -> addStatus("安全剪贴板未启用")
            items.isEmpty() -> addStatus("暂无安全片段")
            else -> items.forEach(::addItem)
        }
        list.addView(manageButton())
    }

    private fun addStatus(value: String) {
        list.addView(TextView(context).apply {
            text = value
            gravity = Gravity.CENTER
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(72))
        })
    }

    private fun addItem(item: SecureClipboardItemUi) {
        list.addView(MaterialButton(context).apply {
            text = context.getString(R.string.secure_clipboard_item, item.displayName)
            contentDescription = context.getString(R.string.secure_clipboard_item_description, item.displayName)
            setIconResource(R.drawable.ic_lock)
            iconSize = dp(18)
            iconPadding = dp(10)
            textSize = 16f
            letterSpacing = 0f
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            cornerRadius = dp(6)
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(52)).apply {
                setMargins(0, dp(3), 0, dp(3))
            }
            setOnClickListener { onItemSelected(item.id) }
        })
    }

    private fun manageButton() = MaterialButton(context).apply {
        text = "⚙"
        contentDescription = "管理安全剪贴板"
        textSize = 20f
        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK))
        isAllCaps = false
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48))
        setOnClickListener { onManageRequested() }
    }

    private fun resolveColor(attribute: Int, fallback: Int): Int {
        val values = context.obtainStyledAttributes(intArrayOf(attribute))
        return values.getColor(0, fallback).also { values.recycle() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
