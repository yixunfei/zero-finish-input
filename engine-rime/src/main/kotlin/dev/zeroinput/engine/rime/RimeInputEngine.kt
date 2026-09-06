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
import dev.zeroinput.engine.api.CandidateTextNormalizer
import dev.zeroinput.engine.api.ChineseInputOptions
import dev.zeroinput.engine.api.ChineseScript
import dev.zeroinput.engine.api.EngineCapability
import dev.zeroinput.engine.api.ReadingSelectionEngine

internal class RimeInputEngine(
    schemaId: String = "zeroinput_pinyin",
    private val options: ChineseInputOptions = ChineseInputOptions(),
    private val onNativeFailure: (Throwable) -> Unit = {},
    private val onClosed: () -> Unit = {},
    private val nineKeyReadings: NineKeyReadings? = null,
) : InputEngine, CandidateTextNormalizer, ReadingSelectionEngine {
    private var sessionId = NativeRimeBridge.nativeCreateSession(schemaId).also {
        check(it != 0L) { "Unable to create a Rime session" }
    }
    private var currentSnapshot = EngineSnapshot.Empty
    private var hasFixedSelection = false
    private val readingHistory = ArrayDeque<Pair<String, String>>()

    override val descriptor = Descriptor

    override val snapshot: EngineSnapshot
        get() = currentSnapshot

    override fun start(context: EditorContext): EngineSnapshot = nativeCall {
        NativeRimeBridge.nativeSetOptions(sessionId, options.script == ChineseScript.SIMPLIFIED, !options.chinesePunctuation)
        NativeRimeBridge.nativeClearComposition(sessionId)
        hasFixedSelection = false
        readingHistory.clear()
        readUpdate(false)
        currentSnapshot
    }

    override fun handle(key: EngineKey): EngineUpdate = nativeCall {
        if (key == EngineKey.Backspace) {
            val previous = readingHistory.lastOrNull()
            if (previous != null && currentSnapshot.rawInput == previous.second) {
                val consumed = NativeRimeBridge.nativeSetInput(sessionId, previous.first)
                if (consumed) readingHistory.removeLast()
                return readUpdate(consumed)
            }
        }
        val (keyCode, modifiers) = when (key) {
            is EngineKey.Character -> key.text.singleOrNull()?.lowercaseChar()?.code?.let { it to 0 }
                ?: return EngineUpdate(currentSnapshot, consumed = false)
            EngineKey.Backspace -> KEY_BACKSPACE to 0
            EngineKey.Space -> KEY_SPACE to 0
            EngineKey.Enter -> KEY_RETURN to 0
        }
        val consumed = NativeRimeBridge.nativeProcessKey(sessionId, keyCode, modifiers)
        readUpdate(consumed)
    }

    override fun selectCandidate(index: Int): EngineUpdate = nativeCall {
        val consumed = NativeRimeBridge.nativeSelectCandidate(sessionId, index)
        if (consumed) {
            hasFixedSelection = true
            readingHistory.clear()
        }
        readUpdate(consumed)
    }

    override fun selectReading(index: Int): EngineUpdate = nativeCall {
        val reading = currentSnapshot.readings.getOrNull(index) ?: return EngineUpdate(currentSnapshot, consumed = false)
        val before = currentSnapshot.rawInput
        val input = nineKeyReadings?.replace(before, reading)
            ?: return EngineUpdate(currentSnapshot, consumed = false)
        val consumed = NativeRimeBridge.nativeSetInput(sessionId, input)
        if (consumed) {
            if (readingHistory.size == 64) readingHistory.removeFirst()
            readingHistory.addLast(before to input)
        }
        readUpdate(consumed)
    }

    override fun changePage(direction: PageDirection): EngineUpdate = nativeCall {
        val consumed = NativeRimeBridge.nativeChangePage(
            sessionId,
            backwards = direction == PageDirection.PREVIOUS,
        )
        readUpdate(consumed)
    }

    override fun reset(): EngineSnapshot = nativeCall {
        NativeRimeBridge.nativeClearComposition(sessionId)
        hasFixedSelection = false
        readingHistory.clear()
        currentSnapshot = EngineSnapshot.Empty
        currentSnapshot
    }

    override fun close() {
        val id = sessionId
        sessionId = 0L
        currentSnapshot = EngineSnapshot.Empty
        if (id != 0L) {
            readingHistory.clear()
            try {
                NativeRimeBridge.nativeDestroySession(id)
            } finally {
                onClosed()
            }
        }
    }

    override fun normalizeCandidateText(text: String): String = nativeCall {
        NativeRimeBridge.nativeConvertText(text, options.script == ChineseScript.SIMPLIFIED)
    }

    private fun readUpdate(consumed: Boolean): EngineUpdate {
        val update = NativeRimeBridge.nativeReadUpdate(sessionId)
        if (update.rawInput.isEmpty()) {
            hasFixedSelection = false
            readingHistory.clear()
        }
        currentSnapshot = EngineSnapshot(
            rawInput = update.rawInput,
            composition = update.composition,
            candidates = update.candidates.mapIndexed { index, text ->
                Candidate("rime:$index:$text", text, update.comments.getOrElse(index) { "" })
            },
            highlightedIndex = update.highlightedIndex,
            hasPreviousPage = update.candidates.isNotEmpty() && update.pageNumber > 0,
            hasNextPage = update.candidates.isNotEmpty() && !update.lastPage,
            readings = if (hasFixedSelection) emptyList() else nineKeyReadings?.choices(update.rawInput, update.comments.toList()).orEmpty(),
        )
        return EngineUpdate(currentSnapshot, update.committedText, consumed || update.committedText.isNotEmpty())
    }

    private inline fun <T> nativeCall(block: () -> T): T = try {
        block()
    } catch (error: Throwable) {
        onNativeFailure(error)
        throw error
    }

    internal companion object {
        const val KEY_BACKSPACE = 0xff08
        const val KEY_RETURN = 0xff0d
        const val KEY_SPACE = 0x20

        val Descriptor = EngineDescriptor(
            id = "rime.luna-pinyin",
            displayName = "Rime 全拼",
            version = "1.13.1-adapter1",
            languages = setOf(InputLanguage.CHINESE),
            capabilities = setOf(EngineCapability.CHINESE_SCRIPT, EngineCapability.ABBREVIATED_PINYIN,
                EngineCapability.FUZZY_PINYIN, EngineCapability.PUNCTUATION_MODE, EngineCapability.CANDIDATE_PAGE_SIZE,
                EngineCapability.NINE_KEY_PINYIN),
        )
    }
}
