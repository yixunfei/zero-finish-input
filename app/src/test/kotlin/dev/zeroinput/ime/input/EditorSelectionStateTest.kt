package dev.zeroinput.ime.input

import org.junit.Assert.*
import org.junit.Test

class EditorSelectionStateTest {
    @Test fun `commit replaces the complete composition and waits for cursor acknowledgement`() {
        val state = EditorSelectionState(4, 4)
        state.replaced(2, composing = true)
        state.replaced(6, composing = true)
        state.replaced(2, composing = false)
        assertNull(state.verifiedRange(2))
        state.updated(6, 6, 4, 6)
        state.updated(10, 10, 4, 10)
        state.updated(6, 6)
        assertEquals(EditorSelectionState.CommitRange(4, 6), state.verifiedRange(2))
    }

    @Test fun `coalesced final callback acknowledges earlier preedit at the same position`() {
        val state = EditorSelectionState(0, 0)
        state.replaced(2, composing = true)
        state.replaced(5, composing = true)
        state.replaced(2, composing = false)
        assertFalse(state.updated(2, 2))
        assertEquals(EditorSelectionState.CommitRange(0, 2), state.verifiedRange(2))
    }

    @Test fun `cursor move selection invalidation and unknown initial location reject reopening`() {
        val state = EditorSelectionState(0, 0)
        state.replaced(2, composing = false)
        state.updated(2, 2)
        assertNotNull(state.verifiedRange(2))
        assertTrue(state.updated(0, 0))
        state.updated(2, 2)
        assertNull(state.verifiedRange(2))
        val unknown = EditorSelectionState(-1, -1)
        unknown.replaced(2, composing = false)
        assertNull(unknown.verifiedRange(2))
    }

    @Test fun `acknowledged batched edits can reopen but another edit revokes the old range`() {
        val state = EditorSelectionState(1, 4)
        state.replaced(3, composing = true)
        state.replaced(2, composing = false)
        state.updated(3, 3)
        val range = checkNotNull(state.verifiedRange(2))
        state.reopened(range)
        state.replaced(7, composing = true)
        state.updated(8, 8, 1, 8)
        assertNull(state.verifiedRange(2))
        state.replaced(2, composing = false)
        state.updated(3, 3)
        assertEquals(EditorSelectionState.CommitRange(1, 3), state.verifiedRange(2))
        state.invalidate()
        assertNull(state.verifiedRange(2))
    }
}
