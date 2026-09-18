package com.pasargad.dezh.generator

import java.security.SecureRandom

/**
 * Cryptographically secure password generator ([SecureRandom]).
 * Each enabled class contributes at least its [Options] minimum; the remainder
 * is drawn from the merged pool; result is Fisher–Yates shuffled.
 */
class PasswordGenerator(private val secureRandom: SecureRandom = SecureRandom()) {

    data class Options(
        val length: Int = DEFAULT_LENGTH,
        val includeUppercase: Boolean = true,
        val includeLowercase: Boolean = true,
        val includeDigits: Boolean = true,
        val includeSymbols: Boolean = true,
        val excludeAmbiguous: Boolean = false,
        val minUppercase: Int = 1,
        val minLowercase: Int = 1,
        val minDigits: Int = 1,
        val minSymbols: Int = 1,
    ) {
        init {
            require(length in MIN_LENGTH..MAX_LENGTH) { "Length is out of the allowed range" }
            require(includeUppercase || includeLowercase || includeDigits || includeSymbols) {
                "At least one character class must be enabled"
            }

            val required = buildList {
                if (includeUppercase) add(minUppercase)
                if (includeLowercase) add(minLowercase)
                if (includeDigits) add(minDigits)
                if (includeSymbols) add(minSymbols)
            }
            require(required.all { it >= 0 }) { "Minimum counts must not be negative" }
            require(required.sum() <= length) { "Minimum requirements exceed the requested length" }
        }

        companion object {
            const val MIN_LENGTH = 8
            const val MAX_LENGTH = 64
            const val DEFAULT_LENGTH = 16
        }
    }

    fun generate(options: Options): String {
        val classes = buildList {
            if (options.includeLowercase) add(Requirement(filterPool(LOWERCASE, options.excludeAmbiguous), options.minLowercase))
            if (options.includeUppercase) add(Requirement(filterPool(UPPERCASE, options.excludeAmbiguous), options.minUppercase))
            if (options.includeDigits) add(Requirement(filterPool(DIGITS, options.excludeAmbiguous), options.minDigits))
            if (options.includeSymbols) add(Requirement(filterPool(SYMBOLS, options.excludeAmbiguous), options.minSymbols))
        }.filter { it.pool.isNotEmpty() }

        require(classes.isNotEmpty()) { "All character pools are empty" }

        val chars = CharArray(options.length)
        var index = 0
        classes.forEach { requirement ->
            repeat(requirement.min.coerceAtMost(options.length - index)) {
                chars[index++] = requirement.pool.randomChar()
            }
        }
        val merged = classes.flatMap { it.pool.toList() }
        while (index < options.length) {
            chars[index++] = merged[secureRandom.nextInt(merged.size)]
        }
        for (i in chars.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars)
    }

    private fun filterPool(pool: String, excludeAmbiguous: Boolean): String =
        if (excludeAmbiguous) pool.filterNot { it in AMBIGUOUS } else pool

    private fun String.randomChar(): Char = this[secureRandom.nextInt(this.length)]

    private data class Requirement(val pool: String, val min: Int)

    private companion object {
        const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val DIGITS = "0123456789"
        const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"
        const val AMBIGUOUS = "O0oIl1"
    }
}
