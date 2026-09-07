package dev.zeroinput.ime.settings

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import dev.zeroinput.ime.R
import dev.zeroinput.ime.ui.KeyboardTheme

internal object KeyboardThemeContext {
    fun create(context: Context, preset: KeyboardTheme): Context =
        ContextThemeWrapper(context, R.style.Theme_ZeroInput_InputMethod).apply { theme.applyStyle(preset.overlay, true) }
}
