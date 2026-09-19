package com.pasargad.dezh.security

import org.junit.Assert.assertEquals
import org.junit.Test

class LoginAttemptPolicyTest {

    @Test
    fun `backoff grows exponentially and is capped`() {
        assertEquals(0L, LoginAttemptPolicy.backoffDelayMillis(0))
        assertEquals(1_000L, LoginAttemptPolicy.backoffDelayMillis(1))
        assertEquals(2_000L, LoginAttemptPolicy.backoffDelayMillis(2))
        assertEquals(4_000L, LoginAttemptPolicy.backoffDelayMillis(3))
        assertEquals(8_000L, LoginAttemptPolicy.backoffDelayMillis(4))
        assertEquals(15_000L, LoginAttemptPolicy.backoffDelayMillis(5))
        assertEquals(LoginAttemptPolicy.MAX_DELAY_MS, LoginAttemptPolicy.backoffDelayMillis(50))
    }

    @Test
    fun `remaining backoff shrinks with elapsed time`() {
        val lastFailedAt = 1_000_000L
        assertEquals(500L, LoginAttemptPolicy.remainingBackoffMillis(1, lastFailedAt, nowMs = 1_000_500L))
        assertEquals(0L, LoginAttemptPolicy.remainingBackoffMillis(1, lastFailedAt, nowMs = 1_001_000L))
        assertEquals(0L, LoginAttemptPolicy.remainingBackoffMillis(1, lastFailedAt, nowMs = 1_099_999L))
    }

    @Test
    fun `no backoff without failures`() {
        assertEquals(0L, LoginAttemptPolicy.remainingBackoffMillis(0, lastFailedAtMs = 1L, nowMs = 1L))
        assertEquals(0L, LoginAttemptPolicy.remainingBackoffMillis(3, lastFailedAtMs = 0L, nowMs = 1L))
    }

    @Test
    fun `negative elapsed time is clamped`() {
        assertEquals(1_000L, LoginAttemptPolicy.remainingBackoffMillis(1, lastFailedAtMs = 2_000L, nowMs = 1_000L))
    }
}
