package dev.zeroinput.ime.clipboard

import android.content.Intent
import dev.zeroinput.userdata.SecureClipboardVault

internal object ClipboardImportIntent {
    fun parse(intent: Intent): ClipboardImportRequest? = try {
        parseChecked(intent)
    } catch (_: RuntimeException) {
        // A caller controls parcelled extras; never expose their exception text.
        null
    }

    private fun parseChecked(intent: Intent): ClipboardImportRequest? {
        if (intent.type != "text/plain" || intent.data != null || intent.selector != null) return null
        if (intent.hasExtra(Intent.EXTRA_STREAM) || intent.hasExtra(Intent.EXTRA_HTML_TEXT)) return null
        val key = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> Intent.EXTRA_PROCESS_TEXT
            Intent.ACTION_SEND -> Intent.EXTRA_TEXT
            else -> return null
        }
        val value = intent.getCharSequenceExtra(key) ?: return null
        if (value.length !in 1..SecureClipboardVault.MAX_VALUE_LENGTH) return null
        if (value.isBlank() || value.any { it == '\u0000' }) return null
        if (!hasValidSurrogates(value)) return null
        val clip = intent.clipData
        if (clip != null) {
            if (clip.itemCount != 1) return null
            val item = clip.getItemAt(0)
            if (item.uri != null || item.intent != null || item.htmlText != null) return null
            val duplicate = item.text ?: return null
            if (duplicate.length != value.length || duplicate.indices.any { duplicate[it] != value[it] }) return null
        }
        // Copy characters only, dropping URL, image and other parcelled spans.
        return ClipboardImportRequest(CharArray(value.length) { value[it] })
    }

    private fun hasValidSurrogates(text: CharSequence): Boolean {
        var index = 0
        while (index < text.length) {
            val character = text[index++]
            if (character.isHighSurrogate()) {
                if (index == text.length || !text[index++].isLowSurrogate()) return false
            } else if (character.isLowSurrogate()) return false
        }
        return true
    }
}
