package dev.zeroinput.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AesGcmKeyStore(
    private val alias: String,
) {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv

        return ByteBuffer.allocate(HEADER_SIZE + iv.size + ciphertext.size)
            .putInt(FORMAT_VERSION)
            .putInt(iv.size)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray {
        require(envelope.size >= HEADER_SIZE + MIN_IV_SIZE + TAG_SIZE_BYTES) {
            "Encrypted payload is truncated"
        }
        val buffer = ByteBuffer.wrap(envelope)
        require(buffer.int == FORMAT_VERSION) { "Unsupported encrypted payload version" }
        val ivSize = buffer.int
        require(ivSize in MIN_IV_SIZE..MAX_IV_SIZE && buffer.remaining() > ivSize) {
            "Invalid encrypted payload IV"
        }
        val iv = ByteArray(ivSize).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }

    fun deleteKey() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = loadKeyStore()
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val specification = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(specification)
        return generator.generateKey()
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply {
        load(null)
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = 1
        const val HEADER_SIZE = Int.SIZE_BYTES * 2
        const val KEY_SIZE_BITS = 256
        const val TAG_SIZE_BITS = 128
        const val TAG_SIZE_BYTES = TAG_SIZE_BITS / Byte.SIZE_BITS
        const val MIN_IV_SIZE = 12
        const val MAX_IV_SIZE = 32
    }
}

