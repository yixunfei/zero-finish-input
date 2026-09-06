package dev.zeroinput.ime.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import dev.zeroinput.engine.api.InputLanguage
import dev.zeroinput.engine.api.ChineseInputOptions
import dev.zeroinput.engine.api.ChineseScript
import dev.zeroinput.engine.api.ChineseKeyboardLayout
import dev.zeroinput.engine.api.EngineCapability
import dev.zeroinput.engine.api.EngineSnapshot
import dev.zeroinput.engine.api.PageDirection
import dev.zeroinput.ime.core.InputSessionState

class ZeroInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    var onKeyboardAction: (KeyboardAction) -> Unit = {}
    var onCandidateSelected: (Int) -> Unit = {}
    var onCandidatePageChanged: (PageDirection) -> Unit = {}
    var onEmojiSelected: (EmojiEntry) -> Unit = {}
    var onExpressionFavoriteRequested: (EmojiEntry, Boolean) -> Unit = { _, _ -> }
    var onExpressionManagementRequested: (String?) -> Unit = {}
    var onSecureClipboardSelected: (String) -> Unit = {}
    var onSettingsRequested: () -> Unit = {}
    var onClipboardGuardRequested: () -> Unit = {}
    var onSecureClipboardManagementRequested: () -> Unit = {}
    /**
     * Notifies the service about UI-only interactions (panel changes and
     * search toggles) that do not otherwise produce an [KeyboardAction].
     * The service uses this signal to invalidate pending authenticated
     * clipboard requests.
     */
    var onUserInteraction: () -> Unit = {}
    var onClearCompositionRequested: () -> Boolean = { false }
    var onEngineRetryRequested: () -> Unit = {}
    var onScriptSwitchRequested: () -> Unit = {}
    var onLayoutSwitchRequested: () -> Unit = {}
    var onReadingSelected: (Int) -> Unit = {}

    private var mode = PanelMode.KEYBOARD
    private val languageButton = toolbarButton("中", "切换中英文") {
        dispatchKeyboardAction(KeyboardAction.SwitchLanguage)
    }
    private val candidateStrip = CandidateStripView(context)
    private val clipboardGuard = ClipboardGuardReminderView(context).apply { onRequested = { onClipboardGuardRequested() } }
    private val expandedCandidates = ExpandedCandidatesView(context)
    private var currentSnapshot = EngineSnapshot.Empty
    private var currentLanguage = InputLanguage.CHINESE
    private var currentPack: String? = null
    private var sensitive = false
    private val scriptButton = toolbarButton(context.getString(R.string.script_short_simplified), context.getString(R.string.switch_chinese_script)) {
        onScriptSwitchRequested()
    }
    private val keyboard = KeyboardPanel(context)
    private val readings = ReadingChoicesView(context).apply { onSelected = { onReadingSelected(it) } }
    private var chineseLayout = ChineseKeyboardLayout.FULL
    private var capabilities: Set<EngineCapability> = emptySet()
    private val keyboardContainer = LinearLayout(context).apply {
        orientation = HORIZONTAL
        addView(readings, LayoutParams(dp(60), keyboard.preferredHeight))
        addView(keyboard, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }
    private val layoutButton = toolbarButton(context.getString(R.string.layout_nine_short), context.getString(R.string.switch_keyboard_layout)) {
        onLayoutSwitchRequested()
    }
    private val emoji = EmojiPanelView(context)
    private val secureClipboard = SecureClipboardPanelView(context)
    private val content = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }
    private val returnButton = toolbarButton("⌨", "返回键盘") { showMode(PanelMode.KEYBOARD) }

    init {
        orientation = VERTICAL
        setBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurface, 0xfffafafa.toInt()))
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        addView(createToolbar())
        addView(clipboardGuard)
        addView(candidateStrip)
        content.addView(emoji)
        content.addView(secureClipboard)
        content.addView(keyboardContainer)
        content.addView(expandedCandidates, LayoutParams(LayoutParams.MATCH_PARENT, dp(214)))
        addView(content)
        bindCallbacks()
        showMode(PanelMode.KEYBOARD)
    }

    fun renderSession(state: InputSessionState) {
        if (currentLanguage != state.language || currentPack != state.languagePackKey || sensitive != state.privacy.isSensitive) {
            cancelPendingGestures()
            if (mode == PanelMode.CANDIDATES) showMode(PanelMode.KEYBOARD)
        }
        currentLanguage = state.language
        currentPack = state.languagePackKey
        sensitive = state.privacy.isSensitive
        capabilities = state.engineDescriptor?.capabilities.orEmpty()
        currentSnapshot = state.snapshot
        val label = if (state.languagePackKey != null) {
            "包"
        } else if (state.language == InputLanguage.CHINESE) {
            "中"
        } else {
            "En"
        }
        languageButton.text = if (state.privacy.isSensitive) "🔒" else label
        languageButton.contentDescription = if (state.privacy.isSensitive) "敏感输入保护中" else "切换中英文"
        keyboard.setLanguageLabel(label)
        candidateStrip.render(state.snapshot)
        scriptButton.visibility = if (state.language == InputLanguage.CHINESE && state.languagePackKey == null &&
            state.privacy.suggestionsAllowed && EngineCapability.CHINESE_SCRIPT in capabilities) View.VISIBLE else View.GONE
        layoutButton.visibility = if (mode == PanelMode.KEYBOARD && state.language == InputLanguage.CHINESE && state.languagePackKey == null &&
            state.privacy.suggestionsAllowed && EngineCapability.NINE_KEY_PINYIN in capabilities) View.VISIBLE else View.GONE
        readings.render(state.snapshot)
        updateKeyboardLayout()
        if (mode == PanelMode.CANDIDATES) {
            if (state.snapshot.candidates.isEmpty()) showMode(PanelMode.KEYBOARD)
            else expandedCandidates.render(state.snapshot)
        }
        if (!state.snapshot.isComposing) expandedCandidates.clear()
    }

    fun renderEngineStatus(status: InputEngineStatus) { candidateStrip.renderStatus(status) }

    fun renderClipboardGuard(enabled: Boolean, changed: Boolean) { clipboardGuard.render(enabled, changed) }

    fun renderChineseOptions(options: ChineseInputOptions) {
        val label = context.getString(if (options.script == ChineseScript.SIMPLIFIED)
            R.string.script_short_simplified else R.string.script_short_traditional)
        if (scriptButton.text != label) scriptButton.text = label
    }

    fun renderActiveLayout(layout: ChineseKeyboardLayout) {
        chineseLayout = layout
        updateKeyboardLayout()
    }

    private fun updateKeyboardLayout() {
        val nineKey = currentLanguage == InputLanguage.CHINESE && currentPack == null && !sensitive &&
            chineseLayout == ChineseKeyboardLayout.NINE_KEY && EngineCapability.NINE_KEY_PINYIN in capabilities &&
            mode != PanelMode.EMOJI
        keyboard.setKeyboardLayout(if (nineKey) ChineseKeyboardLayout.NINE_KEY else ChineseKeyboardLayout.FULL)
        readings.visibility = if (nineKey) View.VISIBLE else View.GONE
        val label = context.getString(if (nineKey) R.string.layout_full_short else R.string.layout_nine_short)
        if (layoutButton.text != label) layoutButton.text = label
    }

    fun cancelPendingGestures() { keyboard.cancelPendingGestures() }

    fun renderExpressions(allowed: Boolean, data: PersonalExpressionsUi, recent: List<String>) {
        emoji.renderPersonal(allowed, data, recent)
    }

    fun clearExpressionSession() { emoji.clearSession() }

    fun renderSecureClipboard(enabled: Boolean, items: List<SecureClipboardItemUi>) {
        secureClipboard.render(enabled, items)
    }

    fun returnToKeyboard() {
        showMode(PanelMode.KEYBOARD)
    }

    private fun createToolbar(): View = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(48))
        addView(languageButton)
        addView(scriptButton)
        addView(layoutButton)
        addView(toolbarButton("☺", context.getString(R.string.expression_smileys)) { toggleMode(PanelMode.EMOJI) })
        addView(toolbarButton("🔒", "安全剪贴板") { toggleMode(PanelMode.SECURE_CLIPBOARD) })
        addView(Space(context).apply { layoutParams = LayoutParams(0, 1, 1f) })
        // The return control replaces the layout switch while a secondary panel is open.
        addView(returnButton)
        addView(toolbarButton("⚙", context.getString(R.string.keyboard_settings)) { onSettingsRequested() })
    }

    private fun bindCallbacks() {
        keyboard.onAction = ::dispatchKeyboardAction
        keyboard.onUserInteraction = { onUserInteraction() }
        keyboard.onClearComposition = {
            if (mode == PanelMode.EMOJI && emoji.isSearchActive) {
                onUserInteraction()
                emoji.clearQuery()
                true
            } else onClearCompositionRequested()
        }
        candidateStrip.onCandidateSelected = { onCandidateSelected(it) }
        candidateStrip.onExpandRequested = {
            if (mode == PanelMode.CANDIDATES) showMode(PanelMode.KEYBOARD)
            else if (currentSnapshot.candidates.isNotEmpty()) showMode(PanelMode.CANDIDATES)
        }
        candidateStrip.onRetryRequested = { onEngineRetryRequested() }
        expandedCandidates.onCandidateSelected = { onCandidateSelected(it) }
        expandedCandidates.onPageChanged = { onCandidatePageChanged(it) }
        emoji.onEmojiSelected = { onEmojiSelected(it) }
        emoji.onFavoriteRequested = { entry, selected -> onExpressionFavoriteRequested(entry, selected) }
        emoji.onManageRequested = { onExpressionManagementRequested(it) }
        emoji.onSearchModeChanged = { searchActive -> updateEmojiSearchLayout(searchActive) }
        emoji.onUserInteraction = { onUserInteraction() }
        secureClipboard.onItemSelected = { onSecureClipboardSelected(it) }
        secureClipboard.onManageRequested = {
            onUserInteraction()
            onSecureClipboardManagementRequested()
        }
    }

    private fun dispatchKeyboardAction(action: KeyboardAction) {
        if (mode == PanelMode.EMOJI && emoji.isSearchActive) {
            when (action) {
                is KeyboardAction.Text -> {
                    onUserInteraction()
                    emoji.appendQuery(action.value)
                }
                is KeyboardAction.LiteralText -> {
                    onUserInteraction()
                    emoji.appendQuery(action.value)
                }
                KeyboardAction.Backspace -> {
                    onUserInteraction()
                    emoji.removeQueryCharacter()
                }
                KeyboardAction.Space -> {
                    onUserInteraction()
                    emoji.appendQuery(" ")
                }
                KeyboardAction.Enter -> onUserInteraction()
                else -> onKeyboardAction(action)
            }
        } else {
            onKeyboardAction(action)
        }
    }

    private fun toggleMode(target: PanelMode) {
        showMode(if (mode == target) PanelMode.KEYBOARD else target)
    }

    private fun showMode(target: PanelMode) {
        if (mode != target) {
            cancelPendingGestures()
            onUserInteraction()
        }
        mode = target
        updateKeyboardLayout()
        returnButton.visibility = if (target == PanelMode.KEYBOARD) View.GONE else View.VISIBLE
        layoutButton.visibility = if (target == PanelMode.KEYBOARD && currentLanguage == InputLanguage.CHINESE &&
            EngineCapability.NINE_KEY_PINYIN in capabilities) View.VISIBLE else View.GONE
        setPanelVisible(candidateStrip, target == PanelMode.KEYBOARD || target == PanelMode.CANDIDATES)
        setPanelVisible(expandedCandidates, target == PanelMode.CANDIDATES)
        candidateStrip.setExpanded(target == PanelMode.CANDIDATES)
        if (target == PanelMode.CANDIDATES) expandedCandidates.render(currentSnapshot)
        else expandedCandidates.clear()
        setPanelVisible(secureClipboard, target == PanelMode.SECURE_CLIPBOARD)
        setPanelVisible(emoji, target == PanelMode.EMOJI)
        setPanelVisible(
            keyboardContainer,
            target == PanelMode.KEYBOARD || emoji.isSearchActive && target == PanelMode.EMOJI,
        )
        updatePanelLayout()
    }

    private fun setPanelVisible(panel: View, visible: Boolean) {
        panel.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateEmojiSearchLayout(searchActive: Boolean) {
        if (mode != PanelMode.EMOJI) return
        setPanelVisible(keyboardContainer, searchActive)
        keyboard.showLetters()
        updatePanelLayout()
    }

    private fun updatePanelLayout() {
        val searchActive = mode == PanelMode.EMOJI && emoji.isSearchActive
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val panelHeight = if (landscape) EMOJI_SEARCH_HEIGHT_DP else EMOJI_PANEL_HEIGHT_DP
        val splitSearch = searchActive &&
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            resources.configuration.screenWidthDp >= 600
        content.orientation = if (splitSearch) HORIZONTAL else VERTICAL
        emoji.layoutParams = when {
            splitSearch -> LayoutParams(0, dp(panelHeight), 1f)
            searchActive -> LayoutParams(LayoutParams.MATCH_PARENT, dp(EMOJI_SEARCH_HEIGHT_DP))
            else -> LayoutParams(LayoutParams.MATCH_PARENT, dp(panelHeight))
        }
        keyboardContainer.layoutParams = if (splitSearch) {
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 2f)
        } else {
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        secureClipboard.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(PANEL_HEIGHT_DP))
        expandedCandidates.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, keyboard.preferredHeight)
    }

    private fun toolbarButton(label: String, description: String, action: () -> Unit) = MaterialButton(context).apply {
        text = label
        contentDescription = description
        textSize = if (label.length > 1) 15f else 20f
        setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface, Color.BLACK))
        letterSpacing = 0f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        insetTop = 0
        insetBottom = 0
        setPadding(0, 0, 0, 0)
        setSingleLine()
        setBackgroundColor(Color.TRANSPARENT)
        elevation = 0f
        stateListAnimator = null
        layoutParams = LayoutParams(dp(48), dp(48))
        setOnClickListener { action() }
        setOnLongClickListener {
            Toast.makeText(context, description, Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun resolveColor(attribute: Int, fallback: Int): Int {
        val values = context.obtainStyledAttributes(intArrayOf(attribute))
        return values.getColor(0, fallback).also { values.recycle() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private enum class PanelMode {
        KEYBOARD,
        EMOJI,
        SECURE_CLIPBOARD,
        CANDIDATES,
    }

    private companion object {
        const val PANEL_HEIGHT_DP = 260
        const val EMOJI_PANEL_HEIGHT_DP = 260
        const val EMOJI_SEARCH_HEIGHT_DP = 224
    }
}
