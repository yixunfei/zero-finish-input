package dev.zeroinput.ime.clipboard

import dev.zeroinput.security.AuthenticationGrant
import org.junit.Assert.*
import org.junit.Test

class SecurePasteConsentTest {
    private val source = PasteEditorIdentity("public.fixture", 7, 1)

    @Test
    fun authenticationNeverProducesAPasteWithoutReturnBindingAndExplicitConfirmation() {
        val consent = request()
        val grant = grant()
        assertTrue(consent.leaveEditor())
        assertTrue(consent.authorize(grant))
        assertFalse(consent.ready(2))
        assertTrue(consent.bind(2, source))
        assertTrue(consent.ready(2))
        assertSame(grant, consent.confirm(2))
        assertNull(consent.confirm(2))
        assertTrue(grant.consume())
        assertFalse(grant.consume())
    }

    @Test
    fun anotherEditorOrAnotherSessionCannotReceiveThePendingGrant() {
        val consent = request()
        consent.authorize(grant())
        assertFalse(consent.bind(2, source.copy(fieldId = 8)))
        assertNull(consent.confirm(2))
        val rebound = request()
        rebound.authorize(grant())
        assertTrue(rebound.bind(2, source))
        assertFalse(rebound.bind(3, source))
        assertNull(rebound.confirm(3))
    }

    @Test
    fun leavingAfterReturnBindingRevokesConfirmationEvenWhenReturningToTheSameField() {
        val consent = request()
        consent.authorize(grant())
        consent.bind(2, source)
        assertFalse(consent.leaveEditor())
        assertFalse(consent.bind(3, source))
        assertNull(consent.confirm(3))
    }

    @Test
    fun cancelledDuplicateDeniedAndExpiredCallbacksCannotReauthorize() {
        val cancelled = request()
        cancelled.close()
        assertFalse(cancelled.authorize(grant()))
        val denied = request()
        assertFalse(denied.authorize(null))
        assertFalse(denied.authorize(grant()))
        val duplicate = request()
        assertTrue(duplicate.authorize(grant()))
        assertFalse(duplicate.authorize(grant()))
        assertFalse(duplicate.bind(2, source))
        var time = 0L
        val expired = SecurePasteConsent("fixture", source, 0) { time }
        expired.authorize(grant())
        expired.bind(2, source)
        time = 30_000L
        assertFalse(expired.ready(2))
        assertNull(expired.confirm(2))
    }

    @Test
    fun returnBeforeAuthenticationDoesNotAllowPrematureConfirmation() {
        val consent = request()
        consent.bind(2, source)
        assertFalse(consent.ready(2))
        assertNull(consent.confirm(2))
        assertFalse(consent.authorize(grant()))
    }

    @Test
    fun temporaryCredentialEditorCannotReceiveAGrantAndSourceEditsRevokeThePendingOperation() {
        val consent = request()
        assertTrue(consent.editorChanged(source.copy(packageName = "credential.fixture", inputType = 18)))
        assertFalse(consent.ready(1))
        assertFalse(consent.editorChanged(source))
        assertFalse(consent.authorize(grant()))
        val returned = request()
        returned.authorize(grant())
        returned.bind(2, source)
        assertFalse(returned.editorChanged(source.copy(fieldId = 8)))
        assertNull(returned.confirm(2))
    }

    private fun request() = SecurePasteConsent("fixture", source, 7) { 0L }
    private fun grant() = AuthenticationGrant.afterSuccessfulSystemAuthentication()
}
