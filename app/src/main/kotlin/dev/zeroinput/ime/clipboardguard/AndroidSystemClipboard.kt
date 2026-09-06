package dev.zeroinput.ime.clipboardguard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/** The only production system-clipboard adapter. It never requests the payload. */
internal class AndroidSystemClipboard(context: Context) : SystemClipboardPort {
    private val manager = context.applicationContext.getSystemService(ClipboardManager::class.java)
    private var ownEmptyTimestamp: Long? = null

    override fun timestamp(): Long? = manager.primaryClipDescription?.timestamp?.takeUnless { it == ownEmptyTimestamp }

    override fun clear() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.clearPrimaryClip()
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
            ownEmptyTimestamp = manager.primaryClipDescription?.timestamp
        }
    }

    override fun listen(onChanged: () -> Unit): AutoCloseable {
        val listener = ClipboardManager.OnPrimaryClipChangedListener(onChanged)
        manager.addPrimaryClipChangedListener(listener)
        return AutoCloseable { manager.removePrimaryClipChangedListener(listener) }
    }
}
