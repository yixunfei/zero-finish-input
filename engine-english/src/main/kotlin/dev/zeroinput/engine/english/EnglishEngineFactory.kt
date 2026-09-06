package dev.zeroinput.engine.english

import dev.zeroinput.engine.api.EngineDescriptor
import dev.zeroinput.engine.api.InputEngine
import dev.zeroinput.engine.api.InputEngineFactory
import dev.zeroinput.engine.api.LearnedSuggestionSource

class EnglishEngineFactory(
    private val learnedSuggestions: LearnedSuggestionSource = LearnedSuggestionSource { _, _ -> emptyList() },
) : InputEngineFactory {
    override val descriptor: EngineDescriptor = EnglishInputEngine.Descriptor

    override fun isAvailable(): Boolean = true

    override fun create(): InputEngine = EnglishInputEngine(learnedSuggestions)
}

