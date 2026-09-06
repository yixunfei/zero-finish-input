package dev.zeroinput.ime.clipboardguard

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import dev.zeroinput.ime.ZeroInputService
import dev.zeroinput.ime.concurrency.BoundedExecutors
import dev.zeroinput.security.AuthenticationGrant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Application-owned runtime; only the worker touches clipboard APIs and session policy. */
internal class ClipboardGuardRuntime(
    private val context: Context,
    private val preferences: ClipboardGuardPreferences,
    private val createClipboard: () -> SystemClipboardPort = { AndroidSystemClipboard(context) },
    private val isDefaultIme: () -> Boolean = { isSelectedIme(context) },
) : AutoCloseable {
    private val worker = BoundedExecutors.singleThread("zeroinput-clipboard-guard", 8)
    private val main = Handler(Looper.getMainLooper())
    private val revision = AtomicLong()
    private val imeAttached = AtomicBoolean()
    private val refreshQueued = AtomicBoolean()
    private val eventQueued = AtomicBoolean()
    private val clearQueued = AtomicBoolean()
    private val closed = AtomicBoolean()
    private val listeners = CopyOnWriteArrayList<(ClipboardGuardState) -> Unit>()
    private val notifications = ClipboardGuardNotifications(context)
    @Volatile private var options = ClipboardGuardOptions()
    @Volatile var state = ClipboardGuardState(options)
        private set
    private var lease = -1L
    private var clipboard: SystemClipboardPort? = null
    private var session: ClipboardGuardSession? = null
    private var registration: AutoCloseable? = null
    private var observingDefaultIme = false
    private val defaultImeObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) { reconfigure() }
    }
    private val settingsObserver = preferences.observe(::reconfigure)

    init { reconfigure() }

    fun attachIme() {
        if (closed.get()) return
        imeAttached.set(true)
        reconfigure()
    }

    fun detachIme() {
        imeAttached.set(false)
        reconfigure()
    }

    fun observe(listener: (ClipboardGuardState) -> Unit): AutoCloseable {
        listeners += listener
        listener(state)
        return AutoCloseable { listeners -= listener }
    }

    fun clear(id: String, grant: AuthenticationGrant?, isActive: () -> Boolean, result: (ClipboardClearResult) -> Unit) {
        if (closed.get() || !clearQueued.compareAndSet(false, true)) {
            main.post { result(ClipboardClearResult.EXPIRED) }
            return
        }
        val expected = revision.get()
        enqueue {
            try {
                val outcome = if (expected != revision.get()) ClipboardClearResult.EXPIRED else {
                    session?.clear(id, authenticate = { grant?.consume() == true },
                        isActive = { expected == revision.get() && isActive() }) ?: ClipboardClearResult.EXPIRED
                }
                main.post { result(outcome) }
                if (expected == revision.get()) publish(session?.state ?: state)
            } finally {
                clearQueued.set(false)
            }
        }.also { accepted ->
            if (!accepted) {
                clearQueued.set(false)
                main.post { result(ClipboardClearResult.FAILED) }
            }
        }
    }

    private fun reconfigure() {
        if (closed.get()) return
        revision.incrementAndGet()
        if (!refreshQueued.compareAndSet(false, true)) return
        if (!enqueue {
            refreshQueued.set(false)
            refresh()
        }) refreshQueued.set(false)
    }

    private fun refresh() {
        detachPlatform()
        session = null
        lease = revision.get()
        notifications.cancel()
        // SharedPreferences can wait for its initial disk load; keep it off the IME thread.
        val current = preferences.options
        if (lease != revision.get()) return
        options = current
        if (closed.get() || !imeAttached.get() || !current.listening) {
            publish(ClipboardGuardState(current, if (current.listening) ClipboardGuardStatus.UNAVAILABLE else ClipboardGuardStatus.OFF))
            return
        }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, defaultImeObserver,
        )
        observingDefaultIme = true
        if (!isDefaultIme()) {
            publish(ClipboardGuardState(current, ClipboardGuardStatus.UNAVAILABLE))
            return
        }
        val port = clipboard ?: createClipboard().also { clipboard = it }
        val policy = ClipboardGuardSession(port, ::mayAccess) { UUID.randomUUID().toString() }
        session = policy
        policy.start(current)
        if (policy.state.status == ClipboardGuardStatus.WAITING && mayAccess()) {
            val registeredRevision = lease
            registration = port.listen { queueEvent(registeredRevision) }
            // Recheck after registration to cover a change between baseline and subscription.
            policy.changed()
        }
        publish(policy.state)
    }

    private fun queueEvent(registeredRevision: Long) {
        if (registeredRevision != revision.get() || !eventQueued.compareAndSet(false, true)) return
        if (!enqueue {
            eventQueued.set(false)
            // A replacement registration may share this coalesced wake-up.
            if (lease == revision.get()) {
                if (!mayAccess()) reconfigure() else {
                    session?.changed()
                    session?.let { publish(it.state) }
                }
            }
        }) eventQueued.set(false)
    }

    private fun mayAccess(): Boolean = imeAttached.get() && options.listening && lease == revision.get() && isDefaultIme()

    private fun publish(next: ClipboardGuardState) {
        val expected = lease
        if (closed.get() || next.options != options || expected != revision.get()) return
        if (next.ticket != null && !mayAccess()) return
        val previous = state
        state = next
        if (next != previous) {
            if (next.status in setOf(ClipboardGuardStatus.CHANGED, ClipboardGuardStatus.CLEARED, ClipboardGuardStatus.FAILED)) {
                notifications.show(next)
            } else notifications.cancel()
        }
        main.post { if (!closed.get() && expected == revision.get() && state == next) listeners.forEach { it(next) } }
    }

    private fun detachPlatform() {
        runCatching { registration?.close() }
        registration = null
        if (observingDefaultIme) {
            context.contentResolver.unregisterContentObserver(defaultImeObserver)
            observingDefaultIme = false
        }
    }

    private fun enqueue(action: () -> Unit): Boolean = try {
        worker.execute {
            try {
                action()
            } catch (_: RuntimeException) {
                lease = revision.incrementAndGet()
                detachPlatform()
                session = null
                publish(ClipboardGuardState(options, ClipboardGuardStatus.FAILED))
            }
        }
        true
    } catch (_: RejectedExecutionException) {
        revision.incrementAndGet()
        false
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        imeAttached.set(false)
        revision.incrementAndGet()
        settingsObserver.close()
        listeners.clear()
        enqueue { detachPlatform(); notifications.cancel() }
        worker.shutdown()
    }

    private companion object {
        fun isSelectedIme(context: Context): Boolean = try {
            val selected = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            selected != null && ComponentName.unflattenFromString(selected) == ComponentName(context, ZeroInputService::class.java)
        } catch (_: RuntimeException) {
            false
        }
    }
}
