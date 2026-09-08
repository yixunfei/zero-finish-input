package dev.zeroinput.ime.core

/** Optional editor capability. Implementations must verify identity, cursor and exact text. */
interface ReconversionEditorConnection {
    /** Marks only the last unchanged committed range as composing; otherwise changes nothing. */
    fun reopenCommittedText(expectedText: String): Boolean

    fun invalidateReconversion()
}
