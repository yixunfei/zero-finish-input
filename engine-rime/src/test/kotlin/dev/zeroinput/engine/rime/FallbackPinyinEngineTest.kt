package dev.zeroinput.engine.rime

import dev.zeroinput.engine.api.EditorContext
import dev.zeroinput.engine.api.EngineKey
import dev.zeroinput.engine.api.InputLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackPinyinEngineTest {
    @Test fun `input beyond the composition bound is returned to the controller without silent consumption`() {
        val engine = FallbackPinyinEngine()
        engine.restoreComposition("a".repeat(128))
        val update = engine.handle(EngineKey.Character("b"))
        assertFalse(update.consumed)
        assertEquals(128, update.snapshot.rawInput.length)
    }

    @Test fun `paging reaches every prefix result and stops without repeats`() {
        val engine = FallbackPinyinEngine()
        engine.restoreComposition("z")
        val seen = mutableSetOf<String>()
        var pages = 0
        do {
            for (candidate in engine.snapshot.candidates) assertTrue(seen.add(candidate.text))
            pages++
        } while (engine.changePage(dev.zeroinput.engine.api.PageDirection.NEXT).consumed)
        assertTrue(pages > 1)
        assertTrue(seen.size > 8)
        assertFalse(engine.snapshot.hasNextPage)
    }

    @Test fun `unknown phrase supports segment undo and complete canonical learning`() {
        val engine = FallbackPinyinEngine()
        engine.restoreComposition("ni'ai'hao")
        engine.selectSyllable()
        engine.selectCandidate(engine.snapshot.candidates.indexOfFirst { it.text == "你" })
        assertEquals("你ai'hao", engine.snapshot.composition)
        assertTrue(engine.snapshot.canUndoSelection)
        engine.undoSelection()
        assertEquals("ni'ai'hao", engine.snapshot.composition)
        engine.selectSyllable()
        engine.selectCandidate(engine.snapshot.candidates.indexOfFirst { it.text == "你" })
        engine.selectCandidate(engine.snapshot.candidates.indexOfFirst { it.text == "爱" })
        val update = engine.selectCandidate(engine.snapshot.candidates.indexOfFirst { it.text == "浩" })
        assertEquals("你爱浩", update.committedText)
        assertEquals("niaihao", update.committedInput)
        assertTrue(update.learnable)
        assertFalse(update.snapshot.isComposing)
    }

    private val context = EditorContext(
        language = InputLanguage.CHINESE,
        isSensitive = false,
        learningAllowed = true,
        packageName = "test",
    )

    @Test
    fun `enter commits a candidate without manufacturing an editor newline`() {
        val engine = FallbackPinyinEngine()
        engine.start(context)
        "nihao".forEach { engine.handle(EngineKey.Character(it.toString())) }

        val update = engine.handle(EngineKey.Enter)

        assertTrue(update.consumed)
        assertEquals("你好", update.committedText)
        assertFalse(update.snapshot.isComposing)
    }

    @Test
    fun `enter declines unknown composition so controller can commit it and send enter`() {
        val engine = FallbackPinyinEngine()
        engine.start(context)
        "qz".forEach { engine.handle(EngineKey.Character(it.toString())) }

        val update = engine.handle(EngineKey.Enter)

        assertFalse(update.consumed)
        assertEquals("qz", update.snapshot.rawInput)
        assertTrue(update.snapshot.isComposing)
    }

    @Test
    fun `uppercase pinyin remains composing in Chinese mode`() {
        val engine = FallbackPinyinEngine()
        engine.start(context)

        "NI".forEach { engine.handle(EngineKey.Character(it.toString())) }

        assertEquals("ni", engine.snapshot.rawInput)
        assertTrue(engine.snapshot.isComposing)
        assertEquals("你", engine.handle(EngineKey.Space).committedText)
    }

    @Test
    fun `space confirms composition and only inserts whitespace when idle`() {
        val engine = FallbackPinyinEngine()
        engine.start(context)
        "nihao".forEach { engine.handle(EngineKey.Character(it.toString())) }

        val update = engine.handle(EngineKey.Space)

        assertEquals("你好", update.committedText)
        assertFalse(update.snapshot.isComposing)
        assertEquals(" ", engine.handle(EngineKey.Space).committedText)
    }
}
