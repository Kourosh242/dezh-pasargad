package com.pasargad.dezh.ui

import com.pasargad.dezh.domain.VaultLockState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FLAG_SECURE policy: everything after the startup splash hides from recents/screenshots. */
class SecureScreenPolicyTest {

    @Test
    fun `startup splash does not require the secure flag`() {
        assertFalse(requiresSecureFlag(VaultLockState.Initializing))
    }

    @Test
    fun `setup requires the secure flag - master password entry`() {
        assertTrue(requiresSecureFlag(VaultLockState.NotSetUp))
    }

    @Test
    fun `locked unlock screen requires the secure flag - password field`() {
        assertTrue(requiresSecureFlag(VaultLockState.Locked))
    }

    @Test
    fun `unlocked vault surface requires the secure flag`() {
        assertTrue(requiresSecureFlag(VaultLockState.Unlocked))
    }

    @Test
    fun `protection disabled allows screenshots on every state`() {
        assertFalse(requiresSecureFlag(VaultLockState.Initializing, screenshotProtectionEnabled = false))
        assertFalse(requiresSecureFlag(VaultLockState.NotSetUp, screenshotProtectionEnabled = false))
        assertFalse(requiresSecureFlag(VaultLockState.Locked, screenshotProtectionEnabled = false))
        assertFalse(requiresSecureFlag(VaultLockState.Unlocked, screenshotProtectionEnabled = false))
    }

    @Test
    fun `protection enabled keeps the default behaviour`() {
        assertTrue(requiresSecureFlag(VaultLockState.Locked, screenshotProtectionEnabled = true))
        assertTrue(requiresSecureFlag(VaultLockState.Unlocked, screenshotProtectionEnabled = true))
        assertFalse(requiresSecureFlag(VaultLockState.Initializing, screenshotProtectionEnabled = true))
    }
}
