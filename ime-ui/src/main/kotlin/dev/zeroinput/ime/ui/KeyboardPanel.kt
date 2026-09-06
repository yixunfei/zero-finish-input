package dev.zeroinput.ime.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import dev.zeroinput.engine.api.ChineseKeyboardLayout

class KeyboardPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    var onAction: (KeyboardAction) -> Unit = {}
    var onUserInteraction: () -> Unit = {}
    var onClearComposition: () -> Boolean = { false }
    private var backspaceRepeater: BackspaceRepeater? = null

    private var page = KeyboardPage.LETTERS
    private var shifted = false
    private var languageLabel = "中"
    private var keyboardLayout = ChineseKeyboardLayout.FULL
    private val keys = mutableListOf<KeyboardKeyView>()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isMotionEventSplittingEnabled = true
        render()
    }

    fun setLanguageLabel(value: String) {
        if (languageLabel == value) return
        languageLabel = value
        render()
    }

    fun showLetters() {
        if (page == KeyboardPage.LETTERS) return
        page = KeyboardPage.LETTERS
        render()
    }

    fun setKeyboardLayout(value: ChineseKeyboardLayout) {
        if (keyboardLayout == value) return
        keyboardLayout = value
        page = KeyboardPage.LETTERS
        shifted = false
        render()
    }

    private fun render() {
        cancelPendingGestures()
        removeAllViews()
        keys.clear()
        backspaceRepeater = null
        val rows = when (page) {
            KeyboardPage.LETTERS -> if (keyboardLayout == ChineseKeyboardLayout.NINE_KEY)
                KeyboardLayouts.nineKey(context, languageLabel) else KeyboardLayouts.letters(shifted, languageLabel)
            KeyboardPage.SYMBOLS -> KeyboardLayouts.symbols(languageLabel)
            KeyboardPage.MORE_SYMBOLS -> KeyboardLayouts.moreSymbols(languageLabel)
        }
        rows.forEach(::addRow)
    }

    private fun addRow(specs: List<KeySpec>) {
        val row = KeyboardRow(context, specs.map(KeySpec::widthWeight)).apply {
            val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(if (landscape) 48 else KEY_ROW_HEIGHT_DP))
        }
        specs.forEach { spec -> row.addView(createKey(spec)) }
        addView(row)
    }

    private fun createKey(spec: KeySpec): KeyboardKeyView = KeyboardKeyView(context, backgroundColor(spec.style),
        backgroundColor(KeyStyle.PRIMARY), androidx.core.graphics.ColorUtils.setAlphaComponent(
            resolveColor(com.google.android.material.R.attr.colorOutline, Color.GRAY), 80)).apply {
        text = spec.label
        contentDescription = spec.contentDescription
        textSize = if (spec.label.length > 2) 13f else 19f
        letterSpacing = 0f
        isAllCaps = false
        gravity = Gravity.CENTER
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK))
        layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, spec.widthWeight)
        setOnClickListener { handleAction(spec.action) }
        if (spec.action == KeyboardAction.Backspace) {
            backspaceRepeater = BackspaceRepeater(this, { onClearComposition() }) {
                onAction(KeyboardAction.Backspace)
            }
        }
        keys += this
    }

    fun cancelPendingGestures() { backspaceRepeater?.cancel(); keys.forEach { it.cancelTouch() } }

    internal val preferredHeight: Int
        get() = paddingTop + paddingBottom + (0 until childCount).sumOf { getChildAt(it).layoutParams.height }

    override fun onDetachedFromWindow() {
        cancelPendingGestures()
        super.onDetachedFromWindow()
    }

    private fun handleAction(action: KeyboardAction) {
        when (action) {
            KeyboardAction.Shift -> {
                onUserInteraction()
                shifted = !shifted
                render()
            }
            KeyboardAction.ShowLetters -> {
                onUserInteraction()
                page = KeyboardPage.LETTERS
                render()
            }
            KeyboardAction.ShowSymbols -> {
                onUserInteraction()
                page = KeyboardPage.SYMBOLS
                render()
            }
            KeyboardAction.ShowMoreSymbols -> {
                onUserInteraction()
                page = KeyboardPage.MORE_SYMBOLS
                render()
            }
            is KeyboardAction.Text -> {
                onAction(action)
                if (shifted) {
                    shifted = false
                    render()
                }
            }
            else -> onAction(action)
        }
    }

    private fun backgroundColor(style: KeyStyle): Int = when (style) {
        KeyStyle.NORMAL -> resolveColor(com.google.android.material.R.attr.colorSurface, 0xfff4f5f5.toInt())
        KeyStyle.MODIFIER -> resolveColor(com.google.android.material.R.attr.colorSurfaceVariant, 0xffe2e5e4.toInt())
        KeyStyle.PRIMARY -> resolveColor(com.google.android.material.R.attr.colorPrimaryContainer, 0xffbceadd.toInt())
    }

    private fun resolveColor(attribute: Int, fallback: Int): Int {
        val values = context.obtainStyledAttributes(intArrayOf(attribute))
        return values.getColor(0, fallback).also { values.recycle() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val KEY_ROW_HEIGHT_DP = 52
    }
}
