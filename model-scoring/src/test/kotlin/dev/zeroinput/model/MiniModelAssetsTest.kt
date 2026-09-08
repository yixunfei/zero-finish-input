package dev.zeroinput.model

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class MiniModelAssetsTest {
    @Test fun `hash length truncation tamper and overflow fail closed`() {
        val bytes = byteArrayOf(1, 2, 3)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertTrue(MiniModelAssets.verified(bytes.inputStream(), 3, hash))
        assertFalse(MiniModelAssets.verified(bytes.inputStream(), 2, hash))
        assertFalse(MiniModelAssets.verified(bytes.inputStream(), 4, hash))
        assertFalse(MiniModelAssets.verified(byteArrayOf(1, 2, 4).inputStream(), 3, hash))
    }

    @Test fun `tokenizer preserves Unicode line separators and masks only candidate positions`() {
        val tokens = MutableList(21_128) { "[unused$it]" }
        tokens[101] = "[CLS]"; tokens[102] = "[SEP]"; tokens[103] = "[MASK]"
        tokens[110] = "\u2028"; tokens[111] = "学"; tokens[112] = "知"; tokens[113] = "识"
        val tokenizer = MaskedWordTokenizer(tokens.joinToString("\n").reader())
        val batch = checkNotNull(tokenizer.encode(charArrayOf('学'), arrayOf(charArrayOf('知', '识'), charArrayOf('学', '识'))))
        assertArrayEquals(longArrayOf(101, 111, 103, 113, 102, 101, 111, 112, 103, 102), batch.ids.take(10).toLongArray())
        assertArrayEquals(longArrayOf(2, 3, 2, 3), batch.positions)
        assertArrayEquals(longArrayOf(112, 113, 111, 113), batch.targets)
        batch.close()
        assertTrue(batch.ids.all { it == 0L } && batch.targets.all { it == 0L })
        assertNull(tokenizer.encode(charArrayOf('X'), arrayOf(charArrayOf('知', '识'), charArrayOf('学', '识'))))
        assertNull(tokenizer.encode(CharArray(17) { '学' }, arrayOf(charArrayOf('知', '识'), charArrayOf('学', '识'))))
        assertThrows(IllegalArgumentException::class.java) { MaskedWordTokenizer("[CLS]\n".reader()) }
    }
}
