package dev.zeroinput.ime

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.zeroinput.security.EncryptedStore
import dev.zeroinput.userdata.EmojiHistoryRepository
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpressionHistoryTest {
    @Test fun longKaomojiRoundTripsWithoutTruncation() {
        val store = MemoryStore()
        val value = "(^_^)".repeat(16)
        EmojiHistoryRepository(store).record(value)
        assertEquals(listOf(value), EmojiHistoryRepository(store).recent())
    }

    @Test fun failedWritesDoNotPublishUnsavedHistory() {
        val store = MemoryStore()
        val repo = EmojiHistoryRepository(store)
        repo.record("(^_^)")
        store.failWrite = true
        assertThrows(Exception::class.java) { repo.record("(^o^)") }
        assertEquals(listOf("(^_^)"), repo.recent())
    }

    @Test fun unknownVersionsAreNotOverwrittenAsEmptyHistory() {
        val store = MemoryStore()
        val original = "{\"format\":99,\"entries\":[]}".toByteArray()
        store.bytes = original
        val repo = EmojiHistoryRepository(store)
        assertThrows(Exception::class.java) { repo.record("(^_^)") }
        assertArrayEquals(original, store.bytes)
    }

    private class MemoryStore : EncryptedStore {
        var bytes: ByteArray? = null
        var failWrite = false
        override fun read() = bytes?.copyOf()
        override fun write(plaintext: ByteArray) {
            if (failWrite) throw IOException("Fixture failure")
            bytes = plaintext.copyOf()
        }
        override fun delete(deleteKey: Boolean) { bytes = null }
    }
}
