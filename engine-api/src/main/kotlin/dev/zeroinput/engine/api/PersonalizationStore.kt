package dev.zeroinput.engine.api

data class PersonalSuggestion(
    val id: String,
    val text: String,
    val frequency: Int,
    val input: String = "",
)

interface PersonalizationStore {
    fun suggestionsFor(prefix: String, language: InputLanguage, limit: Int): List<PersonalSuggestion>

    fun learn(
        shortcut: String,
        value: String,
        language: InputLanguage,
        learningAllowed: Boolean,
    )

    fun recordUse(id: String, learningAllowed: Boolean)

    /**
     * Invalidates writes that were accepted before the current privacy
     * policy became stricter.  Implementations that do not queue work may
     * keep the default no-op behavior.
     */
    fun invalidatePendingWrites() = Unit

    /** Clears all personal entries.  Implementations may perform this asynchronously. */
    fun clear() = Unit
}
