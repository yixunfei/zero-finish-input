package dev.zeroinput.engine.rime

import dev.zeroinput.engine.api.Candidate
import dev.zeroinput.engine.api.EditorContext
import dev.zeroinput.engine.api.EngineDescriptor
import dev.zeroinput.engine.api.EngineKey
import dev.zeroinput.engine.api.EngineSnapshot
import dev.zeroinput.engine.api.EngineUpdate
import dev.zeroinput.engine.api.InputEngine
import dev.zeroinput.engine.api.InputLanguage
import dev.zeroinput.engine.api.PageDirection

internal class FallbackPinyinEngine : InputEngine {
    private val input = StringBuilder()
    private var currentSnapshot = EngineSnapshot.Empty
    private var language = InputLanguage.CHINESE

    override val descriptor = EngineDescriptor(
        id = "zeroinput.pinyin-fallback",
        displayName = "内置全拼（降级）",
        version = "1",
        languages = setOf(InputLanguage.CHINESE),
        isFallback = true,
    )

    override val snapshot: EngineSnapshot
        get() = currentSnapshot

    override fun start(context: EditorContext): EngineSnapshot {
        language = context.language
        return reset()
    }

    override fun handle(key: EngineKey): EngineUpdate = when (key) {
        is EngineKey.Character -> handleCharacter(key.text)
        EngineKey.Backspace -> handleBackspace()
        EngineKey.Space -> commitBest(if (input.isEmpty()) " " else "")
        EngineKey.Enter -> handleEnter()
    }

    override fun selectCandidate(index: Int): EngineUpdate {
        val candidate = currentSnapshot.candidates.getOrNull(index)
            ?: return EngineUpdate(currentSnapshot, consumed = false)
        input.clear()
        currentSnapshot = EngineSnapshot.Empty
        return EngineUpdate(currentSnapshot, candidate.text)
    }

    override fun changePage(direction: PageDirection) = EngineUpdate(currentSnapshot, consumed = false)

    override fun reset(): EngineSnapshot {
        input.clear()
        currentSnapshot = EngineSnapshot.Empty
        return currentSnapshot
    }

    override fun close() {
        reset()
    }

    private fun handleCharacter(text: String): EngineUpdate {
        val normalized = normalizeCharacter(text)
        if (normalized != null) {
            input.append(normalized)
            currentSnapshot = createSnapshot()
            return EngineUpdate(currentSnapshot)
        }
        return commitBest(text)
    }

    /** Chinese pinyin is case-insensitive, including after an accidental Shift. */
    private fun normalizeCharacter(text: String): String? {
        if (text == "'") return text
        if (text.length != 1 || !text[0].isLetter()) return null
        val character = text[0]
        return when {
            character in 'a'..'z' -> character.toString()
            language == InputLanguage.CHINESE && character in 'A'..'Z' ->
                character.lowercaseChar().toString()
            else -> null
        }
    }

    private fun handleBackspace(): EngineUpdate {
        if (input.isEmpty()) return EngineUpdate(currentSnapshot, consumed = false)
        input.deleteCharAt(input.lastIndex)
        currentSnapshot = createSnapshot()
        return EngineUpdate(currentSnapshot)
    }

    private fun handleEnter(): EngineUpdate {
        val candidate = currentSnapshot.candidates.firstOrNull()
            ?: return EngineUpdate(currentSnapshot, consumed = false)
        input.clear()
        currentSnapshot = EngineSnapshot.Empty
        return EngineUpdate(currentSnapshot, committedText = candidate.text)
    }

    private fun commitBest(suffix: String): EngineUpdate {
        val value = currentSnapshot.candidates.firstOrNull()?.text ?: input.toString()
        input.clear()
        currentSnapshot = EngineSnapshot.Empty
        return EngineUpdate(currentSnapshot, value + suffix)
    }

    private fun createSnapshot(): EngineSnapshot {
        val raw = input.toString()
        if (raw.isEmpty()) return EngineSnapshot.Empty
        val exact = phrases[raw].orEmpty()
        val completions = phrases.asSequence()
            .filter { (key, _) -> key.startsWith(raw) }
            .flatMap { it.value.asSequence() }
        val candidates = exact.asSequence()
            .plus(completions)
            .distinct()
            .take(8)
            .mapIndexed { index, value -> Candidate("fallback:$index:$value", value, "内置") }
            .toList()
        return EngineSnapshot(raw, raw, candidates)
    }

    private companion object {
        val phrases = mapOf(
            "a" to listOf("啊", "阿"), "ai" to listOf("爱", "哎", "唉"),
            "an" to listOf("安", "按", "案"), "ba" to listOf("吧", "八", "把"),
            "bei" to listOf("被", "北", "杯"), "bu" to listOf("不", "部", "步"),
            "chi" to listOf("吃", "持", "迟"), "de" to listOf("的", "得", "德"),
            "dui" to listOf("对", "队"), "fang" to listOf("方", "放", "房"),
            "ge" to listOf("个", "各", "歌"), "gong" to listOf("公", "工", "共"),
            "hao" to listOf("好", "号", "浩"), "he" to listOf("和", "喝", "合"),
            "hen" to listOf("很", "恨"), "hui" to listOf("会", "回", "灰"),
            "ji" to listOf("机", "几", "及"), "jia" to listOf("家", "加", "假"),
            "jian" to listOf("见", "件", "间"), "jin" to listOf("进", "今", "近"),
            "ke" to listOf("可", "科", "客"), "lai" to listOf("来", "莱"),
            "le" to listOf("了", "乐"), "ma" to listOf("吗", "妈", "马"),
            "mei" to listOf("没", "每", "美"), "men" to listOf("们", "门"),
            "ming" to listOf("明", "名"), "ni" to listOf("你", "呢", "尼"),
            "ren" to listOf("人", "认", "任"), "shi" to listOf("是", "时", "事"),
            "shuo" to listOf("说", "硕"), "ta" to listOf("他", "她", "它"),
            "wo" to listOf("我", "握"), "xiang" to listOf("想", "向", "像"),
            "xie" to listOf("些", "写", "谢"), "yi" to listOf("一", "以", "已"),
            "you" to listOf("有", "又", "由"), "zai" to listOf("在", "再"),
            "zhe" to listOf("这", "着", "者"), "zhong" to listOf("中", "种", "重"),
            "nihao" to listOf("你好"), "xiexie" to listOf("谢谢"),
            "women" to listOf("我们"), "zhongguo" to listOf("中国"),
            "keyi" to listOf("可以"), "meiyou" to listOf("没有"),
            "zaijian" to listOf("再见"), "shijie" to listOf("世界"),
        )
    }
}
