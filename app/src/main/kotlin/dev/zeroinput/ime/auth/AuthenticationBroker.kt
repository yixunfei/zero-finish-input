package dev.zeroinput.ime.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import dev.zeroinput.ime.R
import dev.zeroinput.security.AuthenticationGrant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object AuthenticationBroker {
    private val callbacks = ConcurrentHashMap<String, PendingRequest>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun request(context: Context, callback: (AuthenticationGrant?) -> Unit) {
        requestCancellable(context, callback = callback)
    }

    /**
     * Starts authentication and returns a handle that can invalidate the
     * request before the biometric activity finishes.  The legacy [request]
     * entry point intentionally remains fire-and-forget for settings screens.
     */
    fun requestCancellable(
        context: Context,
        promptTitle: Int = R.string.unlock_secure_clipboard,
        callback: (AuthenticationGrant?) -> Unit,
    ): RequestHandle {
        val requestId = UUID.randomUUID().toString()
        val timeout = Runnable { complete(requestId, null) }
        callbacks[requestId] = PendingRequest(callback, timeout)
        mainHandler.postDelayed(timeout, REQUEST_TIMEOUT_MILLIS)
        val intent = Intent(context, SecureClipboardUnlockActivity::class.java)
            .putExtra(SecureClipboardUnlockActivity.EXTRA_REQUEST_ID, requestId)
            .putExtra(SecureClipboardUnlockActivity.EXTRA_PROMPT_TITLE, promptTitle)
            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { complete(requestId, null) }
        return RequestHandle(requestId)
    }

    internal fun complete(requestId: String, grant: AuthenticationGrant?) {
        val pending = callbacks.remove(requestId) ?: return
        mainHandler.removeCallbacks(pending.timeout)
        mainHandler.post { pending.callback(grant) }
    }

    private data class PendingRequest(
        val callback: (AuthenticationGrant?) -> Unit,
        val timeout: Runnable,
    )

    class RequestHandle internal constructor(
        private val requestId: String,
    ) : AutoCloseable {
        private val cancelled = AtomicBoolean(false)

        override fun close() {
            if (cancelled.compareAndSet(false, true)) {
                complete(requestId, null)
            }
        }
    }

    private const val REQUEST_TIMEOUT_MILLIS = 60_000L
}
