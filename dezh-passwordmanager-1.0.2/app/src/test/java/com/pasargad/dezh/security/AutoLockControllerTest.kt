package com.pasargad.dezh.security

import com.pasargad.dezh.cryptography.AesGcmCipher
import com.pasargad.dezh.domain.VaultLockState
import java.security.SecureRandom
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoLockControllerTest {

    private fun newUnlockedSession(): VaultSession {
        val session = VaultSession(AesGcmCipher(SecureRandom()))
        session.unlockWithKey(ByteArray(32))
        return session
    }

    @Test
    fun `background timeout locks the session`() = runTest {
        val session = newUnlockedSession()
        val controller = AutoLockController(backgroundScope, session, timeoutMillis = 5_000L)

        controller.onAppBackgrounded()
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(VaultLockState.Unlocked, session.lockState.value)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(VaultLockState.Locked, session.lockState.value)
    }

    @Test
    fun `returning to foreground cancels the pending lock`() = runTest {
        val session = newUnlockedSession()
        val controller = AutoLockController(backgroundScope, session, timeoutMillis = 5_000L)

        controller.onAppBackgrounded()
        advanceTimeBy(3_000)
        controller.onAppForegrounded()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(VaultLockState.Unlocked, session.lockState.value)
    }

    @Test
    fun `background while locked does nothing`() = runTest {
        val session = VaultSession(AesGcmCipher(SecureRandom()))
        session.markLocked()
        val controller = AutoLockController(backgroundScope, session, timeoutMillis = 1_000L)
        controller.onAppBackgrounded()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(VaultLockState.Locked, session.lockState.value)
    }

    @Test
    fun `repeated background events restart the timer`() = runTest {
        val session = newUnlockedSession()
        val controller = AutoLockController(backgroundScope, session, timeoutMillis = 5_000L)
        controller.onAppBackgrounded()
        advanceTimeBy(4_000)
        controller.onAppBackgrounded() // restarts
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(VaultLockState.Unlocked, session.lockState.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(VaultLockState.Locked, session.lockState.value)
    }

    @Test
    fun `immediately policy (0 minutes) locks right after backgrounding`() = runTest {
        val session = newUnlockedSession()
        val controller = AutoLockController(backgroundScope, session, timeoutMillis = 60_000L)
        controller.updateTimeoutMinutes(0)

        controller.onAppBackgrounded()
        advanceTimeBy(1)
        runCurrent()
        assertEquals(VaultLockState.Locked, session.lockState.value)
    }

    @Test
    fun `forbidden never value fails closed to immediate lock`() = runTest {
        val session = newUnlockedSession()
        val controller = AutoLockController(backgroundScope, session, timeoutMillis = 60_000L)
        controller.updateTimeoutMinutes(-1) // never is NOT permitted by the security policy

        controller.onAppBackgrounded()
        advanceTimeBy(1)
        runCurrent()
        assertEquals(VaultLockState.Locked, session.lockState.value)
    }

    @Test
    fun `one minute policy locks after 60 seconds in background`() = runTest {
        val session = newUnlockedSession()
        val controller = AutoLockController(backgroundScope, session, timeoutMillis = 60_000L)
        controller.updateTimeoutMinutes(1)

        controller.onAppBackgrounded()
        advanceTimeBy(59_999)
        runCurrent()
        assertEquals(VaultLockState.Unlocked, session.lockState.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(VaultLockState.Locked, session.lockState.value)
    }

    @Test
    fun `screen off applies the configured auto-lock policy`() = runTest {
        val session = newUnlockedSession()
        val controller = AutoLockController(backgroundScope, session, timeoutMillis = 1_000L)

        controller.onScreenOff()
        advanceTimeBy(999)
        runCurrent()
        assertEquals(VaultLockState.Unlocked, session.lockState.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(VaultLockState.Locked, session.lockState.value)
    }

    @Test
    fun `screen off with immediate policy locks at once`() = runTest {
        val session = newUnlockedSession()
        val controller = AutoLockController(backgroundScope, session, timeoutMillis = 10_000L)
        controller.updateTimeoutMinutes(0)

        controller.onScreenOff()
        advanceTimeBy(1)
        runCurrent()
        assertEquals(VaultLockState.Locked, session.lockState.value)
    }
}
