package com.pasargad.dezh.security

/**
 * Interactive brute-force throttling policy.
 * Exponential backoff on consecutive wrong passwords, capped at [MAX_DELAY_MS].
 * Pure functions so the exact behavior is unit-testable and the UI can display it.
 */
object LoginAttemptPolicy {

    const val FIRST_DELAY_MS = 1_000L
    const val SECOND_DELAY_MS = 2_000L
    const val THIRD_DELAY_MS = 4_000L
    const val FOURTH_DELAY_MS = 8_000L
    const val MAX_DELAY_MS = 15_000L

    fun backoffDelayMillis(failedAttemptCount: Int): Long = when (failedAttemptCount) {
        0 -> 0L
        1 -> FIRST_DELAY_MS
        2 -> SECOND_DELAY_MS
        3 -> THIRD_DELAY_MS
        4 -> FOURTH_DELAY_MS
        else -> MAX_DELAY_MS
    }

    /** How much of the current backoff is still pending at [nowMs] (0 = none). */
    fun remainingBackoffMillis(failedAttemptCount: Int, lastFailedAtMs: Long, nowMs: Long): Long {
        if (failedAttemptCount <= 0 || lastFailedAtMs <= 0) return 0L
        val delay = backoffDelayMillis(failedAttemptCount)
        val elapsed = (nowMs - lastFailedAtMs).coerceAtLeast(0L)
        return (delay - elapsed).coerceAtLeast(0L)
    }
}
