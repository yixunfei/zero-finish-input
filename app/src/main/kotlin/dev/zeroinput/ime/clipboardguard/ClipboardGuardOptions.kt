package dev.zeroinput.ime.clipboardguard

internal enum class ClipboardClearMode { NONE, CONFIRM, AUTOMATIC }
internal enum class ClipboardOverlayPosition { TOP, BOTTOM }

internal data class ClipboardGuardOptions(
    val listening: Boolean = false,
    val keyboardReminder: Boolean = false,
    val notificationReminder: Boolean = false,
    val clearMode: ClipboardClearMode = ClipboardClearMode.NONE,
    val authenticate: Boolean = false,
    val overlayReminder: Boolean = false,
    val overlayPosition: ClipboardOverlayPosition = ClipboardOverlayPosition.TOP,
    val overlaySeconds: Int = 5,
) {
    fun normalized(): ClipboardGuardOptions = copy(
        clearMode = if (authenticate && clearMode == ClipboardClearMode.AUTOMATIC) ClipboardClearMode.CONFIRM else clearMode,
        overlaySeconds = overlaySeconds.takeIf { it in setOf(3, 5, 10) } ?: 5,
    )
}

internal interface SystemClipboardPort {
    /** Only presence and the platform timestamp; never text, labels or URIs. */
    fun timestamp(): Long?
    fun clear()
    fun listen(onChanged: () -> Unit): AutoCloseable
}

internal data class ClipboardGuardTicket(val id: String, val timestamp: Long, val userRequested: Boolean = false)
internal enum class ClipboardGuardStatus { OFF, UNAVAILABLE, NOT_DEFAULT, WAITING, CHANGED, CLEARED, EMPTY, BLOCKED, FAILED }
internal enum class ClipboardClearResult { CLEARED, EXPIRED, AUTHENTICATION_REQUIRED, FAILED }
internal enum class ClipboardAccessIssue { DEVICE_LOCKED, READ_DENIED, WRITE_DENIED }
internal class ClipboardAccessException(val issue: ClipboardAccessIssue) : RuntimeException()

internal data class ClipboardGuardState(
    val options: ClipboardGuardOptions = ClipboardGuardOptions(),
    val status: ClipboardGuardStatus = ClipboardGuardStatus.OFF,
    val ticket: ClipboardGuardTicket? = null,
    val accessIssue: ClipboardAccessIssue? = null,
    val eventId: String? = null,
)
