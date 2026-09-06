package dev.zeroinput.userdata

import android.content.Context
import dev.zeroinput.security.EncryptedFileStore
import dev.zeroinput.security.SecurityAliases
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class EmojiHistoryRepository(context: Context) {
    private val store = EncryptedFileStore(
        context = context,
        fileName = "emoji-history.bin",
        keyAlias = SecurityAliases.EMOJI_HISTORY,
    )
    private val lock = Any()
    private var entries: MutableMap<String, EmojiUsage>? = null
    /**
     * Increments whenever the user clears history.  The IME records history
     * asynchronously, so callers can use this revision to discard a write
     * that was queued before a clear operation completed.
     */
    @Volatile
    private var clearRevision = 0L

    fun record(emoji: String) = synchronized(lock) {
        recordLocked(emoji)
    }

    /**
     * Records only when no clear has happened since [expectedRevision] was
     * captured.  The check and write share the repository lock, making a
     * clear-before-write race deterministic.
     */
    fun recordIfRevision(emoji: String, expectedRevision: Long): Boolean = synchronized(lock) {
        if (clearRevision != expectedRevision) return@synchronized false
        recordLocked(emoji)
        true
    }

    fun currentRevision(): Long = clearRevision

    private fun recordLocked(emoji: String) {
        require(emoji.isNotBlank() && emoji.length <= MAX_EMOJI_LENGTH)
        val current = loadEntries()
        val old = current[emoji]
        current[emoji] = EmojiUsage(
            value = emoji,
            count = ((old?.count ?: 0) + 1).coerceAtMost(MAX_COUNT),
            lastUsedEpochMillis = System.currentTimeMillis(),
        )
        trim(current)
        persist(current.values)
    }

    fun recent(limit: Int = DEFAULT_LIMIT): List<String> = synchronized(lock) {
        require(limit in 0..MAX_ENTRIES)
        loadEntries().values
            .sortedWith(compareByDescending<EmojiUsage> { it.lastUsedEpochMillis }.thenByDescending { it.count })
            .take(limit)
            .map(EmojiUsage::value)
    }

    fun clear() = synchronized(lock) {
        clearRevision += 1
        entries = mutableMapOf()
        // Emoji history has a dedicated alias; remove it for cryptographic
        // erasure instead of retaining a key for data the user deleted.
        store.delete(deleteKey = true)
    }

    private fun loadEntries(): MutableMap<String, EmojiUsage> {
        entries?.let { return it }
        val bytes = store.read()
        val loaded = if (bytes == null) {
            emptyList()
        } else {
            try {
                val root = JSONObject(String(bytes, StandardCharsets.UTF_8))
                if (root.optInt("format") != FORMAT_VERSION) emptyList() else parse(root.getJSONArray("entries"))
            } finally {
                bytes.fill(0)
            }
        }
        return loaded.associateByTo(mutableMapOf(), EmojiUsage::value).also { entries = it }
    }

    private fun parse(array: JSONArray): List<EmojiUsage> = List(array.length().coerceAtMost(MAX_ENTRIES)) { index ->
        val item = array.getJSONObject(index)
        EmojiUsage(
            value = item.getString("value").take(MAX_EMOJI_LENGTH),
            count = item.optInt("count", 1).coerceIn(1, MAX_COUNT),
            lastUsedEpochMillis = item.optLong("lastUsed", 0L).coerceAtLeast(0L),
        )
    }

    private fun trim(values: MutableMap<String, EmojiUsage>) {
        if (values.size <= MAX_ENTRIES) return
        values.values.sortedByDescending(EmojiUsage::lastUsedEpochMillis)
            .drop(MAX_ENTRIES)
            .forEach { values.remove(it.value) }
    }

    private fun persist(values: Collection<EmojiUsage>) {
        val root = JSONObject().apply {
            put("format", FORMAT_VERSION)
            put("entries", JSONArray().apply {
                values.forEach { usage ->
                    put(JSONObject().apply {
                        put("value", usage.value)
                        put("count", usage.count)
                        put("lastUsed", usage.lastUsedEpochMillis)
                    })
                }
            })
        }
        store.write(root.toString().toByteArray(StandardCharsets.UTF_8))
    }

    private data class EmojiUsage(
        val value: String,
        val count: Int,
        val lastUsedEpochMillis: Long,
    )

    private companion object {
        const val FORMAT_VERSION = 1
        const val DEFAULT_LIMIT = 32
        const val MAX_ENTRIES = 128
        const val MAX_EMOJI_LENGTH = 32
        const val MAX_COUNT = 1_000_000
    }
}
