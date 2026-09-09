package dev.zeroinput.ime.clipboard

import android.os.Handler
import android.os.Looper
import java.util.UUID

internal data class ClipboardImportDraft(val request: ClipboardImportRequest, val generation: Long)

/** Main-thread, one-use handoff to a nonexported page. No text crosses an Intent. */
internal class ClipboardSelectionTransfer : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private var token: String? = null
    private var draft: ClipboardImportDraft? = null
    private val expire = Runnable { close() }

    fun offer(value: ClipboardImportDraft): String {
        close()
        draft = value
        return UUID.randomUUID().toString().also {
            token = it
            main.postDelayed(expire, 5_000L)
        }
    }

    fun take(id: String?): ClipboardImportDraft? {
        if (id == null || id != token) return null
        val value = draft
        draft = null
        close()
        return value
    }

    override fun close() {
        main.removeCallbacks(expire)
        token = null
        draft?.request?.close()
        draft = null
    }
}
