package dev.zeroinput.engine.api

enum class InputLanguage {
    CHINESE,
    ENGLISH,
}

data class EngineDescriptor(
    val id: String,
    val displayName: String,
    val version: String,
    val languages: Set<InputLanguage>,
    val isFallback: Boolean = false,
    val capabilities: Set<EngineCapability> = emptySet(),
)

data class EditorContext(
    val language: InputLanguage,
    val isSensitive: Boolean,
    val learningAllowed: Boolean,
    val packageName: String?,
)

data class Candidate(
    val id: String,
    val text: String,
    val comment: String = "",
    val score: Int = 0,
)

data class EngineSnapshot(
    val rawInput: String = "",
    val composition: String = "",
    val candidates: List<Candidate> = emptyList(),
    val highlightedIndex: Int = 0,
    val hasPreviousPage: Boolean = false,
    val hasNextPage: Boolean = false,
    val readings: List<String> = emptyList(),
) {
    val isComposing: Boolean
        get() = rawInput.isNotEmpty() || composition.isNotEmpty()

    companion object {
        val Empty = EngineSnapshot()
    }
}

data class EngineUpdate(
    val snapshot: EngineSnapshot,
    val committedText: String = "",
    val consumed: Boolean = true,
)

sealed interface EngineKey {
    data class Character(val text: String) : EngineKey

    data object Backspace : EngineKey

    data object Space : EngineKey

    data object Enter : EngineKey
}

enum class PageDirection {
    PREVIOUS,
    NEXT,
}
