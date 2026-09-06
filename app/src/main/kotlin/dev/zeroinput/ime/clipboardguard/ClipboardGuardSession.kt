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
        } catch (failure: RuntimeException) {
            failed(failure)
        }
    }

    fun changed() {
        if (!state.options.listening || !mayAccess()) {
            stop()
            return
        }
        val timestamp = try {
            clipboard.timestamp()
        } catch (failure: RuntimeException) {
            failed(failure)
            return
        }
        if (timestamp == lastTimestamp) return
        lastTimestamp = timestamp
        if (timestamp == null) {
            state = state.copy(status = ClipboardGuardStatus.WAITING, ticket = null)
            return
        }
        val ticket = ClipboardGuardTicket(newId(), timestamp)
        state = state.copy(status = ClipboardGuardStatus.CHANGED, ticket = ticket, eventId = ticket.id, accessIssue = null)
        if (state.options.clearMode == ClipboardClearMode.AUTOMATIC) clear(ticket.id)
    }

    /** A foreground command inspects existing content without silently applying automatic mode. */
    fun inspectCurrent(isActive: () -> Boolean): ClipboardGuardState {
        if (!state.options.listening || !mayAccess() || !isActive()) return state.copy(ticket = null)
        try {
            val timestamp = clipboard.timestamp()
            if (!mayAccess() || !isActive()) return state.copy(ticket = null)
            lastTimestamp = timestamp
            state = state.copy(
                status = if (timestamp == null) ClipboardGuardStatus.EMPTY else ClipboardGuardStatus.CHANGED,
                ticket = timestamp?.let { ClipboardGuardTicket(newId(), it, userRequested = true) },
                accessIssue = null,
                eventId = null,
            )
        } catch (failure: RuntimeException) { failed(failure) }
        return state
    }

    fun clear(
        id: String,
        authenticate: () -> Boolean = { false },
        isActive: () -> Boolean = { true },
    ): ClipboardClearResult {
        val ticket = state.ticket
        if (ticket == null || ticket.id != id || !state.options.listening ||
            (state.options.clearMode == ClipboardClearMode.NONE && !ticket.userRequested) || !mayAccess() || !isActive()
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
        } catch (failure: RuntimeException) {
            failed(failure)
            ClipboardClearResult.FAILED
        }
    }

    private fun failed(failure: RuntimeException) {
        val issue = (failure as? ClipboardAccessException)?.issue
        state = state.copy(status = if (issue == null) ClipboardGuardStatus.FAILED else ClipboardGuardStatus.BLOCKED,
            ticket = null, accessIssue = issue)
    }

    fun stop() {
        lastTimestamp = null
        state = state.copy(ticket = null, status = if (state.options.listening) {
            ClipboardGuardStatus.UNAVAILABLE
        } else ClipboardGuardStatus.OFF)
    }
}
