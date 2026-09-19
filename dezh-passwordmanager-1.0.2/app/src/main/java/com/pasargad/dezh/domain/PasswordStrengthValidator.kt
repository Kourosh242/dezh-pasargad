package com.pasargad.dezh.domain

/**
 * Pure-Kotlin master password policy (unit-testable, no Android dependencies).
 *
 * Rules:
 *  - length in [MIN_LENGTH, MAX_LENGTH]
 *  - at least MIN_CLASSES categories among {letters, digits, symbols, uppercase}
 *    (Unicode-aware: Persian letters count as letters, Persian digits as digits)
 *  - not in the embedded common-password list
 *  - no run of MAX_REPEATED_RUN identical characters
 */
class PasswordStrengthValidator {

    enum class Rating { Weak, Fair, Strong }

    enum class Failure {
        TOO_SHORT,
        TOO_LONG,
        NOT_ENOUGH_CHARACTER_CLASSES,
        COMMON_PASSWORD,
        REPEATED_SEQUENCE,
    }

    data class Result(
        val rating: Rating,
        val isValid: Boolean,
        val failures: List<Failure>,
    )

    fun evaluate(password: CharArray): Result {
        val failures = mutableListOf<Failure>()

        if (password.size < MIN_LENGTH) failures += Failure.TOO_SHORT
        if (password.size > MAX_LENGTH) failures += Failure.TOO_LONG

        var hasLetter = false
        var hasUppercase = false
        var hasDigit = false
        var hasSymbol = false
        for (c in password) {
            when {
                // Digits first: Unicode-aware (Persian ۱۲۳ count as digits).
                Character.isDigit(c) -> hasDigit = true
                // Letters cover caseless scripts (Arabic/Persian) too.
                Character.isLetter(c) -> {
                    hasLetter = true
                    if (Character.isUpperCase(c)) hasUppercase = true
                }
                !c.isWhitespace() -> hasSymbol = true
            }
        }
        val classCount = listOf(hasLetter, hasUppercase, hasDigit, hasSymbol).count { it }
        if (classCount < MIN_CLASSES) failures += Failure.NOT_ENOUGH_CHARACTER_CLASSES

        if (String(password).lowercase() in COMMON_PASSWORDS) failures += Failure.COMMON_PASSWORD
        if (hasRepeatedRun(password)) failures += Failure.REPEATED_SEQUENCE

        // Rating among policy-valid passwords follows the shared entropy meter
        // so setup feedback and the entry meter tell the same story.
        val rating = when {
            failures.isNotEmpty() -> Rating.Weak
            meter.evaluate(password).band == PasswordStrengthMeter.Band.STRONG -> Rating.Strong
            else -> Rating.Fair
        }
        return Result(rating = rating, isValid = failures.isEmpty(), failures = failures)
    }

    private fun hasRepeatedRun(password: CharArray): Boolean {
        var run = 1
        for (i in 1 until password.size) {
            run = if (password[i] == password[i - 1]) run + 1 else 1
            if (run >= MAX_REPEATED_RUN) return true
        }
        return false
    }

    private val meter = PasswordStrengthMeter()

    companion object {
        const val MIN_LENGTH = 10
        const val STRONG_LENGTH = 14
        const val MAX_LENGTH = 128
        const val MIN_CLASSES = 3

        private const val MAX_REPEATED_RUN = 4

        private val COMMON_PASSWORDS = setOf(
            "password", "password1", "password123", "passw0rd",
            "123456", "1234567", "12345678", "123456789", "1234567890",
            "qwerty", "qwerty123", "1q2w3e4r", "abc123", "asdfgh", "zxcvbnm",
            "iloveyou", "admin", "welcome", "monkey", "letmein", "dragon",
            "111111", "000000", "sunshine", "princess", "football", "master",
            "dezhpasargad", "dezh-pasargad", "pasargad", "رمزعبور",
        )
    }
}
