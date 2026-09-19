package com.pasargad.dezh.security

import com.pasargad.dezh.cryptography.AesGcmCipher
import com.pasargad.dezh.cryptography.KeyWrapper
import com.pasargad.dezh.cryptography.CryptoException
import com.pasargad.dezh.cryptography.Pbkdf2HmacSha256Engine
import com.pasargad.dezh.data.security.FileVaultSecurityRepository
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Security regression tests:
 *  1) No exception produced by the security pipeline ever carries secret material.
 *  2) The security-critical packages contain no logging calls at all (static scan,
 *     skipped gracefully when sources are unavailable in the test runtime).
 */
class NoSensitiveDataTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val password = "SecretPass#777x"

    @Test
    fun `exceptions never carry the password or derived material`() {
        val secureRandom = SecureRandom()
        val cipher = AesGcmCipher(secureRandom)
        val kdf = Pbkdf2HmacSha256Engine()
        val wrapper = KeyWrapper(kdf, cipher, secureRandom)
        val dek = ByteArray(32).also(secureRandom::nextBytes)
        val messages = mutableListOf<String>()

        runCatching { wrapper.unwrap(wrapper.wrap(dek, password.toCharArray(), 2_000), "Wrong-9999".toCharArray()) }
            .onFailure { messages += it.message.orEmpty() + "|" + it.toString() }
        runCatching { cipher.decrypt(ByteArray(32), AesGcmCipher.SealedPayload(ByteArray(12), ByteArray(40))) }
            .onFailure { messages += it.message.orEmpty() + "|" + it.toString() }

        assertTrue(messages.isNotEmpty())
        messages.forEach { message ->
            assertFalse("exception message leaks password: $message", message.contains(password))
            assertFalse(message.contains("SecretPass"))
        }
    }

    @Test
    fun `repository failures never expose secrets in exception messages`() {
        val dir = tmp.newFolder("security")
        val repo = FileVaultSecurityRepository(
            storage = VaultSecurityStorage(dir),
            keyWrapper = KeyWrapper(Pbkdf2HmacSha256Engine(), AesGcmCipher(SecureRandom()), SecureRandom()),
            keystoreGateway = FakeKeystoreGateway(),
            session = VaultSession(AesGcmCipher(SecureRandom())),
            secureRandom = SecureRandom(),
            defaultIterations = 2_000,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val messages = mutableListOf<String>()

        runCatching { runBlocking { repo.unlock(password.toCharArray()) } }
            .onFailure { messages += it.message.orEmpty() + "|" + it.toString() }
        runCatching { runBlocking { repo.setup(password.toCharArray()) } }
            .onSuccess { runCatching { runBlocking { repo.lock() } } }
        runCatching { runBlocking { repo.unlock("Nope-1234abcd".toCharArray()) } }
            .onFailure { messages += it.message.orEmpty() + "|" + it.toString() }
        dir.resolve("keywrap.v1").writeBytes(ByteArray(90) { (it * 7).toByte() })
        runCatching { runBlocking { repo.unlock(password.toCharArray()) } }
            .onFailure { messages += it.message.orEmpty() + "|" + it.toString() }

        assertTrue(messages.size >= 2)
        messages.forEach { message ->
            assertFalse("exception message leaks password: $message", message.contains(password))
            assertFalse(message.contains("Nope-1234"))
        }
    }

    @Test
    fun `security critical sources contain no logging calls`() {
        val roots = listOf(
            "src/main/java/com/pasargad/dezh/cryptography",
            "src/main/java/com/pasargad/dezh/security",
            "src/main/java/com/pasargad/dezh/data/security",
            "src/main/java/com/pasargad/dezh/data/vault",
            "src/main/java/com/pasargad/dezh/presentation/vault",
            "src/main/java/com/pasargad/dezh/domain",
        ).map(::File)

        assumeTrue("source dirs not available in this runtime — scan skipped", roots.all { it.isDirectory })

        val forbidden = Regex("""\bLog\.[deivw]\b|println\(|printStackTrace\(|System\.out\.print""")
        val offenders = roots.asSequence()
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .flatMap { file -> file.readLines().withIndex().filter { forbidden.containsMatchIn(it.value) }.map { "${file.path}:${it.index + 1}" } }
            .toList()

        assertTrue("logging calls found in security-critical sources: $offenders", offenders.isEmpty())
    }
}
