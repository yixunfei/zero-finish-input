package dev.zeroinput.ime.ui

enum class KeyboardTheme(val label: Int, val overlay: Int) {
    CLASSIC(R.string.keyboard_theme_classic, R.style.ThemeOverlay_ZeroInput_Keyboard_Classic),
    GRAY(R.string.keyboard_theme_gray, R.style.ThemeOverlay_ZeroInput_Keyboard_Gray),
    MINT(R.string.keyboard_theme_mint, R.style.ThemeOverlay_ZeroInput_Keyboard_Mint),
    ROSE(R.string.keyboard_theme_rose, R.style.ThemeOverlay_ZeroInput_Keyboard_Rose),
}

enum class KeyboardHeight(val label: Int, private val portrait: Int, private val landscape: Int) {
    COMPACT(R.string.keyboard_height_compact, 48, 48),
    STANDARD(R.string.keyboard_height_standard, 52, 48),
    COMFORTABLE(R.string.keyboard_height_comfortable, 60, 52);

    fun rowHeight(landscape: Boolean): Int = if (landscape) this.landscape else portrait
}

data class KeyboardAppearance(
    val theme: KeyboardTheme = KeyboardTheme.CLASSIC,
    val height: KeyboardHeight = KeyboardHeight.STANDARD,
)
