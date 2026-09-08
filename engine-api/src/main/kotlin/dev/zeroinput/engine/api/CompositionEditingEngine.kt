package dev.zeroinput.engine.api

/** Edits engine-owned composition without reading or modifying an editor. */
interface CompositionEditingEngine {
    fun restoreComposition(input: String): EngineUpdate

    fun undoSelection(): EngineUpdate

    fun selectSyllable(): EngineUpdate
}
