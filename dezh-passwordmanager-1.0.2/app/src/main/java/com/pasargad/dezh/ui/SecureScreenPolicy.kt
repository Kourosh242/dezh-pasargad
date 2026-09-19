package com.pasargad.dezh.ui

import com.pasargad.dezh.domain.VaultLockState

/**
 * Screenshot/recents policy: every screen that can hold or reveal credentials —
 * unlock (password field), first-time setup (new master password), and the whole
 * unlocked vault surface — is excluded from screenshots and the app switcher
 * preview (FLAG_SECURE) while [screenshotProtectionEnabled] is on. The user may
 * turn protection off (settings > security > "حفاظت از عکاسی") to allow
 * screenshots; the transient startup splash is always exempt.
 */
fun requiresSecureFlag(
    lockState: VaultLockState,
    screenshotProtectionEnabled: Boolean = true,
): Boolean = screenshotProtectionEnabled && lockState != VaultLockState.Initializing
