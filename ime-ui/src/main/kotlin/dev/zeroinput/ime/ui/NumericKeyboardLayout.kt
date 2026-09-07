package dev.zeroinput.ime.ui

import android.content.Context
import dev.zeroinput.ime.core.EditorInputOptions
import dev.zeroinput.ime.core.EditorLayout

internal object NumericKeyboardLayout {
    fun rows(context: Context, options: EditorInputOptions): List<List<KeySpec>> {
        val phone = options.layout == EditorLayout.PHONE
        val date = options.layout == EditorLayout.DATETIME
        return listOf(
            listOf(key("1"), key("2"), key("3"), KeySpec("⌫", context.getString(R.string.key_backspace), KeyboardAction.Backspace, style = KeyStyle.MODIFIER)),
            listOf(key("4"), key("5"), key("6"), key(if (phone) "+" else "-", phone || date || options.signed)),
            listOf(key("7"), key("8"), key("9"), key(if (date) "/" else ".", date || options.decimal)),
            listOf(key(if (date) ":" else "*", phone || date), key("0"), key("#", phone),
                KeySpec("↵", context.getString(R.string.key_enter), KeyboardAction.Enter, style = KeyStyle.PRIMARY)),
        )
    }

    private fun key(value: String, enabled: Boolean = true) = KeySpec(if (enabled) value else "",
        if (enabled) value else "", KeyboardAction.LiteralText(value), enabled = enabled)
}
