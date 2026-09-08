package dev.zeroinput.ime.ui

/** Reuses the same commands in three rows when the landscape window is short. */
internal object CompactKeyboardLayout {
    fun rows(source: List<List<KeySpec>>): List<List<KeySpec>> {
        if (source.size != 4) return source
        if (source[0].size == 10 && source[3].size == 6) {
            val controls = source[3]
            return listOf(
                source[0] + source[2].last() + controls.last(),
                source[1] + source[2].first() + controls[2] + controls[4],
                source[2].drop(1).dropLast(1) + controls.take(2) + controls[3],
            )
        }
        val keys = source.flatten()
        val rowSize = (keys.size + 2) / 3
        return keys.chunked(rowSize)
    }
}
