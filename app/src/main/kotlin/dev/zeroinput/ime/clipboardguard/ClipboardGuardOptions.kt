package dev.zeroinput.ime.clipboardguard

internal enum class ClipboardClearMode { NONE, CONFIRM, AUTOMATIC }

internal data class ClipboardGuardOptions(
    val listening: Boolean = false,
    val keyboardReminder: Boolean = false,
    val notificationReminder: Boolean = false,
    val clearMode: ClipboardClearMode = ClipboardClearMode.NONE,
    val authenticate: Boolean = false,
) {
    fun normalized(): ClipboardGuardOptions = if (authenticate && clearMode == ClipboardClearMode.AUTOMATIC) {
        copy(clearMode = ClipboardClearMode.CONFIRM)
    } else this
}

internal interface SystemClipboardPort {
    /** Only presence and the platform timestamp; never text, labels or URIs. */
    fun timestamp(): Long?
    fun clear()
    fun listen(onChanged: () -> Unit): AutoCloseable
}

internal data class ClipboardGuardTicket(val id: String, val timestamp: Long)
internal enum class ClipboardGuardStatus { OFF, UNAVAILABLE, WAITING, CHANGED, CLEARED, FAILED }
internal enum class ClipboardClearResult { CLEARED, EXPIRED, AUTHENTICATION_REQUIRED, FAILED }

internal data class ClipboardGuardState(
    val options: ClipboardGuardOptions = ClipboardGuardOptions(),
    val status: ClipboardGuardStatus = ClipboardGuardStatus.OFF,
    val ticket: ClipboardGuardTicket? = null,
)
