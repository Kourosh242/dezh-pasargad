package com.pasargad.dezh.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Automatic lock infrastructure. The app-level lifecycle (ProcessLifecycleOwner)
 * reports foreground/background transitions; this class owns the timer policy.
 *
 * The timeout is user-configurable from Settings; the policy remains
 * fail-closed: any uncertainty ends in `session.lock()`.
 */
class AutoLockController(
    private val scope: CoroutineScope,
    private val session: VaultSession,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    private val timeSource: () -> Long = System::currentTimeMillis,
) {

    @Volatile
    private var timeoutMillis = timeoutMillis

    private var backgroundedAtMs: Long? = null
    private var lockJob: Job? = null

    /**
     * Settings hook: 0 minutes locks immediately on backgrounding. Unknown or
     * negative values (including a forbidden "never") fail closed to immediate.
     */
    fun updateTimeoutMinutes(minutes: Int) {
        timeoutMillis = minutes.coerceAtLeast(0).toLong() * MILLIS_PER_MINUTE
    }

    /**
     * Screen-off is handled with the same policy as backgrounding (the user
     * setting applies). The policy itself stays fail-closed at every point.
     */
    fun onScreenOff() {
        onAppBackgrounded()
    }

    fun onAppBackgrounded() {
        if (!session.isUnlocked) return
        backgroundedAtMs = timeSource()
        lockJob?.cancel()
        lockJob = scope.launch {
            delay(timeoutMillis)
            session.lock()
        }
    }

    fun onAppForegrounded() {
        lockJob?.cancel()
        lockJob = null
        backgroundedAtMs = null
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
