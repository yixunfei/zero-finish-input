package dev.zeroinput.userdata

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal object PersonalExpressionFormat {
    private const val MAGIC = 0x5a495850
    private const val VERSION = 1

    fun encode(data: PersonalExpressions): ByteArray {
        validate(data)
        val buffer = ClearingOutput()
        return try {
            DataOutputStream(buffer).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(data.custom.size)
                data.custom.forEach { entry ->
                    output.text(entry.id)
                    output.text(entry.value)
                    output.text(entry.name)
                    output.text(entry.keywords)
                    output.text(entry.group)
                }
                output.writeInt(data.favorites.size)
                data.favorites.forEach { output.text(it) }
            }
            buffer.toByteArray()
        } finally {
            buffer.erase()
        }
    }

    fun decode(bytes: ByteArray): PersonalExpressions {
        if (bytes.size > ExpressionLimits.BYTES) corrupt()
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) corrupt()
                val custom = List(input.count(ExpressionLimits.CUSTOM)) {
                    PersonalExpression(input.text(36), input.text(ExpressionLimits.TEXT),
                        input.text(ExpressionLimits.NAME), input.text(ExpressionLimits.KEYWORDS),
                        input.text(ExpressionLimits.GROUP))
                }
                val favorites = List(input.count(ExpressionLimits.FAVORITES)) { input.text(ExpressionLimits.TEXT) }
                if (favorites.distinct().size != favorites.size || input.read() != -1) corrupt()
                PersonalExpressions(custom, favorites.toSet()).also(::validate)
            }
        } catch (_: IOException) {
            corrupt()
        } catch (_: IllegalArgumentException) {
            corrupt()
        } catch (_: ExpressionException) {
            corrupt()
        }
    }

    private fun validate(data: PersonalExpressions) {
        if (data.custom.size > ExpressionLimits.CUSTOM || data.favorites.size > ExpressionLimits.FAVORITES) {
            throw ExpressionException(ExpressionFailure.CAPACITY)
        }
        data.custom.forEach(ExpressionLimits::validate)
        if (data.custom.map { it.id }.distinct().size != data.custom.size ||
            data.custom.map { it.value }.distinct().size != data.custom.size ||
            data.favorites.any { !ExpressionLimits.validText(it) }) {
            throw ExpressionException(ExpressionFailure.INVALID)
        }
    }

    private fun DataInputStream.count(max: Int): Int = readInt().also { if (it !in 0..max) corrupt() }

    private fun DataInputStream.text(max: Int): String {
        val length = count(max * 4)
        if (length > available()) corrupt()
        val bytes = ByteArray(length)
        return try {
            readFully(bytes)
            val characters = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
            try {
                characters.toString().also { if (it.length > max) corrupt() }
            } finally { if (characters.hasArray()) characters.array().fill('\u0000') }
        } finally {
            bytes.fill(0)
        }
    }

    private fun DataOutputStream.text(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        try {
            writeInt(bytes.size)
            write(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun corrupt(): Nothing = throw ExpressionException(ExpressionFailure.CORRUPT)

    private class ClearingOutput : ByteArrayOutputStream() {
        fun erase() { buf.fill(0); reset() }
    }
}
