package com.pasargad.dezh.domain

import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Practical strength meter for UI feedback, aligned with NIST SP 800-63B
 * Appendix A thinking: raw length × alphabet size is a *necessary* start, but
 * guessable structure (sequential/repeated runs, common-password fragments)
 * must reduce the estimate — the same philosophy as the zxcvbn model, kept as
 * a lightweight offline heuristic (no breach-corpus download, no network).
 *
 * Output: estimated entropy bits (clamped 0..100) and a 3-level band:
 *  - WEAK  (< 28 bits)  → «ضعیف»
 *  - FAIR  (28–49 bits) → «قابل قبول»
 *  - STRONG (≥ 50 bits) → «خیلی خوب»
 */
class PasswordStrengthMeter {

    enum class Band { WEAK, FAIR, STRONG }

    data class Result(val score: Int, val band: Band)

    fun evaluate(password: CharArray): Result {
        if (password.isEmpty()) return Result(SCORE_MIN, Band.WEAK)

        val entropyBits = password.size * log2(poolSize(classify(password)).toDouble())
        var penalties = 0.0
        penalties += PENALTY_PER_SEQUENTIAL_RUN * countSequentialRuns(password)
        penalties += PENALTY_PER_REPEATED_RUN * countRepeatedRuns(password)
        if (containsCommonFragment(password)) penalties += PENALTY_COMMON
        if (containsContextFragment(password)) penalties += PENALTY_CONTEXT

        val effective = max(0.0, entropyBits - penalties)
        val score = effective.coerceAtMost(SCORE_MAX.toDouble()).roundToInt()
        val band = when {
            score < THRESHOLD_WEAK -> Band.WEAK
            score < THRESHOLD_STRONG -> Band.FAIR
            else -> Band.STRONG
        }
        return Result(score = score, band = band)
    }

    /** Character-class flags extracted in a single pass. */
    private data class ClassFlags(
        val lower: Boolean,
        val upper: Boolean,
        val digit: Boolean,
        val symbol: Boolean,
        val space: Boolean,
        val nonLatinLetter: Boolean,
    )

    private fun classify(password: CharArray): ClassFlags {
        var lower = false
        var upper = false
        var digit = false
        var symbol = false
        var space = false
        var nonLatin = false
        for (c in password) {
            when {
                c.isWhitespace() -> space = true
                c.isDigit() -> digit = true
                isLatinLetter(c) -> {
                    if (c.isLowerCase()) lower = true
                    if (c.isUpperCase()) upper = true
                }
                c.isLetter() -> nonLatin = true
                else -> symbol = true
            }
        }
        return ClassFlags(lower, upper, digit, symbol, space, nonLatin)
    }

    private fun isLatinLetter(c: Char): Boolean = c.code < LATIN_UPPER_BOUND && c.isLetter()

    /** Approximate alphabet size actually used by the password. */
    private fun poolSize(flags: ClassFlags): Int {
        var pool = 0
        if (flags.lower) pool += POOL_LOWER
        if (flags.upper) pool += POOL_UPPER
        if (flags.digit) pool += POOL_DIGIT
        if (flags.symbol) pool += POOL_SYMBOL
        if (flags.space) pool += POOL_SPACE
        // Persian/Arabic and other non-Latin letters: a practical band of common
        // graphemes so mixed-script passwords are not under-counted.
        if (flags.nonLatinLetter) pool += POOL_NON_LATIN
        return max(pool, 1)
    }

    /** Non-overlapping ascending/descending runs of ≥3 letter/digit chars. */
    private fun countSequentialRuns(password: CharArray): Int {
        var count = 0
        var run = 1
        for (i in 1 until password.size) {
            val delta = password[i].code - password[i - 1].code
            run = if ((delta == 1 || delta == -1) && password[i].isLetterOrDigit()) run + 1 else 1
            if (run == SEQUENTIAL_RUN_LENGTH) {
                count++
                run = 1 // restart counting: non-overlapping
            }
        }
        return count
    }

    /** Non-overlapping runs of the same character with length ≥3. */
    private fun countRepeatedRuns(password: CharArray): Int {
        var count = 0
        var run = 1
        for (i in 1 until password.size) {
            run = if (password[i] == password[i - 1]) run + 1 else 1
            if (run == REPEATED_RUN_LENGTH) {
                count++
                run = 1
            }
        }
        return count
    }

    private fun containsCommonFragment(password: CharArray): Boolean {
        val lower = String(password).lowercase()
        return COMMON_FRAGMENTS.any { it in lower }
    }

    private fun containsContextFragment(password: CharArray): Boolean {
        val lower = String(password).lowercase()
        return CONTEXT_FRAGMENTS.any { it in lower }
    }

    private companion object {
        const val SCORE_MIN = 0
        const val SCORE_MAX = 100

        // Alphabet sizes per character class (printable ASCII conventions).
        const val POOL_LOWER = 26
        const val POOL_UPPER = 26
        const val POOL_DIGIT = 10
        const val POOL_SYMBOL = 33
        const val POOL_SPACE = 1
        const val POOL_NON_LATIN = 32

        // Latin block boundary used to split case-mapped letters from script letters.
        const val LATIN_UPPER_BOUND = 0x0250

        // Band thresholds in effective entropy bits.
        const val THRESHOLD_WEAK = 28
        const val THRESHOLD_STRONG = 50

        const val SEQUENTIAL_RUN_LENGTH = 3
        const val REPEATED_RUN_LENGTH = 3

        const val PENALTY_PER_SEQUENTIAL_RUN = 6.0
        const val PENALTY_PER_REPEATED_RUN = 6.0
        const val PENALTY_COMMON = 45.0
        const val PENALTY_CONTEXT = 12.0

        val COMMON_FRAGMENTS = setOf(
            "password", "passw0rd", "123456", "12345678", "qwerty",
            "iloveyou", "admin", "welcome", "letmein", "abc123",
            "111111", "123123", "asdf", "zxcv", "qwe123",
            "رمزعبور", "رمز", "password1",
        )
        val CONTEXT_FRAGMENTS = setOf("dezh", "pasargad", "دژ", "پاسارگاد")
    }
}
