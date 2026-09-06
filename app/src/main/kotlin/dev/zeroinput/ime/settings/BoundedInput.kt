package dev.zeroinput.ime.settings

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun InputStream.readBoundedBytes(maxBytes: Int): ByteArray {
    require(maxBytes >= 0) { "Maximum byte count must not be negative" }
    val output = WipingByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    try {
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(total <= maxBytes - count) { "Input exceeds the allowed size" }
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    } finally {
        buffer.fill(0)
        output.clear()
    }
}

private class WipingByteArrayOutputStream(capacity: Int) : ByteArrayOutputStream(capacity) {
    fun clear() {
        buf.fill(0)
        reset()
    }
}
