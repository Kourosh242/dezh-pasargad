package com.pasargad.dezh.security

import com.pasargad.dezh.cryptography.AesGcmCipher
import com.pasargad.dezh.cryptography.CryptoConstants
import com.pasargad.dezh.domain.VaultLockState
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSessionTest {

    private val cipher = AesGcmCipher(SecureRandom())
    private val session = VaultSession(cipher)
    private val dek = ByteArray(32).also(SecureRandom()::nextBytes)
    private val secret = "top secret payload".encodeToByteArray()

    @Test
    fun `fresh session starts in initializing`() {
        assertEquals(VaultLockState.Initializing, session.lockState.value)
    }

    @Test
    fun `notSetUp is sticky against lock`() {
        session.markNotSetUp()
        assertEquals(VaultLockState.NotSetUp, session.lockState.value)
        session.lock()
        assertEquals(VaultLockState.NotSetUp, session.lockState.value)
    }

    @Test
    fun `unlock sets unlocked and holds a key copy`() {
        session.unlockWithKey(dek)
        assertEquals(VaultLockState.Unlocked, session.lockState.value)
        assertTrue(session.isUnlocked)
        assertNotNull(session.diagnosticKeySnapshot())
    }

    @Test
    fun `lock zeroes the key and returns to locked`() {
        session.unlockWithKey(dek)
        session.lock()
        assertEquals(VaultLockState.Locked, session.lockState.value)
        assertNull(session.diagnosticKeySnapshot())
    }

    @Test
    fun `operations on a locked session fail closed`() {
        assertThrows(VaultSessionLockedException::class.java) { session.encrypt(secret) }
    }

    @Test
    fun `encrypt decrypt roundtrip while unlocked`() {
        session.unlockWithKey(dek)
        val container = session.encrypt(secret)
        assertEquals(CryptoConstants.KEY_ID_SESSION_DEK, container.keyId)
        assertTrue(secret.contentEquals(session.decrypt(container)))
    }

    @Test
    fun `encrypt after lock throws session locked`() {
        session.unlockWithKey(dek)
        session.lock()
        assertThrows(VaultSessionLockedException::class.java) { session.encrypt(secret) }
    }
}
