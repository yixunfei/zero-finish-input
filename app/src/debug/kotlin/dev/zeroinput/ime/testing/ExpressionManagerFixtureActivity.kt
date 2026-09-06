package dev.zeroinput.ime.testing

import dev.zeroinput.ime.expressions.ExpressionManagerActivity
import dev.zeroinput.security.EncryptedStore
import dev.zeroinput.userdata.PersonalExpressionRepository

/** Nonexported UI fixture. It never opens production expression storage. */
class ExpressionManagerFixtureActivity : ExpressionManagerActivity() {
    override val repository: PersonalExpressionRepository get() = fixtureRepository

    companion object {
        val fixtureRepository = PersonalExpressionRepository(object : EncryptedStore {
            private var bytes: ByteArray? = null
            override fun read() = bytes?.copyOf()
            override fun write(plaintext: ByteArray) {
                bytes?.fill(0)
                bytes = plaintext.copyOf()
                plaintext.fill(0)
            }
            override fun delete(deleteKey: Boolean) { bytes?.fill(0); bytes = null }
        })
    }
}
