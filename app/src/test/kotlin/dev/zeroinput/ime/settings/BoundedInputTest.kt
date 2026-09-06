package dev.zeroinput.ime.settings

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedInputTest {
    @Test
    fun readsInputAtLimit() {
        val input = byteArrayOf(1, 2, 3, 4)

        val result = ByteArrayInputStream(input).readBoundedBytes(input.size)

        assertArrayEquals(input, result)
    }

    @Test
    fun rejectsInputAboveLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)).readBoundedBytes(3)
        }
    }
}
