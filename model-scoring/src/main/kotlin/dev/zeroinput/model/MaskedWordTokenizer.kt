package dev.zeroinput.model

import java.io.Reader

/** Character IDs from the pinned vocabulary; unsupported input fails closed. */
internal class MaskedWordTokenizer(reader: Reader) {
    private val characters = IntArray(0x10000) { -1 }

    init {
        reader.buffered().use { source ->
            var count = 0
            while (true) {
                val token = source.readLine() ?: break
                require(count < 21_128 && token.length <= 128) { "Invalid model vocabulary" }
                if (token.length == 1) characters[token[0].code] = count
                when (count) {
                    101 -> require(token == "[CLS]") { "Invalid model vocabulary" }
                    102 -> require(token == "[SEP]") { "Invalid model vocabulary" }
                    103 -> require(token == "[MASK]") { "Invalid model vocabulary" }
                }
                count++
            }
            require(count == 21_128) { "Invalid model vocabulary" }
        }
    }

    class Batch(val rows: Int, val length: Int) : AutoCloseable {
        val ids = LongArray(rows * length)
        val positions = LongArray(rows)
        val targets = LongArray(rows)
        override fun close() { ids.fill(0); positions.fill(0); targets.fill(0) }
    }

    fun encode(context: CharArray, words: Array<CharArray>): Batch? {
        if (context.size !in 1..16 || words.size !in 2..8 || words.any { it.size != 2 }) return null
        if (context.any { !supported(it) } || words.any { word -> word.any { !supported(it) } }) return null
        val batch = Batch(words.size * 2, context.size + 4)
        words.forEachIndexed { index, word ->
            repeat(2) { masked ->
                val row = index * 2 + masked
                val offset = row * batch.length
                batch.ids[offset] = 101
                context.forEachIndexed { position, character -> batch.ids[offset + position + 1] = id(character) }
                repeat(2) { position -> batch.ids[offset + context.size + 1 + position] = id(word[position]) }
                val position = context.size + 1 + masked
                batch.ids[offset + position] = 103
                batch.ids[offset + batch.length - 1] = 102
                batch.positions[row] = position.toLong()
                batch.targets[row] = id(word[masked])
            }
        }
        return batch
    }

    private fun supported(character: Char) = character in '\u4e00'..'\u9fff' && characters[character.code] >= 0
    private fun id(character: Char) = characters[character.code].toLong()
}
