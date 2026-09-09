package dev.zeroinput.ime.clipboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.zeroinput.security.AuthenticationGrant
import dev.zeroinput.security.EncryptedStore
import dev.zeroinput.userdata.SecureClipboardVault
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardImportVaultTest {
    @Test
    fun anAuthorizedAdditionConsumesItsGrantAndWipesSerializedBuffers() {
        val body = MemoryStore()
        val index = MemoryStore()
        val vault = SecureClipboardVault(body, index)
        val grant = grant()
        val item = vault.add("fixture", "public fixture", grant)
        assertEquals("public fixture", vault.read(item.id, grant()))
        assertThrows(IllegalArgumentException::class.java) { vault.add("", "second fixture", grant) }
        assertEquals(1, vault.count())
        assertTrue(checkNotNull(body.lastWrite).all { it == 0.toByte() })
        assertTrue(checkNotNull(body.lastRead).all { it == 0.toByte() })
        assertTrue(checkNotNull(index.lastWrite).all { it == 0.toByte() })
    }

    @Test
    fun cancelledOrClearedRequestsCannotReadOrRecreateVaultData() {
        val body = MemoryStore()
        val vault = SecureClipboardVault(body, MemoryStore())
        val generation = vault.captureGeneration()
        assertThrows(CancellationException::class.java) { vault.add("", "fixture", grant(), generation) { false } }
        assertEquals(0, body.reads)
        vault.clear(grant())
        assertTrue(body.deletedKey)
        assertThrows(CancellationException::class.java) { vault.add("", "fixture", grant(), generation) }
        assertEquals(0, body.reads)
        assertNull(body.bytes)
    }

    @Test
    fun cancellationDuringReadIsCheckedAgainBeforePersisting() {
        val body = MemoryStore()
        val vault = SecureClipboardVault(body, MemoryStore())
        var active = true
        body.onRead = { active = false }
        assertThrows(CancellationException::class.java) {
            vault.add("", "fixture", grant(), vault.captureGeneration()) { active }
        }
        assertEquals(0, body.writes)
    }

    @Test
    fun clearWhileAnAdditionLoadsInvalidatesItsCommitAndQueuedAdditions() {
        val body = MemoryStore()
        val vault = SecureClipboardVault(body, MemoryStore())
        val generation = vault.captureGeneration()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        body.onRead = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val addition = pool.submit {
                assertThrows(CancellationException::class.java) { vault.add("", "fixture", grant(), generation) }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val clear = pool.submit { vault.clear(grant()) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (vault.captureGeneration() == generation && System.nanoTime() < deadline) Thread.yield()
            assertTrue(vault.captureGeneration() != generation)
            release.countDown()
            addition.get(5, TimeUnit.SECONDS)
            clear.get(5, TimeUnit.SECONDS)
            assertNull(body.bytes)
            assertEquals(0, body.writes)
            assertThrows(CancellationException::class.java) { vault.add("", "fixture", grant(), generation) }
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun cancelledOrDeletedPasteDoesNotDecryptAndCancellationDuringDecryptionDiscardsTheResult() {
        val body = MemoryStore()
        val vault = SecureClipboardVault(body, MemoryStore())
        val item = vault.add("", "public fixture", grant())
        val generation = vault.captureGeneration()
        val reads = body.reads
        assertThrows(CancellationException::class.java) { vault.read(item.id, grant(), generation) { false } }
        assertEquals(reads, body.reads)
        var active = true
        body.onRead = { active = false }
        assertThrows(CancellationException::class.java) { vault.read(item.id, grant(), generation) { active } }
        assertTrue(checkNotNull(body.lastRead).all { it == 0.toByte() })
        body.onRead = {}
        vault.clear(grant())
        val readsAfterClear = body.reads
        assertThrows(CancellationException::class.java) { vault.read(item.id, grant(), generation) }
        assertEquals(readsAfterClear, body.reads)
    }

    @Test
    fun corruptionReadFailuresAndFailedWritesNeverReplaceExistingData() {
        val malformed = listOf("broken fixture", "{\"format\":2,\"entries\":[]}")
        for (data in malformed) {
            val body = MemoryStore(data.toByteArray())
            assertThrows(Exception::class.java) { SecureClipboardVault(body, MemoryStore()).add("", "fixture", grant()) }
            assertEquals(0, body.writes)
            assertEquals(data, String(checkNotNull(body.bytes)))
            assertTrue(checkNotNull(body.lastRead).all { it == 0.toByte() })
        }
        val body = MemoryStore()
        val vault = SecureClipboardVault(body, MemoryStore())
        vault.add("", "original fixture", grant())
        val original = checkNotNull(body.bytes).copyOf()
        body.failRead = true
        assertThrows(IOException::class.java) { vault.add("", "fixture", grant()) }
        body.failRead = false
        body.failWrite = true
        assertThrows(IOException::class.java) { vault.add("", "fixture", grant()) }
        assertTrue(checkNotNull(body.bytes).contentEquals(original))
        assertTrue(checkNotNull(body.lastWrite).all { it == 0.toByte() })
        assertFalse(body.deletedKey)
    }

    private fun grant() = AuthenticationGrant.afterSuccessfulSystemAuthentication()

    private class MemoryStore(var bytes: ByteArray? = null) : EncryptedStore {
        var reads = 0
        var writes = 0
        var lastRead: ByteArray? = null
        var lastWrite: ByteArray? = null
        var deletedKey = false
        var failRead = false
        var failWrite = false
        var onRead: () -> Unit = {}

        override fun read(): ByteArray? {
            reads++
            onRead()
            if (failRead) throw IOException("Fixture read failure")
            return bytes?.copyOf().also { lastRead = it }
        }

        override fun write(plaintext: ByteArray) {
            lastWrite = plaintext
            if (failWrite) throw IOException("Fixture write failure")
            bytes = plaintext.copyOf()
            writes++
        }

        override fun delete(deleteKey: Boolean) {
            bytes = null
            deletedKey = deleteKey
        }
    }
}
