package dev.zeroinput.engine.rime

import dev.zeroinput.engine.api.EditorContext
import dev.zeroinput.engine.api.EngineKey
import dev.zeroinput.engine.api.InputLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackPinyinEngineTest {
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
