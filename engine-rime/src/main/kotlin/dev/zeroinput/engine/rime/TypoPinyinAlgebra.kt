package dev.zeroinput.engine.rime

/** One edit per syllable. Marked results cannot feed another derivation. */
internal object TypoPinyinAlgebra {
    fun rules(): List<String> = buildList {
        for (position in 0..5) {
            val prefix = "([a-z]{$position})"
            for ((letter, neighbors) in adjacent) for (neighbor in neighbors) {
                add("fuzz/^$prefix$letter([a-z]*)\u0024/~\u00241$neighbor\u00242/")
            }
            add("fuzz/^$prefix([a-z])([a-z])([a-z]*)\u0024/~\u00241\u00243\u00242\u00244/")
            add("fuzz/^(?=[a-z]{3,}\u0024)$prefix[a-z]([a-z]*)\u0024/~\u00241\u00242/")
            add("fuzz/^$prefix([a-z])([a-z]*)\u0024/~\u00241\u00242\u00242\u00243/")
        }
        add("xform/^~//")
    }

    private val adjacent = mapOf(
        'q' to "wa", 'w' to "qeas", 'e' to "wrsd", 'r' to "etdf", 't' to "ryfg",
        'y' to "tugh", 'u' to "yihj", 'i' to "uojk", 'o' to "ipkl", 'p' to "ol",
        'a' to "qwsz", 's' to "awedxz", 'd' to "serfcx", 'f' to "drtgvc", 'g' to "ftyhbv",
        'h' to "gyujnb", 'j' to "huikmn", 'k' to "jiolm", 'l' to "kop",
        'z' to "asx", 'x' to "zsdc", 'c' to "xdfv", 'v' to "cfgb", 'b' to "vghn",
        'n' to "bhjm", 'm' to "njk",
    )
}
