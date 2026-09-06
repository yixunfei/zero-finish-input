package dev.zeroinput.ime.clipboardguard

/** Worker-confined policy. Every destructive action rechecks its external lease. */
internal class ClipboardGuardSession(
    private val clipboard: SystemClipboardPort,
    private val mayAccess: () -> Boolean,
    private val newId: () -> String,
) {
    var state = ClipboardGuardState()
        private set
    private var lastTimestamp: Long? = null

    fun start(options: ClipboardGuardOptions) {
        val normalized = options.normalized()
        state = ClipboardGuardState(normalized)
        lastTimestamp = null
        if (!normalized.listening) return
        if (!mayAccess()) {
            state = state.copy(status = ClipboardGuardStatus.UNAVAILABLE)
            return
        }
        try {
            lastTimestamp = clipboard.timestamp()
            state = state.copy(status = ClipboardGuardStatus.WAITING)
        } catch (_: RuntimeException) {
            state = state.copy(status = ClipboardGuardStatus.FAILED)
        }
    }

    fun changed() {
        if (!state.options.listening || !mayAccess()) {
            stop()
            return
        }
        val timestamp = try {
            clipboard.timestamp()
        } catch (_: RuntimeException) {
            state = state.copy(status = ClipboardGuardStatus.FAILED, ticket = null)
            return
        }
        if (timestamp == lastTimestamp) return
        lastTimestamp = timestamp
        if (timestamp == null) {
            state = state.copy(status = ClipboardGuardStatus.WAITING, ticket = null)
            return
        }
        val ticket = ClipboardGuardTicket(newId(), timestamp)
        state = state.copy(status = ClipboardGuardStatus.CHANGED, ticket = ticket)
        if (state.options.clearMode == ClipboardClearMode.AUTOMATIC) clear(ticket.id)
    }

    fun clear(
        id: String,
        authenticate: () -> Boolean = { false },
        isActive: () -> Boolean = { true },
    ): ClipboardClearResult {
        val ticket = state.ticket
        if (ticket == null || ticket.id != id || !state.options.listening ||
            state.options.clearMode == ClipboardClearMode.NONE || !mayAccess() || !isActive()
        ) return ClipboardClearResult.EXPIRED
        if (state.options.authenticate && !authenticate()) return ClipboardClearResult.AUTHENTICATION_REQUIRED
        return try {
            if (clipboard.timestamp() != ticket.timestamp || !mayAccess() || !isActive()) {
                state = state.copy(ticket = null, status = ClipboardGuardStatus.WAITING)
                ClipboardClearResult.EXPIRED
            } else {
                // Android has no atomic compare-and-clear API; a new clip can still win this gap.
                clipboard.clear()
                state = state.copy(ticket = null, status = ClipboardGuardStatus.CLEARED)
                lastTimestamp = null
                if (clipboard.timestamp() == null) ClipboardClearResult.CLEARED else {
                    state = state.copy(status = ClipboardGuardStatus.FAILED)
                    ClipboardClearResult.FAILED
                }
            }
        } catch (_: RuntimeException) {
            state = state.copy(status = ClipboardGuardStatus.FAILED, ticket = null)
            ClipboardClearResult.FAILED
        }
    }

    fun stop() {
        lastTimestamp = null
        state = state.copy(ticket = null, status = if (state.options.listening) {
            ClipboardGuardStatus.UNAVAILABLE
        } else ClipboardGuardStatus.OFF)
    }
}
