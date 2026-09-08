package dev.zeroinput.ime.input

/** Tracks acknowledgements of our own edits; unexpected cursor updates revoke the anchor. */
internal class EditorSelectionState(start: Int, end: Int) {
    data class Selection(val start: Int, val end: Int, val composingStart: Int = -1, val composingEnd: Int = -1)
    data class CommitRange(val start: Int, val end: Int)

    private var predicted = Selection(start, end)
    private var observed = predicted
    private var composingStart: Int? = null
    private val pending = ArrayDeque<Selection>()
    private var committed: CommitRange? = null

    fun replaced(length: Int, composing: Boolean) {
        committed = null
        if (predicted.start < 0 || predicted.end < 0) return
        val start = composingStart ?: minOf(predicted.start, predicted.end)
        predicted = Selection(start + length, start + length,
            if (composing && length > 0) start else -1, if (composing && length > 0) start + length else -1)
        if (pending.size == 32) {
            unknown()
            return
        }
        pending.addLast(predicted)
        composingStart = start.takeIf { composing && length > 0 }
        if (!composing && length > 0) committed = CommitRange(start, start + length)
    }

    fun updated(start: Int, end: Int, composingStart: Int = -1, composingEnd: Int = -1): Boolean {
        observed = Selection(start, end, composingStart, composingEnd)
        // Android coalesces updates. The composing range distinguishes a final
        // commit from an earlier preedit with the same cursor position.
        val expected = pending.lastIndexOf(observed)
        if (expected >= 0) {
            repeat(expected + 1) { pending.removeFirst() }
            return false
        }
        if (observed == predicted) return false
        predicted = observed
        this.composingStart = null
        pending.clear()
        committed = null
        return true
    }

    fun verifiedRange(length: Int): CommitRange? = committed?.takeIf {
        pending.isEmpty() && observed == Selection(it.end, it.end) && it.end - it.start == length
    }

    fun reopened(range: CommitRange) {
        committed = null
        composingStart = range.start
    }

    fun finishComposition() { composingStart = null }
    fun invalidate() { committed = null }
    fun unknown() {
        committed = null
        composingStart = null
        pending.clear()
        predicted = Selection(-1, -1)
        observed = predicted
    }
}
