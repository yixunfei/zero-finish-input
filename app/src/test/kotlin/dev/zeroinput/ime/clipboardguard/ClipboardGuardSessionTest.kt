package dev.zeroinput.ime.clipboardguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardGuardSessionTest {
    @Test
    fun defaultsNeverInspectOrClearTheClipboard() {
        val fixture = Fixture()
        fixture.policy.start(ClipboardGuardOptions())
        fixture.policy.changed()
        assertEquals(0, fixture.port.reads)
        assertEquals(0, fixture.port.clears)
        assertFalse(fixture.policy.state.options.listening)
        assertFalse(fixture.policy.state.options.keyboardReminder)
        assertFalse(fixture.policy.state.options.notificationReminder)
        assertFalse(fixture.policy.state.options.authenticate)
        assertEquals(ClipboardGuardStatus.OFF, fixture.policy.state.status)
    }

    @Test
    fun enablingMonitoringEstablishesABaselineWithoutClearingExistingContent() {
        val fixture = Fixture()
        fixture.port.stamp = 1
        fixture.policy.start(ClipboardGuardOptions(listening = true, clearMode = ClipboardClearMode.AUTOMATIC))
        fixture.policy.changed()
        assertEquals(0, fixture.port.clears)
        assertNull(fixture.policy.state.ticket)
        fixture.port.stamp = 2
        fixture.policy.changed()
        assertEquals(1, fixture.port.clears)
        fixture.policy.changed()
        fixture.policy.changed()
        assertEquals(1, fixture.port.clears)
    }

    @Test
    fun remindersAndMonitoringDoNotImplicitlyEnableCleanup() {
        for (keyboard in listOf(false, true)) for (notifications in listOf(false, true)) {
            val fixture = Fixture()
            fixture.policy.start(ClipboardGuardOptions(listening = true, keyboardReminder = keyboard, notificationReminder = notifications))
            fixture.port.stamp = 1
            fixture.policy.changed()
            val ticket = checkNotNull(fixture.policy.state.ticket)
            assertEquals(ClipboardClearResult.EXPIRED, fixture.policy.clear(ticket.id))
            assertEquals(0, fixture.port.clears)
        }
    }

    @Test
    fun duplicateClassificationCallbacksAndEmptyEventsDoNotCreateRepeatedRequests() {
        val fixture = Fixture()
        fixture.policy.start(ClipboardGuardOptions(listening = true, clearMode = ClipboardClearMode.CONFIRM))
        fixture.port.stamp = 1
        fixture.policy.changed()
        val first = fixture.policy.state.ticket
        fixture.policy.changed()
        assertEquals(first, fixture.policy.state.ticket)
        fixture.port.stamp = null
        fixture.policy.changed()
        assertNull(fixture.policy.state.ticket)
        assertEquals(0, fixture.port.clears)
    }

    @Test
    fun clearingRequiresTheCurrentTicketAndCannotRunTwice() {
        val fixture = prepared()
        val id = checkNotNull(fixture.policy.state.ticket).id
        assertEquals(ClipboardClearResult.EXPIRED, fixture.policy.clear("old"))
        assertEquals(ClipboardClearResult.CLEARED, fixture.policy.clear(id))
        assertEquals(ClipboardClearResult.EXPIRED, fixture.policy.clear(id))
        assertEquals(1, fixture.port.clears)
    }

    @Test
    fun authenticationIsIndependentFromConfirmationAndDisablesSilentAutomaticCleanup() {
        val fixture = Fixture()
        fixture.policy.start(ClipboardGuardOptions(listening = true, clearMode = ClipboardClearMode.AUTOMATIC, authenticate = true))
        assertEquals(ClipboardClearMode.CONFIRM, fixture.policy.state.options.clearMode)
        fixture.port.stamp = 1
        fixture.policy.changed()
        val id = checkNotNull(fixture.policy.state.ticket).id
        assertEquals(0, fixture.port.clears)
        assertEquals(ClipboardClearResult.AUTHENTICATION_REQUIRED, fixture.policy.clear(id))
        assertEquals(ClipboardClearResult.AUTHENTICATION_REQUIRED, fixture.policy.clear(id, authenticate = { false }))
        assertEquals(0, fixture.port.clears)
        assertEquals(ClipboardClearResult.CLEARED, fixture.policy.clear(id, authenticate = { true }))
    }

    @Test
    fun settingsChangesAndNewClipsInvalidateOldConfirmations() {
        val fixture = prepared()
        val id = checkNotNull(fixture.policy.state.ticket).id
        fixture.port.stamp = 2
        fixture.policy.changed()
        assertEquals(ClipboardClearResult.EXPIRED, fixture.policy.clear(id))
        val current = checkNotNull(fixture.policy.state.ticket).id
        fixture.policy.start(ClipboardGuardOptions(listening = false))
        assertEquals(ClipboardClearResult.EXPIRED, fixture.policy.clear(current))
        assertEquals(0, fixture.port.clears)
    }

    @Test
    fun aChangeWithoutADeliveredCallbackIsCheckedAgainBeforeClearing() {
        val fixture = prepared()
        val id = checkNotNull(fixture.policy.state.ticket).id
        fixture.port.stamp = 2
        assertEquals(ClipboardClearResult.EXPIRED, fixture.policy.clear(id))
        assertEquals(0, fixture.port.clears)
    }

    @Test
    fun revokedDefaultImeAndCancelledPagesCannotClearEvenAfterAuthentication() {
        for (revoke in listOf(true, false)) {
            val fixture = prepared()
            val id = checkNotNull(fixture.policy.state.ticket).id
            fixture.allowed = !revoke
            assertEquals(ClipboardClearResult.EXPIRED, fixture.policy.clear(id, isActive = { revoke }))
            assertEquals(0, fixture.port.clears)
        }
        val fixture = prepared()
        val id = checkNotNull(fixture.policy.state.ticket).id
        fixture.port.onRead = { fixture.allowed = false }
        assertEquals(ClipboardClearResult.EXPIRED, fixture.policy.clear(id))
        assertEquals(0, fixture.port.clears)
    }

    @Test
    fun clipboardFailuresFailClosedAndDoNotClaimSuccessfulCleanup() {
        val fixture = prepared()
        val id = checkNotNull(fixture.policy.state.ticket).id
        fixture.port.failClear = true
        assertEquals(ClipboardClearResult.FAILED, fixture.policy.clear(id))
        assertEquals(ClipboardGuardStatus.FAILED, fixture.policy.state.status)
        assertNotNull(fixture.port.stamp)
        fixture.port.failRead = true
        fixture.policy.start(ClipboardGuardOptions(listening = true))
        assertEquals(ClipboardGuardStatus.FAILED, fixture.policy.state.status)
        assertNull(fixture.policy.state.ticket)
    }

    @Test
    fun aNonDefaultImeCannotEvenReadTheBaseline() {
        val fixture = Fixture()
        fixture.allowed = false
        fixture.policy.start(ClipboardGuardOptions(listening = true))
        assertEquals(0, fixture.port.reads)
        assertEquals(ClipboardGuardStatus.UNAVAILABLE, fixture.policy.state.status)
        assertTrue(fixture.policy.state.options.listening)
    }

    private fun prepared() = Fixture().apply {
        policy.start(ClipboardGuardOptions(listening = true, clearMode = ClipboardClearMode.CONFIRM))
        port.stamp = 1
        policy.changed()
    }

    private class Fixture {
        val port = FakeClipboard()
        var allowed = true
        private var id = 0
        val policy = ClipboardGuardSession(port, { allowed }, { (++id).toString() })
    }

    private class FakeClipboard : SystemClipboardPort {
        var stamp: Long? = null
        var reads = 0
        var clears = 0
        var failRead = false
        var failClear = false
        var onRead: () -> Unit = {}
        override fun timestamp(): Long? {
            reads++
            onRead()
            if (failRead) throw IllegalStateException("Fixture failure")
            return stamp
        }
        override fun clear() {
            if (failClear) throw IllegalStateException("Fixture failure")
            clears++
            stamp = null
        }
        override fun listen(onChanged: () -> Unit) = AutoCloseable {}
    }
}
