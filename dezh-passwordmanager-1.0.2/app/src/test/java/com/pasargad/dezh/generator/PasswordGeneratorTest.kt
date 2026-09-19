package com.pasargad.dezh.generator

import java.security.SecureRandom
import java.security.SecureRandomSpi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    private fun seeded(seed: Long = 42L): SecureRandom =
        SecureRandom.getInstance("SHA1PRNG").apply { setSeed(seed) }

    @Test
    fun `generated length matches the requested option`() {
        val generator = PasswordGenerator(seeded())
        listOf(8, 16, 64).forEach { length ->
            assertEquals(length, generator.generate(PasswordGenerator.Options(length)).length)
        }
    }

    @Test
    fun `minimum requirements of every enabled class are honored`() {
        val generator = PasswordGenerator(seeded())
        repeat(30) {
            val password = generator.generate(
                PasswordGenerator.Options(
                    length = 24,
                    minUppercase = 2,
                    minLowercase = 3,
                    minDigits = 4,
                    minSymbols = 2,
                ),
            )
            assertTrue(password.count { it.isUpperCase() } >= 2)
            assertTrue(password.count { it.isLowerCase() } >= 3)
            assertTrue(password.count { it.isDigit() } >= 4)
            assertTrue(password.count { !it.isLetterOrDigit() } >= 2)
        }
    }

    @Test
    fun `minimums exceeding length are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PasswordGenerator.Options(length = 8, minDigits = 5, minSymbols = 5, minLowercase = 1)
        }
    }

    @Test
    fun `ambiguous characters are excluded when requested`() {
        val generator = PasswordGenerator(seeded())
        val ambiguous = "O0oIl1"
        repeat(50) {
            val password = generator.generate(PasswordGenerator.Options(length = 32, excludeAmbiguous = true))
            assertTrue(password.none { it in ambiguous })
        }
    }

    @Test
    fun `disabling every class is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PasswordGenerator.Options(
                length = 16,
                includeUppercase = false,
                includeLowercase = false,
                includeDigits = false,
                includeSymbols = false,
            )
        }
    }

    @Test
    fun `length outside the allowed bounds is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.Options(length = 7) }
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.Options(length = 65) }
    }

    @Test
    fun `generation is randomized`() {
        val generator = PasswordGenerator(SecureRandom())
        assertNotEquals(
            generator.generate(PasswordGenerator.Options()),
            generator.generate(PasswordGenerator.Options()),
        )
    }

    @Test
    fun `same seed produces the same output - deterministic for tests`() {
        // Provider-independent deterministic SecureRandom: Robolectric tests running earlier
        // in the same JVM mutate the platform security state, so SHA1PRNG is not stable here.
        val g1 = PasswordGenerator(DeterministicRandom(7L))
        val g2 = PasswordGenerator(DeterministicRandom(7L))
        assertEquals(g1.generate(PasswordGenerator.Options()), g2.generate(PasswordGenerator.Options()))
    }
}

/** Tiny LCG-backed SecureRandom for reproducible generator tests (never used in production). */
private class DeterministicRandom(seed: Long) : SecureRandom(
    object : SecureRandomSpi() {
        private var state = seed
        override fun engineSetSeed(seed: ByteArray) {
            state = seed.foldIndexed(seedFoldSeed) { index, acc, byte -> acc + (byte.toLong() and 0xff) * (index + 1) }
        }
        override fun engineNextBytes(bytes: ByteArray) {
            for (index in bytes.indices) {
                state = state * LCG_MULTIPLIER + LCG_INCREMENT
                bytes[index] = (state ushr 33).toByte()
            }
        }
        override fun engineGenerateSeed(numBytes: Int): ByteArray = ByteArray(numBytes)
    },
    null,
) {
    private companion object {
        const val LCG_MULTIPLIER = 6364136223846793005L
        const val LCG_INCREMENT = 1442695040888963407L
        const val seedFoldSeed = 7L
    }
}
