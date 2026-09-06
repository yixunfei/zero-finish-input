package dev.zeroinput.engine.rime

import dev.zeroinput.engine.api.ChineseInputOptions
import dev.zeroinput.engine.api.ChineseScript
import dev.zeroinput.engine.api.FuzzyPinyinPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinAlgebraTest {
    @Test
    fun `default enables abbreviations without widening exact syllables`() {
        val rules = PinyinAlgebra.rules(ChineseInputOptions())
        assertTrue(rules.isNotEmpty())
        assertTrue(rules.all { it.startsWith("abbrev/") })
    }

    @Test
    fun `each fuzzy pair is independently reversible`() {
        val initial = ChineseInputOptions(abbreviatedPinyin = false)
        for (pair in FuzzyPinyinPair.entries) {
            val enabled = initial.withFuzzy(pair, true)
            assertEquals(2, PinyinAlgebra.rules(enabled).size)
            assertTrue(enabled.isFuzzyEnabled(pair))
            assertFalse(initial.isFuzzyEnabled(pair))
            assertEquals(initial, enabled.withFuzzy(pair, false))
            assertNotEquals(PinyinAlgebra.schemaId(initial), PinyinAlgebra.schemaId(enabled))
        }
    }

    @Test
    fun `display switches do not rebuild the syllable index`() {
        val initial = ChineseInputOptions()
        val switched = initial.copy(script = ChineseScript.TRADITIONAL, chinesePunctuation = false)
        assertEquals(PinyinAlgebra.schemaId(initial), PinyinAlgebra.schemaId(switched))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown fuzzy bits fail closed`() {
        ChineseInputOptions(fuzzyPinyinMask = 256)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unbounded candidate pages are rejected`() {
        ChineseInputOptions(candidatePageSize = 1000)
    }
}
