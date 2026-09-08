package dev.zeroinput.engine.rime

import dev.zeroinput.engine.api.*

/** Two prepared sessions share public dictionary data; only explicit selection changes reading. */
internal class ExpandingRimeEngine(
    private var primary: RimeInputEngine,
    private var related: RimeInputEngine,
    private val readings: RelatedReadings,
) : InputEngine, CompositionEditingEngine, ReadingSelectionEngine, CandidateTextNormalizer {
    private var alternatives: List<String>? = null
    private val lastPages = HashMap<Int, Int>()
    private var source = -1
    private var page = 0
    override val descriptor get() = primary.descriptor
    override var snapshot = EngineSnapshot.Empty
        private set

    override fun start(context: EditorContext): EngineSnapshot {
        related.start(context)
        primary.start(context)
        return reset()
    }

    override fun handle(key: EngineKey): EngineUpdate {
        if (source >= 0 && key == EngineKey.Space && snapshot.candidates.isNotEmpty()) return selectCandidate(0)
        clearExpansion()
        return publish(primary.handle(key))
    }

    override fun selectCandidate(index: Int): EngineUpdate {
        if (index !in snapshot.candidates.indices) return EngineUpdate(snapshot, consumed = false)
        if (source >= 0) {
            val old = primary
            primary = related
            related = old
        }
        clearExpansion()
        return publish(primary.selectCandidate(index))
    }

    override fun changePage(direction: PageDirection): EngineUpdate {
        if (source < 0) {
            if (direction == PageDirection.PREVIOUS || primary.snapshot.hasNextPage) {
                return publish(primary.changePage(direction))
            }
            if (!eligible()) return EngineUpdate(snapshot, consumed = false)
            if (alternatives == null) alternatives = readings.alternatives(primary.snapshot.rawInput)
            return moveSource(0, backwards = false)
        }
        if (direction == PageDirection.PREVIOUS && page == 0) return moveSource(source - 1, backwards = true)
        if (direction == PageDirection.NEXT && !related.snapshot.hasNextPage) {
            lastPages[source] = page
            return moveSource(source + 1, backwards = false)
        }
        page += if (direction == PageDirection.NEXT) 1 else -1
        val update = related.browsePage(page)
        return publishRelated(update.consumed)
    }

    private fun moveSource(target: Int, backwards: Boolean): EngineUpdate {
        if (target < 0) { source = -1; return publish(EngineUpdate(primary.snapshot)) }
        val values = alternatives.orEmpty()
        var next = target
        while (next in values.indices) {
            related.restoreComposition(values[next])
            val targetPage = if (backwards) lastPages[next] ?: 0 else 0
            val update = related.browsePage(targetPage)
            if (update.consumed) {
                source = next
                page = targetPage
                return publishRelated(true)
            }
            next += if (backwards) -1 else 1
        }
        if (backwards) { source = -1; return publish(EngineUpdate(primary.snapshot)) }
        if (source >= 0) {
            related.restoreComposition(values[source])
            related.browsePage(page)
        }
        snapshot = snapshot.copy(hasNextPage = false)
        return EngineUpdate(snapshot, consumed = false)
    }

    private fun publishRelated(consumed: Boolean): EngineUpdate {
        val result = related.snapshot
        snapshot = primary.snapshot.copy(
            candidates = result.candidates.map { it.copy(id = "related:$source:${it.id}", kind = CandidateKind.RELATED_READING) },
            highlightedIndex = result.highlightedIndex,
            hasPreviousPage = true,
            hasNextPage = result.hasNextPage || source + 1 < alternatives.orEmpty().size,
        )
        return EngineUpdate(snapshot, consumed = consumed)
    }

    private fun eligible(): Boolean = primary.snapshot.rawInput.length in 2..64 &&
        !primary.snapshot.canUndoSelection && primary.snapshot.rawInput.all { it in 'a'..'z' || it == '\'' }

    private fun publish(update: EngineUpdate): EngineUpdate {
        snapshot = update.snapshot.copy(hasNextPage = update.snapshot.hasNextPage ||
            eligible() && (alternatives == null || !alternatives.isNullOrEmpty()))
        return update.copy(snapshot = snapshot)
    }

    private fun clearExpansion() {
        if (related.snapshot.isComposing) related.reset()
        alternatives = null
        lastPages.clear()
        source = -1
        page = 0
    }

    override fun restoreComposition(input: String): EngineUpdate { clearExpansion(); return publish(primary.restoreComposition(input)) }
    override fun undoSelection(): EngineUpdate { clearExpansion(); return publish(primary.undoSelection()) }
    override fun selectSyllable(): EngineUpdate { clearExpansion(); return publish(primary.selectSyllable()) }
    override fun selectReading(index: Int): EngineUpdate { clearExpansion(); return publish(primary.selectReading(index)) }
    override fun normalizeCandidateText(text: String): String = primary.normalizeCandidateText(text)
    override fun reset(): EngineSnapshot { clearExpansion(); primary.reset(); snapshot = EngineSnapshot.Empty; return snapshot }
    override fun close() { try { primary.close() } finally { related.close(); alternatives = null; lastPages.clear(); snapshot = EngineSnapshot.Empty } }
}
