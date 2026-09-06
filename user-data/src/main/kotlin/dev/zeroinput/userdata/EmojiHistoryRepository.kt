package dev.zeroinput.userdata

import android.content.Context
import dev.zeroinput.security.EncryptedFileStore
import dev.zeroinput.security.EncryptedStore
import dev.zeroinput.security.SecurityAliases
import java.util.concurrent.atomic.AtomicLong

/** Background-only encrypted history, with no process-wide plaintext cache. */
class EmojiHistoryRepository(private val store: EncryptedStore) {
    constructor(context: Context) : this(EncryptedFileStore(context, "emoji-history.bin", SecurityAliases.EMOJI_HISTORY))

    private val lock = Any()
    private val clearRevision = AtomicLong()
    private var deletionPending = false

    fun currentRevision(): Long = clearRevision.get()

    fun record(emoji: String) { recordIfRevision(emoji, currentRevision()) }

    fun recordIfRevision(emoji: String, expectedRevision: Long, isCurrent: () -> Boolean = { true }): Boolean =
        synchronized(lock) {
            if (expectedRevision != currentRevision() || !isCurrent()) return@synchronized false
            if (!ExpressionLimits.validText(emoji)) throw ExpressionException(ExpressionFailure.INVALID)
            val current = load().associateByTo(linkedMapOf(), EmojiUsage::value)
            val old = current[emoji]
            current[emoji] = EmojiUsage(emoji, ((old?.count ?: 0) + 1).coerceAtMost(EmojiHistoryFormat.MAX_COUNT),
                maxOf(System.currentTimeMillis(), current.values.maxOfOrNull { it.lastUsed }?.plus(1) ?: 0L))
            val updated = current.values.sortedByDescending { it.lastUsed }.take(EmojiHistoryFormat.MAX_ENTRIES)
            val bytes = EmojiHistoryFormat.encode(updated)
            try {
                if (expectedRevision != currentRevision() || !isCurrent()) return@synchronized false
                store.write(bytes)
            } finally { bytes.fill(0) }
            true
        }

    fun recent(limit: Int = 32, isCurrent: () -> Boolean = { true }): List<String> = synchronized(lock) {
        require(limit in 0..EmojiHistoryFormat.MAX_ENTRIES)
        if (!isCurrent()) return@synchronized emptyList()
        val revision = currentRevision()
        val loaded = load()
        if (!isCurrent() || revision != currentRevision()) return@synchronized emptyList()
        loaded.sortedWith(compareByDescending<EmojiUsage> { it.lastUsed }.thenByDescending { it.count })
            .take(limit).map { it.value }
    }

    fun clear() {
        clearRevision.incrementAndGet()
        synchronized(lock) {
            deletionPending = true
            store.delete(deleteKey = true)
            deletionPending = false
        }
    }

    private fun load(): List<EmojiUsage> {
        if (deletionPending) throw ExpressionException(ExpressionFailure.STORAGE)
        val bytes = store.read() ?: return emptyList()
        return try { EmojiHistoryFormat.decode(bytes) } finally { bytes.fill(0) }
    }
}
