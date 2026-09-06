package dev.zeroinput.ime

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.zeroinput.security.AesGcmKeyStore
import dev.zeroinput.security.EncryptedFileStore
import dev.zeroinput.userdata.UserLexiconRepository
import dev.zeroinput.engine.api.InputLanguage
import java.io.File
import java.security.GeneralSecurityException
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedUserStoreTest {
    @Test
    fun encryptedPhrasesReloadAndClearingDeletesTheFileAndDedicatedKey() = withStore { store, cipher, file ->
        val repository = UserLexiconRepository(store)
        repository.addPhrase("fixture", "public-fixture", InputLanguage.ENGLISH)
        val envelope = file.readBytes()
        cipher.decrypt(envelope, aad()).fill(0)
        assertFalse(String(envelope, Charsets.UTF_8).contains("public-fixture"))
        assertEquals(1, UserLexiconRepository(store).list().size)
        repository.clear()
        assertFalse(file.exists())
        assertNull(store.read())
        assertThrows(GeneralSecurityException::class.java) { cipher.decrypt(envelope, aad()) }
    }

    @Test
    fun tamperingTruncationAndUnsupportedVersionsFailClosedWithoutOverwritingData() = withStore { store, _, file ->
        UserLexiconRepository(store).addPhrase("fixture", "public-fixture", InputLanguage.ENGLISH)
        val original = file.readBytes()
        val malformed = listOf(
            original.copyOf(10),
            original.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() },
            original.copyOf().apply { this[3] = 2 },
        )
        for (bytes in malformed) {
            file.writeBytes(bytes)
            val repository = UserLexiconRepository(store)
            assertThrows(Exception::class.java) { repository.list() }
            assertThrows(Exception::class.java) { repository.addPhrase("extra", "extra", InputLanguage.ENGLISH) }
            assertTrue(file.readBytes().contentEquals(bytes))
        }
        UserLexiconRepository(store).clear()
        assertNull(store.read())
    }

    @Test
    fun lostKeysRejectExistingCiphertextAndFailedReadsDoNotCacheAnEmptyDictionary() = withStore { store, cipher, file ->
        UserLexiconRepository(store).addPhrase("fixture", "public-fixture", InputLanguage.ENGLISH)
        val original = file.readBytes()
        cipher.deleteKey()
        val repository = UserLexiconRepository(store)
        assertThrows(GeneralSecurityException::class.java) { repository.list() }
        assertThrows(GeneralSecurityException::class.java) {
            repository.addPhrase("extra", "extra", InputLanguage.ENGLISH)
        }
        assertTrue(file.readBytes().contentEquals(original))
        repository.clear()
        repository.addPhrase("new", "new", InputLanguage.ENGLISH)
        assertEquals(1, UserLexiconRepository(store).list().size)
    }

    @Test
    fun storesConsumeWriteBuffersAndRejectAnIncorrectFileBinding() = withStore { store, cipher, file ->
        val plaintext = "public-fixture".toByteArray()
        store.write(plaintext)
        assertTrue(plaintext.all { it == 0.toByte() })
        assertThrows(GeneralSecurityException::class.java) {
            cipher.decrypt(file.readBytes(), "different-file".toByteArray())
        }
        checkNotNull(store.read()).fill(0)
    }

    private fun withStore(action: (EncryptedFileStore, AesGcmKeyStore, File) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.noBackupFilesDir, "test-lexicon-${UUID.randomUUID()}")
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
        }
        val alias = "dev.zeroinput.test.lexicon.${UUID.randomUUID()}"
        val store = EncryptedFileStore(context, "fixture.bin", alias)
        val cipher = AesGcmKeyStore(alias)
        try {
            action(store, cipher, File(directory, "encrypted/fixture.bin"))
        } finally {
            cipher.deleteKey()
            check(directory.canonicalFile.parentFile == target.noBackupFilesDir.canonicalFile)
            directory.deleteRecursively()
        }
    }

    private fun aad() = "zeroinput:fixture.bin:v1".toByteArray()
}
