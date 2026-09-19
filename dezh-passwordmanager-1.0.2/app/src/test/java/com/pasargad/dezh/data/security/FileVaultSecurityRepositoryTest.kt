package com.pasargad.dezh.data.security

import com.pasargad.dezh.cryptography.AesGcmCipher
import com.pasargad.dezh.cryptography.CryptoConstants
import com.pasargad.dezh.cryptography.KeyWrapContainer
import com.pasargad.dezh.cryptography.KeyWrapper
import com.pasargad.dezh.cryptography.Pbkdf2HmacSha256Engine
import com.pasargad.dezh.cryptography.ByteCodec
import com.pasargad.dezh.domain.VaultLockState
import com.pasargad.dezh.domain.VaultSecurityException
import com.pasargad.dezh.security.FakeKeystoreGateway
import com.pasargad.dezh.security.VaultSecurityStorage
import com.pasargad.dezh.security.VaultSession
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileVaultSecurityRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var securityDir: File
    private lateinit var storage: VaultSecurityStorage

    private val testIterations = 2_000
    private val password = "SabzBagh-1403!x"
    private val masterPassword = password.toCharArray()

    @Before
    fun setUp() {
        securityDir = tmp.newFolder("security")
        storage = VaultSecurityStorage(securityDir)
    }

    private fun newRepository(
        secureRandom: SecureRandom = SecureRandom(),
        timeSource: () -> Long = System::currentTimeMillis,
        keystoreGateway: FakeKeystoreGateway = FakeKeystoreGateway(),
    ): FileVaultSecurityRepository {
        val sr = SecureRandom()
        val cipher = AesGcmCipher(sr)
        val kdfEngine = Pbkdf2HmacSha256Engine()
        val session = VaultSession(cipher)
        val wrapper = KeyWrapper(kdfEngine, cipher, sr)
        return FileVaultSecurityRepository(
            storage = storage,
            keyWrapper = wrapper,
            keystoreGateway = keystoreGateway,
            session = session,
            secureRandom = secureRandom,
            defaultIterations = testIterations,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            timeSource = timeSource,
        )
    }

    @Test
    fun `setup creates files unlocks the session and lock returns to locked`() = runBlocking {
        val repo = newRepository()
        repo.setup(masterPassword)

        assertEquals(VaultLockState.Unlocked, repo.lockState.value)
        assertTrue(storage.hasKeyWrap())
        assertTrue(storage.readMeta() != null)

        repo.lock()
        assertEquals(VaultLockState.Locked, repo.lockState.value)
    }

    @Test
    fun `unlock with the correct password succeeds`() = runBlocking {
        val repo = newRepository()
        repo.setup(masterPassword)
        repo.lock()

        repo.unlock(password.toCharArray())
        assertEquals(VaultLockState.Unlocked, repo.lockState.value)
    }

    @Test
    fun `wrong password throws and enforces backoff then correct password succeeds`() = runBlocking {
        var now = 1_000_000L
        val repo = newRepository(timeSource = { now })
        repo.setup(masterPassword)
        repo.lock()

        val wrong = assertThrows(VaultSecurityException.WrongMasterPassword::class.java) {
            runBlocking { repo.unlock("Totally-Wrong-99".toCharArray()) }
        }
        assertEquals(1_000L, wrong.nextAttemptDelayMillis)

        // Correct password right after a failure is throttled (persisted counter).
        assertThrows(VaultSecurityException.BackoffRequired::class.java) {
            runBlocking { repo.unlock(masterPassword) }
        }

        now += 60_000
        repo.unlock(masterPassword)
        assertEquals(VaultLockState.Unlocked, repo.lockState.value)
    }

    @Test
    fun `backoff grows with consecutive failures`() = runBlocking {
        var now = 1_000_000L
        val repo = newRepository(timeSource = { now })
        repo.setup(masterPassword)
        repo.lock()

        var delay = 0L
        repeat(4) { attempt ->
            now += 60_000 // clear previous backoff between attempts
            val ex = assertThrows(VaultSecurityException.WrongMasterPassword::class.java) {
                runBlocking { repo.unlock(("Wrong-Pass-000" + attempt).toCharArray()) }
            }
            delay = ex.nextAttemptDelayMillis
        }
        assertEquals(8_000L, delay)
    }

    @Test
    fun `corrupted keywrap file is reported as data corruption`(): Unit = runBlocking {
        val repo = newRepository()
        repo.setup(masterPassword)
        repo.lock()

        securityDir.resolve("keywrap.v1").writeBytes(ByteArray(64) { it.toByte() })
        assertThrows(VaultSecurityException.VaultDataCorrupted::class.java) {
            runBlocking { repo.unlock(masterPassword) }
        }
    }

    @Test
    fun `tampered wrapped key fails authentication`(): Unit = runBlocking {
        val repo = newRepository()
        repo.setup(masterPassword)
        repo.lock()

        val bytes = securityDir.resolve("keywrap.v1").readBytes()
        val container = KeyWrapContainer.decode(bytes)
        val tampered = container.copy(
            payload = container.payload.copyOf().also {
                it[0] = (it[0].toInt() xor 0x10).toByte()
            },
        ).encode()
        securityDir.resolve("keywrap.v1").writeBytes(tampered)

        assertThrows(VaultSecurityException.WrongMasterPassword::class.java) {
            runBlocking { repo.unlock(masterPassword) }
        }
    }

    @Test
    fun `kdf parameter tampering between wrap and meta is detected`(): Unit = runBlocking {
        val repo = newRepository()
        repo.setup(masterPassword)
        repo.lock()

        val container = KeyWrapContainer.decode(securityDir.resolve("keywrap.v1").readBytes())
        val downgraded = container.copy(iterations = 1_500).encode()
        securityDir.resolve("keywrap.v1").writeBytes(downgraded)

        assertThrows(VaultSecurityException.VaultDataCorrupted::class.java) {
            runBlocking { repo.unlock(masterPassword) }
        }
    }

    @Test
    fun `tampered meta file is reported as data corruption`(): Unit = runBlocking {
        val repo = newRepository()
        repo.setup(masterPassword)
        repo.lock()

        val meta = securityDir.resolve("meta.v1")
        val bytes = meta.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x40).toByte()
        meta.writeBytes(bytes)

        assertThrows(VaultSecurityException.VaultDataCorrupted::class.java) {
            runBlocking { repo.unlock(masterPassword) }
        }
    }

    @Test
    fun `secrets are never persisted to storage files`() = runBlocking {
        val dekSeed = "dezh-expected-dek-seed".toByteArray()
        val expectedDek = ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES)
        SecureRandom.getInstance("SHA1PRNG").apply { setSeed(dekSeed) }.nextBytes(expectedDek)
        val repoRandom = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(dekSeed) }

        val repo = newRepository(secureRandom = repoRandom)
        repo.setup(masterPassword)
        repo.lock()

        assertTrue(storage.hasKeyWrap())
        val blob = securityDir.walkTopDown()
            .filter { it.isFile }
            .fold(ByteArray(0)) { acc, file -> acc + file.readBytes() }

        assertFalse("master password leaked to storage", containsSubarray(blob, password.encodeToByteArray()))
        assertFalse("DEK leaked to storage", containsSubarray(blob, expectedDek))
    }

    @Test
    fun `initialize resolves notSetUp on fresh storage and locked after setup`() = runBlocking {
        // AndroidKeyStore persists across process restarts -> the simulated
        // restart (second repository) must share the same Keystore gateway.
        val keystore = FakeKeystoreGateway()
        val first = newRepository(keystoreGateway = keystore)
        first.initialize()
        assertEquals(VaultLockState.NotSetUp, first.lockState.value)

        first.setup(masterPassword)
        first.lock()

        val second = newRepository(keystoreGateway = keystore)
        second.initialize()
        assertEquals(VaultLockState.Locked, second.lockState.value)

        second.unlock(masterPassword)
        assertEquals(VaultLockState.Unlocked, second.lockState.value)
    }

    @Test
    fun `setup on an existing vault is rejected`(): Unit = runBlocking {
        val repo = newRepository()
        repo.setup(masterPassword)
        assertThrows(VaultSecurityException.VaultAlreadySetUp::class.java) {
            runBlocking { repo.setup("Another-Pass-77".toCharArray()) }
        }
    }

    @Test
    fun `keywrap payload never contains the plaintext dek`() = runBlocking {
        val dekSeed = "wrap-payload-seed".toByteArray()
        val expectedDek = ByteArray(CryptoConstants.AES_KEY_SIZE_BYTES)
        SecureRandom.getInstance("SHA1PRNG").apply { setSeed(dekSeed) }.nextBytes(expectedDek)
        val repo = newRepository(secureRandom = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(dekSeed) })
        repo.setup(masterPassword)

        val wrap = KeyWrapContainer.decode(securityDir.resolve("keywrap.v1").readBytes())
        assertEquals(testIterations, wrap.iterations)
        assertFalse(containsSubarray(wrap.payload, expectedDek))
        assertFalse(containsSubarray(wrap.salt, expectedDek))
    }

    private fun containsSubarray(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        return (0..haystack.size - needle.size).any { offset ->
            needle.indices.all { j -> haystack[offset + j] == needle[j] }
        }
    }
}
