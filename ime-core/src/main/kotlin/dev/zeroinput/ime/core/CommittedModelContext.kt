package dev.zeroinput.ime.core

/** Only successful, contiguous IME commits enter this ephemeral, wipeable buffer. */
class CommittedModelContext {
    private val text = CharArray(ModelRankingPolicy.CONTEXT_LIMIT)
    private var length = 0

    fun append(value: String) {
        // A separator or unsupported character starts a new context, even inside a commit.
        for (index in maxOf(0, value.length - text.size) until value.length) {
            val character = value[index]
            if (!ModelRankingPolicy.isChinese(character)) { clear(); continue }
            if (length == text.size) {
                text.copyInto(text, 0, 1)
                length--
            }
            text[length++] = character
        }
    }

    fun copy(): CharArray = text.copyOf(length)
    fun clear() { text.fill('\u0000'); length = 0 }
}
