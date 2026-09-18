package com.pasargad.dezh.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordStrengthMeterTest {

    private val meter = PasswordStrengthMeter()

    private fun score(raw: String): PasswordStrengthMeter.Result = meter.evaluate(raw.toCharArray())

    @Test
    fun `empty password scores zero`() {
        val result = score("")
        assertEquals(0, result.score)
        assertEquals(PasswordStrengthMeter.Band.WEAK, result.band)
    }

    @Test
    fun `common structure is weak even with classes`() {
        val result = score("Password1!")
        assertTrue("score=${result.score}", result.score < 35)
        assertEquals(PasswordStrengthMeter.Band.WEAK, result.band)
    }

    @Test
    fun `sequential patterns are penalized`() {
        val sequential = score("Abcd1234!x")
        val random = score("A7x#mQ2vL8")
        assertTrue(sequential.score < random.score)
    }

    @Test
    fun `repeated runs are penalized`() {
        val repeated = score("Aaa111!!!b")
        val varied = score("Aa1!Bb2@Cc3#")
        assertTrue(repeated.score < varied.score)
    }

    @Test
    fun `long mixed password is strong`() {
        val result = score("xK9#mQ2vL8pWz4!T")
        assertEquals(PasswordStrengthMeter.Band.STRONG, result.band)
        assertTrue(result.score >= 65)
    }

    @Test
    fun `score stays within bounds`() {
        repeat(20) { index ->
            val result = score("a${'$'}{index}b")
            assertTrue(result.score in 0..100)
        }
    }

    @Test
    fun `ten digit numeric password is weak - reported bug`() {
        val result = score("1234567890")
        assertEquals(PasswordStrengthMeter.Band.WEAK, result.band)
        assertTrue("score=${result.score}", result.score < 28)
    }

    @Test
    fun `mixed persian password is not weak`() {
        val result = score("رمز#قوی123Xy")
        assertTrue("band=${result.band}", result.band != PasswordStrengthMeter.Band.WEAK)
    }

    @Test
    fun `score grows with length for same charset`() {
        val short = score("aB1!xK9#")
        val long = score("aB1!xK9#mQ2vL8pW")
        assertTrue(long.score > short.score)
    }
}
