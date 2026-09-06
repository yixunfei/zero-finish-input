package dev.zeroinput.languagepack

import dev.zeroinput.engine.api.InputLanguage

/**
 * Maps BCP-47 language tags to the input languages currently implemented by
 * ZeroInput.  Keeping this mapping in the language-pack module prevents the
 * settings UI and registry from silently making different assumptions about
 * an imported package.
 */
object LanguagePackLanguage {
    fun fromTag(tag: String): InputLanguage? = when (
        tag.substringBefore('-').lowercase()
    ) {
        "zh" -> InputLanguage.CHINESE
        "en" -> InputLanguage.ENGLISH
        else -> null
    }
}
