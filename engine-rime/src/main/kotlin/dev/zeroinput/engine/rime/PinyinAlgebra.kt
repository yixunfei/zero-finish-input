package dev.zeroinput.engine.rime

import dev.zeroinput.engine.api.ChineseInputOptions
import dev.zeroinput.engine.api.FuzzyPinyinPair
import dev.zeroinput.engine.api.ChineseKeyboardLayout

/** Rime compiles these rules into its syllable index, outside the input thread. */
internal object PinyinAlgebra {
    fun rules(options: ChineseInputOptions): List<String> = buildList {
        if (options.experimentalTypoCorrection && options.keyboardLayout == ChineseKeyboardLayout.FULL) {
            addAll(TypoPinyinAlgebra.rules())
        }
        for (pair in FuzzyPinyinPair.entries) {
            if (options.isFuzzyEnabled(pair)) addAll(fuzzyRules.getValue(pair))
        }
        if (options.abbreviatedPinyin) {
            add("abbrev/^([a-z]).+\u0024/\u00241/")
            add("abbrev/^([zcs]h).+\u0024/\u00241/")
        }
        if (options.keyboardLayout == ChineseKeyboardLayout.NINE_KEY) {
            for ((index, group) in listOf("abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz").withIndex()) {
                add("derive/[$group]/${index + 2}/")
            }
            // Retain both exact Latin syllables and numeric codes, discarding intermediate mixtures.
            add("erase/^(?=.*[a-z])(?=.*[2-9]).+\u0024/")
        }
    }

    fun schemaId(options: ChineseInputOptions): String =
        if (options.fuzzyPinyinMask == 0 && options.abbreviatedPinyin && options.candidatePageSize == 8 &&
            options.keyboardLayout == ChineseKeyboardLayout.FULL && !options.experimentalTypoCorrection) {
            "zeroinput_pinyin"
        } else {
            "zeroinput_pinyin_${options.fuzzyPinyinMask}_${if (options.abbreviatedPinyin) 1 else 0}_${options.candidatePageSize}" +
                (if (options.keyboardLayout == ChineseKeyboardLayout.NINE_KEY) "_9" else "") +
                (if (options.experimentalTypoCorrection && options.keyboardLayout == ChineseKeyboardLayout.FULL) "_2" else "")
        }

    private val fuzzyRules = mapOf(
        FuzzyPinyinPair.Z_ZH to listOf("derive/^zh/z/", "derive/^z([^h])/zh\u00241/"),
        FuzzyPinyinPair.C_CH to listOf("derive/^ch/c/", "derive/^c([^h])/ch\u00241/"),
        FuzzyPinyinPair.S_SH to listOf("derive/^sh/s/", "derive/^s([^h])/sh\u00241/"),
        FuzzyPinyinPair.N_L to listOf("derive/^n/l/", "derive/^l/n/"),
        FuzzyPinyinPair.HU_FU to listOf("derive/^hu\u0024/fu/", "derive/^fu\u0024/hu/"),
        FuzzyPinyinPair.AN_ANG to listOf("derive/an\u0024/ang/", "derive/ang\u0024/an/"),
        FuzzyPinyinPair.EN_ENG to listOf("derive/en\u0024/eng/", "derive/eng\u0024/en/"),
        FuzzyPinyinPair.IN_ING to listOf("derive/in\u0024/ing/", "derive/ing\u0024/in/"),
    )
}
