package dev.zeroinput.engine.dictionary

import java.util.TreeMap

internal class ReferenceDictionary private constructor(private val entries: TreeMap<String, List<String>>) {
    fun lookup(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        val prefix = input.replace("'", "")
        if (prefix.isEmpty()) return emptyList()
        val results = LinkedHashSet<String>()
        for ((key, values) in entries.tailMap(prefix)) {
            if (!key.startsWith(prefix)) break
            for (value in values) {
                results += value
                if (results.size == MAX_RESULTS) return results.toList()
            }
        }
        return results.toList()
    }

    companion object {
        private const val MAX_RESULTS = 64

        fun load(): ReferenceDictionary {
            val stream = checkNotNull(ReferenceDictionary::class.java.getResourceAsStream("/reference-pinyin.tsv")) {
                "Reference dictionary is missing"
            }
            val entries = TreeMap<String, MutableList<String>>()
            stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.take(512).forEach { line ->
                    val columns = line.split('\t')
                    check(columns.size == 2 && columns[0].length in 1..64 && columns[0].all { it in 'a'..'z' }) {
                        "Invalid reference dictionary entry"
                    }
                    check(columns[1].length in 1..64 && columns[1].none(Char::isISOControl)) {
                        "Invalid reference dictionary value"
                    }
                    entries.getOrPut(columns[0]) { mutableListOf() }.add(columns[1])
                }
            }
            check(entries.isNotEmpty()) { "Reference dictionary is empty" }
            return ReferenceDictionary(TreeMap(entries.mapValues { it.value.toList() }))
        }
    }
}
