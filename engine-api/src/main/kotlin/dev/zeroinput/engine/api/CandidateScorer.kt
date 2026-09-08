package dev.zeroinput.engine.api

/** Worker-confined scorer. Inputs are borrowed for this call and must never be retained. */
interface CandidateScorer : AutoCloseable {
    fun score(context: CharArray, words: Array<CharArray>, cancelled: () -> Boolean): FloatArray?
}
