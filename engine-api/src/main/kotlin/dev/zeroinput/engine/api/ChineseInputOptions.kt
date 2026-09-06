package dev.zeroinput.engine.api

enum class ChineseScript { SIMPLIFIED, TRADITIONAL }

enum class ChineseKeyboardLayout { FULL, NINE_KEY }

enum class FuzzyPinyinPair {
    Z_ZH, C_CH, S_SH, N_L, HU_FU, AN_ANG, EN_ENG, IN_ING,
}

/** A value snapshot; masks keep the configuration immutable across worker boundaries. */
data class ChineseInputOptions(
    val script: ChineseScript = ChineseScript.SIMPLIFIED,
    val abbreviatedPinyin: Boolean = true,
    val fuzzyPinyinMask: Int = 0,
    val chinesePunctuation: Boolean = true,
    val candidatePageSize: Int = 8,
    val keyboardLayout: ChineseKeyboardLayout = ChineseKeyboardLayout.FULL,
) {
    init {
        require(fuzzyPinyinMask in 0..255)
        require(candidatePageSize in PAGE_SIZES)
    }

    fun isFuzzyEnabled(pair: FuzzyPinyinPair): Boolean = fuzzyPinyinMask and (1 shl pair.ordinal) != 0

    fun withFuzzy(pair: FuzzyPinyinPair, enabled: Boolean): ChineseInputOptions {
        val bit = 1 shl pair.ordinal
        return copy(fuzzyPinyinMask = if (enabled) fuzzyPinyinMask or bit else fuzzyPinyinMask and bit.inv())
    }

    companion object {
        val PAGE_SIZES: List<Int> = listOf(5, 8, 10)
    }
}

enum class EngineCapability {
    CHINESE_SCRIPT, ABBREVIATED_PINYIN, FUZZY_PINYIN, PUNCTUATION_MODE, CANDIDATE_PAGE_SIZE, NINE_KEY_PINYIN,
}
