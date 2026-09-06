package dev.zeroinput.engine.dictionary

import dev.zeroinput.engine.api.ChineseInputOptions
import dev.zeroinput.engine.api.ConfigurableChineseEngineFactory
import dev.zeroinput.engine.api.EngineCapability
import dev.zeroinput.engine.api.EngineDescriptor
import dev.zeroinput.engine.api.InputEngine
import dev.zeroinput.engine.api.InputLanguage

/** Small Apache-2.0 reference implementation for comparing the input pipeline. */
class DictionaryEngineFactory : ConfigurableChineseEngineFactory {
    private val dictionary by lazy { ReferenceDictionary.load() }
    override val descriptor: EngineDescriptor = Descriptor
    override fun isAvailable(): Boolean = true
    override fun create(): InputEngine = create(ChineseInputOptions())

    /** Loads the bundled resource on the engine preparation worker. */
    override fun create(options: ChineseInputOptions): InputEngine = DictionaryInputEngine(dictionary, options)

    companion object {
        val Descriptor = EngineDescriptor(
            id = "zeroinput.dictionary-test",
            displayName = "ZeroInput Dictionary Test",
            version = "1",
            languages = setOf(InputLanguage.CHINESE),
            capabilities = setOf(EngineCapability.CANDIDATE_PAGE_SIZE, EngineCapability.PUNCTUATION_MODE),
        )
    }
}
