package dev.zeroinput.engine.rime

import dev.zeroinput.engine.api.ChineseInputOptions
import dev.zeroinput.engine.api.ChineseKeyboardLayout
import org.junit.Assert.*
import org.junit.Test

class TypoPinyinAlgebraTest {
    @Test fun `typo matching is opt in and does not change the nine key spelling index`() {
        val normal = ChineseInputOptions()
        assertFalse(normal.experimentalTypoCorrection)
        assertFalse(PinyinAlgebra.rules(normal).any { it.startsWith("fuzz/") })
        assertNotEquals(PinyinAlgebra.schemaId(normal), PinyinAlgebra.schemaId(normal.copy(experimentalTypoCorrection = true)))
        val nine = normal.copy(keyboardLayout = ChineseKeyboardLayout.NINE_KEY)
        assertEquals(PinyinAlgebra.rules(nine), PinyinAlgebra.rules(nine.copy(experimentalTypoCorrection = true)))
        assertEquals(PinyinAlgebra.schemaId(nine), PinyinAlgebra.schemaId(nine.copy(experimentalTypoCorrection = true)))
    }

    @Test fun `single edits cover adjacent letters swapped letters omissions and repeats without cascading`() {
        val forms = forms("hao")
        assertTrue("hso" in forms)
        assertTrue("hoa" in forms)
        assertTrue("ha" in forms)
        assertTrue("haao" in forms)
        assertFalse("jso" in forms)
        assertTrue(forms.size < 40)
    }

    private fun forms(value: String): Set<String> {
        var forms = setOf(value)
        for (rule in TypoPinyinAlgebra.rules()) {
            val parts = rule.split('/')
            val expression = Regex(parts[1])
            forms = if (parts[0] == "fuzz") forms + forms.map { expression.replace(it, parts[2]) }
                else forms.map { expression.replace(it, parts[2]) }.toSet()
        }
        return forms
    }
}
