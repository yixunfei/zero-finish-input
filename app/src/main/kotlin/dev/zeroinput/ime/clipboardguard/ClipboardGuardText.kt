package dev.zeroinput.ime.clipboardguard

import androidx.annotation.StringRes
import dev.zeroinput.ime.R

@StringRes
internal fun ClipboardGuardState.statusText(): Int = when (accessIssue) {
    ClipboardAccessIssue.DEVICE_LOCKED -> R.string.clipboard_guard_locked
    ClipboardAccessIssue.READ_DENIED -> R.string.clipboard_guard_read_denied
    ClipboardAccessIssue.WRITE_DENIED -> R.string.clipboard_guard_write_denied
    null -> when (status) {
        ClipboardGuardStatus.OFF -> R.string.clipboard_guard_off
        ClipboardGuardStatus.UNAVAILABLE -> R.string.clipboard_guard_unavailable
        ClipboardGuardStatus.NOT_DEFAULT -> R.string.clipboard_guard_not_default
        ClipboardGuardStatus.WAITING -> R.string.clipboard_guard_waiting
        ClipboardGuardStatus.CHANGED -> R.string.clipboard_guard_changed
        ClipboardGuardStatus.CLEARED -> R.string.clipboard_guard_cleared
        ClipboardGuardStatus.EMPTY -> R.string.clipboard_guard_empty
        ClipboardGuardStatus.BLOCKED -> R.string.clipboard_guard_read_denied
        ClipboardGuardStatus.FAILED -> R.string.clipboard_guard_failed
    }
}
