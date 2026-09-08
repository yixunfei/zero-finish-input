package dev.zeroinput.ime.core

import dev.zeroinput.engine.api.Candidate
import dev.zeroinput.engine.api.CandidateKind
import dev.zeroinput.engine.api.EngineSnapshot
import dev.zeroinput.engine.api.InputLanguage

/** Frozen policy evaluated with public Rime fixtures; never changes candidate identity or reading. */
object ModelRankingPolicy {
    const val CONTEXT_LIMIT = 16
    const val CANDIDATE_LIMIT = 8
    const val WINNING_MARGIN = 1.25f

    fun eligible(state: InputSessionState): List<Candidate> {
        val snapshot = state.snapshot
        if (state.language != InputLanguage.CHINESE || !state.privacy.personalizationAllowed ||
            state.privacy.isSensitive || state.languagePackKey != null || state.modelRanked ||
            snapshot.canUndoSelection || snapshot.hasPreviousPage || snapshot.highlightedIndex != 0 ||
            snapshot.rawInput.isEmpty() || snapshot.rawInput.length > 32 ||
            snapshot.rawInput.any { it !in 'a'..'z' } ||
            snapshot.candidates.any { it.id.startsWith("personal:") || it.kind != CandidateKind.STANDARD }) return emptyList()
        val candidates = snapshot.candidates.take(CANDIDATE_LIMIT).filter {
            it.input == snapshot.rawInput && it.text.length == 2 && it.text.all(::isChinese)
        }
        return candidates.takeIf { it.size >= 2 && it.first() == snapshot.candidates.firstOrNull() }.orEmpty()
    }

    fun winner(scores: FloatArray): Int? {
        if (scores.size !in 2..CANDIDATE_LIMIT || scores.any { !it.isFinite() }) return null
        val order = scores.indices.sortedByDescending { scores[it] }
        return order[0].takeIf { scores[it] - scores[order[1]] >= WINNING_MARGIN }
    }

    fun promote(snapshot: EngineSnapshot, id: String): EngineSnapshot? {
        val index = snapshot.candidates.indexOfFirst { it.id == id }
        if (index <= 0) return null
        val candidates = snapshot.candidates.toMutableList()
        candidates.add(0, candidates.removeAt(index))
        return snapshot.copy(candidates = candidates, highlightedIndex = 0)
    }

    fun isChinese(value: Char): Boolean = value in '\u4e00'..'\u9fff'
}
