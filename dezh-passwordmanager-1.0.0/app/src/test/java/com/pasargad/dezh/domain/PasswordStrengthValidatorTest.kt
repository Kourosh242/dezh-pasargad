package com.pasargad.dezh.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordStrengthValidatorTest {

    private val validator = PasswordStrengthValidator()

    private fun evaluate(raw: String): PasswordStrengthValidator.Result = validator.evaluate(raw.toCharArray())

    @Test
    fun `too short password is invalid`() {
        val result = evaluate("aB1!x")
        assertFalse(result.isValid)
        assertTrue(PasswordStrengthValidator.Failure.TOO_SHORT in result.failures)
        assertEquals(PasswordStrengthValidator.Rating.Weak, result.rating)
    }

    @Test
    fun `common password is rejected`() {
        val result = evaluate("password123")
        assertFalse(result.isValid)
        assertTrue(PasswordStrengthValidator.Failure.COMMON_PASSWORD in result.failures)
    }

    @Test
    fun `missing character classes is rejected`() {
        // only letters (no digit, no symbol, no uppercase)
        val result = evaluate("abcdefghij")
        assertFalse(result.isValid)
        assertTrue(PasswordStrengthValidator.Failure.NOT_ENOUGH_CHARACTER_CLASSES in result.failures)
    }

    @Test
    fun `valid medium password rates fair`() {
        val result = evaluate("abcdefgh1!")
        assertTrue(result.isValid)
        assertEquals(PasswordStrengthValidator.Rating.Fair, result.rating)
    }

    @Test
    fun `long mixed password rates strong`() {
        val result = evaluate("xK9#mQ2\$vL8pWz4")
        assertTrue(result.isValid)
        assertEquals(PasswordStrengthValidator.Rating.Strong, result.rating)
    }

    @Test
    fun `persian password with letters digits and symbols is accepted`() {
        val result = evaluate("رمزعبوربسیارقوی۱۲۳!")
        assertTrue(result.isValid)
    }

    @Test
    fun `persian letters only is rejected`() {
        val result = evaluate("رمزعبوربسیارقویتر")
        assertFalse(result.isValid)
        assertTrue(PasswordStrengthValidator.Failure.NOT_ENOUGH_CHARACTER_CLASSES in result.failures)
    }

    @Test
    fun `long repeated run is rejected`() {
        val result = evaluate("aaaaaaaaa1!aA")
        assertFalse(result.isValid)
        assertTrue(PasswordStrengthValidator.Failure.REPEATED_SEQUENCE in result.failures)
    }

    @Test
    fun `too long password is rejected`() {
        val overlong = buildString {
            append("aB1!")
            repeat(32) { append("xY9z") }
        } // 132 chars
        val result = evaluate(overlong)
        assertFalse(result.isValid)
        assertTrue(PasswordStrengthValidator.Failure.TOO_LONG in result.failures)
    }
}
