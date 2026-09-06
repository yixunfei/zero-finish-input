package dev.zeroinput.engine.english

import dev.zeroinput.engine.api.EditorContext
import dev.zeroinput.engine.api.EngineKey
import dev.zeroinput.engine.api.InputLanguage
import dev.zeroinput.engine.api.LearnedSuggestionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishInputEngineTest {
    private val context = EditorContext(
        language = InputLanguage.ENGLISH,
        isSensitive = false,
        learningAllowed = true,
        packageName = "dev.example",
    )

    @Test
    fun `typing offers matching words and preserves composition`() {
        val engine = EnglishInputEngine()
        engine.start(context)

        "hel".forEach { engine.handle(EngineKey.Character(it.toString())) }

        assertEquals("hel", engine.snapshot.composition)
        assertTrue(engine.snapshot.candidates.any { it.text == "hello" })
    }

    @Test
    fun `space commits current word and clears composition`() {
        val engine = EnglishInputEngine()
        engine.start(context)
        "hello".forEach { engine.handle(EngineKey.Character(it.toString())) }

        val update = engine.handle(EngineKey.Space)

        assertEquals("hello ", update.committedText)
        assertFalse(update.snapshot.isComposing)
    }

    @Test
    fun `backspace with empty composition is not consumed`() {
        val engine = EnglishInputEngine()
        engine.start(context)

        assertFalse(engine.handle(EngineKey.Backspace).consumed)
    }

    @Test
    fun `disabled learning does not query or expose learned suggestions`() {
        var queried = false
        val engine = EnglishInputEngine(
            learnedSuggestions = LearnedSuggestionSource { _, _ ->
                queried = true
                listOf(dev.zeroinput.engine.api.WeightedTerm("private", 100))
            },
        )
        engine.start(context.copy(learningAllowed = false))

        "pri".forEach { engine.handle(EngineKey.Character(it.toString())) }

        assertFalse(queried)
        assertTrue(engine.snapshot.candidates.none { it.text == "private" })
    }
}
