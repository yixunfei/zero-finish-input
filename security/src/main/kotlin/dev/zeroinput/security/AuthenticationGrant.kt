package dev.zeroinput.security

import android.os.SystemClock
import java.security.SecureRandom

class AuthenticationGrant private constructor(
    private val nonce: Long,
    private val issuedAtMillis: Long,
) {
    @Volatile
    private var consumed = false

    fun consume(maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS): Boolean = synchronized(this) {
        val age = SystemClock.elapsedRealtime() - issuedAtMillis
        val valid = !consumed && nonce != 0L && age in 0..maxAgeMillis
        consumed = true
        valid
    }

    companion object {
        private const val DEFAULT_MAX_AGE_MILLIS = 30_000L
        private val random = SecureRandom()

        fun afterSuccessfulSystemAuthentication(): AuthenticationGrant {
            var nonce = random.nextLong()
            while (nonce == 0L) nonce = random.nextLong()
            return AuthenticationGrant(nonce, SystemClock.elapsedRealtime())
        }
    }
}

