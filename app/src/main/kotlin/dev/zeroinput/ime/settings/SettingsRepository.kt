package dev.zeroinput.ime.settings

import android.content.Context
import dev.zeroinput.engine.api.InputLanguage
import dev.zeroinput.engine.api.ChineseInputOptions
import dev.zeroinput.engine.api.ChineseScript
import dev.zeroinput.engine.api.ChineseKeyboardLayout
import dev.zeroinput.ime.core.privacy.PrivacyConfiguration

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * Observes settings changes without exposing the underlying
     * SharedPreferences instance to consumers.  The returned handle is
     * lifecycle-bound and safe to close more than once.
     */
    fun addChangeListener(listener: () -> Unit): AutoCloseable {
        val observer = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            runCatching(listener)
        }
        preferences.registerOnSharedPreferenceChangeListener(observer)
        return object : AutoCloseable {
            override fun close() {
                preferences.unregisterOnSharedPreferenceChangeListener(observer)
            }
        }
    }

    var learningEnabled: Boolean
        get() = preferences.getBoolean(KEY_LEARNING, true)
        set(value) = edit(KEY_LEARNING, value)

    var incognitoMode: Boolean
        get() = preferences.getBoolean(KEY_INCOGNITO, false)
        set(value) = edit(KEY_INCOGNITO, value)

    var secureClipboardEnabled: Boolean
        get() = preferences.getBoolean(KEY_SECURE_CLIPBOARD, false)
        set(value) = edit(KEY_SECURE_CLIPBOARD, value)

    var hapticFeedbackEnabled: Boolean
        get() = preferences.getBoolean(KEY_HAPTICS, true)
        set(value) = edit(KEY_HAPTICS, value)

    var lastLanguage: InputLanguage
        get() = runCatching {
            InputLanguage.valueOf(preferences.getString(KEY_LANGUAGE, InputLanguage.CHINESE.name).orEmpty())
        }.getOrDefault(InputLanguage.CHINESE)
        set(value) = preferences.edit().putString(KEY_LANGUAGE, value.name).apply()

    var lastLanguagePackKey: String?
        get() = preferences.getString(KEY_LANGUAGE_PACK, null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_LANGUAGE_PACK) else putString(KEY_LANGUAGE_PACK, value)
            }.apply()
        }

    var chineseInputOptions: ChineseInputOptions
        get() = ChineseInputOptions(
            script = if (preferences.getBoolean(KEY_SIMPLIFIED, true)) ChineseScript.SIMPLIFIED else ChineseScript.TRADITIONAL,
            abbreviatedPinyin = preferences.getBoolean(KEY_ABBREVIATED, true),
            fuzzyPinyinMask = preferences.getInt(KEY_FUZZY, 0).takeIf { it in 0..255 } ?: 0,
            chinesePunctuation = preferences.getBoolean(KEY_CHINESE_PUNCTUATION, true),
            candidatePageSize = preferences.getInt(KEY_PAGE_SIZE, 8).takeIf { it in ChineseInputOptions.PAGE_SIZES } ?: 8,
            keyboardLayout = if (preferences.getBoolean(KEY_NINE_KEY, false)) ChineseKeyboardLayout.NINE_KEY else ChineseKeyboardLayout.FULL,
        )
        set(value) {
            preferences.edit()
                .putBoolean(KEY_SIMPLIFIED, value.script == ChineseScript.SIMPLIFIED)
                .putBoolean(KEY_ABBREVIATED, value.abbreviatedPinyin)
                .putInt(KEY_FUZZY, value.fuzzyPinyinMask)
                .putBoolean(KEY_CHINESE_PUNCTUATION, value.chinesePunctuation)
                .putInt(KEY_PAGE_SIZE, value.candidatePageSize)
                .putBoolean(KEY_NINE_KEY, value.keyboardLayout == ChineseKeyboardLayout.NINE_KEY)
                .apply()
        }

    var chineseEngine: ChineseEngineChoice
        get() = ChineseEngineChoice.entries.firstOrNull { it.name == preferences.getString(KEY_CHINESE_ENGINE, null) }
            ?: ChineseEngineChoice.RIME
        set(value) { preferences.edit().putString(KEY_CHINESE_ENGINE, value.name).apply() }

    fun privacyConfiguration() = PrivacyConfiguration(
        learningEnabled = learningEnabled,
        incognitoMode = incognitoMode,
    )

    private fun edit(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    private companion object {
        const val FILE_NAME = "zeroinput-settings"
        const val KEY_LEARNING = "learning.enabled"
        const val KEY_INCOGNITO = "privacy.incognito"
        const val KEY_SECURE_CLIPBOARD = "secure-clipboard.enabled"
        const val KEY_HAPTICS = "keyboard.haptics"
        const val KEY_LANGUAGE = "keyboard.language"
        const val KEY_LANGUAGE_PACK = "keyboard.language-pack"
        const val KEY_SIMPLIFIED = "chinese.simplified"
        const val KEY_ABBREVIATED = "chinese.abbreviated"
        const val KEY_FUZZY = "chinese.fuzzy-mask"
        const val KEY_CHINESE_PUNCTUATION = "chinese.punctuation"
        const val KEY_PAGE_SIZE = "chinese.page-size"
        const val KEY_NINE_KEY = "chinese.nine-key"
        const val KEY_CHINESE_ENGINE = "chinese.engine"
    }
}
