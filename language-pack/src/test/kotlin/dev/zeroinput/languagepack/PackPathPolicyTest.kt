package dev.zeroinput.languagepack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PackPathPolicyTest {
    @Test
    fun `normal data path is accepted`() {
        assertEquals("dicts/de.dict.yaml", PackPathPolicy.validate("dicts/de.dict.yaml"))
    }

    @Test
    fun `parent traversal is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PackPathPolicy.validate("../outside.yaml")
        }
    }

    @Test
    fun `native library is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PackPathPolicy.validate("lib/arm64-v8a/payload.so")
        }
    }
}

