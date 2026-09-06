package dev.zeroinput.ime

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.zeroinput.security.AesGcmKeyStore
import dev.zeroinput.security.EncryptedFileStore
import dev.zeroinput.userdata.PersonalExpressionRepository
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpressionEncryptedStoreTest {
    @Test fun customExpressionsAndFavoritesReloadFromCiphertextAndClearDeletesTheirKey() = withStore { store, cipher, file ->
        val repository = PersonalExpressionRepository(store)
        val entry = repository.save(null, "(public-fixture)", "Public name", "public keyword", "happy")
        repository.favorite(entry.value, true)
        val encrypted = file.readBytes()
        assertFalse(String(encrypted, Charsets.UTF_8).contains("public-fixture"))
        assertEquals(entry, PersonalExpressionRepository(store).read().custom.single())
        assertEquals(setOf(entry.value), PersonalExpressionRepository(store).read().favorites)
        repository.clear()
        assertFalse(file.exists())
        assertThrows(Exception::class.java) { cipher.decrypt(encrypted, "zeroinput:fixture.bin:v1".toByteArray()) }
    }

    @Test fun tamperingTruncationAndKeyLossCannotOverwriteExistingPersonalExpressions() = withStore { store, cipher, file ->
        PersonalExpressionRepository(store).favorite("(public-fixture)", true)
        val original = file.readBytes()
        for (bytes in listOf(original.copyOf(12), original.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })) {
            file.writeBytes(bytes)
            val repository = PersonalExpressionRepository(store)
            assertThrows(Exception::class.java) { repository.read() }
            assertThrows(Exception::class.java) { repository.favorite("extra", true) }
            assertArrayEquals(bytes, file.readBytes())
        }
        file.writeBytes(original)
        cipher.deleteKey()
        val repository = PersonalExpressionRepository(store)
        assertThrows(Exception::class.java) { repository.read() }
        assertThrows(Exception::class.java) { repository.favorite("extra", true) }
        assertArrayEquals(original, file.readBytes())
        repository.clear()
        repository.favorite("new-public-fixture", true)
        assertEquals(setOf("new-public-fixture"), repository.read().favorites)
    }

    private fun withStore(action: (EncryptedFileStore, AesGcmKeyStore, File) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.noBackupFilesDir, "test-expression-${UUID.randomUUID()}")
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir() = directory }
        val alias = "dev.zeroinput.test.expression.${UUID.randomUUID()}"
        val store = EncryptedFileStore(context, "fixture.bin", alias)
        val cipher = AesGcmKeyStore(alias)
        try { action(store, cipher, File(directory, "encrypted/fixture.bin")) } finally {
            cipher.deleteKey()
            check(directory.canonicalFile.parentFile == target.noBackupFilesDir.canonicalFile)
            directory.deleteRecursively()
        }
    }
}
