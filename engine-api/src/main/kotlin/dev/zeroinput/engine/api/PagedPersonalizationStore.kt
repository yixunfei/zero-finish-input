package dev.zeroinput.engine.api

data class PersonalSuggestionPage(
    val items: List<PersonalSuggestion> = emptyList(),
    val hasMore: Boolean = false,
    val ready: Boolean = true,
    val revision: Long = 0,
)

/** Offset is relative to one stable, deterministically ranked query revision. */
interface PagedPersonalizationStore : PersonalizationStore {
    fun suggestionPage(prefix: String, language: InputLanguage, offset: Int, limit: Int): PersonalSuggestionPage
}
