package com.pasargad.dezh.data.security

import com.pasargad.dezh.cryptography.AesGcmCipher
import com.pasargad.dezh.cryptography.KeyWrapper
import com.pasargad.dezh.cryptography.Pbkdf2HmacSha256Engine
import com.pasargad.dezh.domain.VaultLockState
import com.pasargad.dezh.domain.VaultSecurityException
import com.pasargad.dezh.security.FakeKeystoreGateway
import com.pasargad.dezh.security.VaultSecurityStorage
import com.pasargad.dezh.security.VaultSession
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Full authentication lifecycle over real file storage — the requested
 * first-setup / correct / incorrect / relock / restart / process-recreation
 * chain. "Restart" and "process recreation" are modeled by rebuilding every
 * component (session, wrapper, repository) against the SAME storage directory,
 * exactly what happens on cold starts; only the files persist.
 */
class AuthenticationLifecycleTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val password = "Pasargad-1404#قفل".toCharArray()
    private val testIterations = 2_000

    private lateinit var securityDir: File

    @Before
    fun setUp() {
        securityDir = tmp.newFolder("security")
    }

    private val deviceKeystore = FakeKeystoreGateway()

    /**
     * Builds a completely fresh stack over the same directory (a cold process).
     * The [deviceKeystore] instance is intentionally SHARED: the real Android
     * Keystore persists across restarts, so must this fake.
     */
    private fun newColdProcess(timeSource: () -> Long): FileVaultSecurityRepository {
        val random = SecureRandom()
        val cipher = AesGcmCipher(random)
        return FileVaultSecurityRepository(
            storage = VaultSecurityStorage(securityDir),
            keyWrapper = KeyWrapper(Pbkdf2HmacSha256Engine(), cipher, random),
            keystoreGateway = deviceKeystore,
            session = VaultSession(cipher),
            secureRandom = random,
            defaultIterations = testIterations,
            ioDispatcher = Dispatchers.Unconfined,
            timeSource = timeSource,
        )
    }

    @Test
    fun `full lifecycle - setup, wrong, correct, relock, restart, process recreation`() {
        var now = 1_000_000L
        val firstProcess = newColdProcess({ now })

        // ── first setup ────────────────────────────────────────────────
        runBlocking { firstProcess.initialize() }
        assertEquals(VaultLockState.NotSetUp, firstProcess.lockState.value)
        runBlocking { firstProcess.setup(password) }
        assertEquals(VaultLockState.Unlocked, firstProcess.lockState.value)

        // ── relock ─────────────────────────────────────────────────────
        runBlocking { firstProcess.lock() }
        assertEquals(VaultLockState.Locked, firstProcess.lockState.value)

        // ── incorrect password: rejected + backoff recorded ────────────
        val wrong = assertThrows(VaultSecurityException.WrongMasterPassword::class.java) {
            runBlocking { firstProcess.unlock("wrong-pass-۱۲۳".toCharArray()) }
        }
        org.junit.Assert.assertTrue(wrong.nextAttemptDelayMillis > 0)

        // ── RESTART right after the failed attempt (new process, same files) ──
        val restartedProcess = newColdProcess({ now })
        runBlocking { restartedProcess.initialize() }
        // Existing vault → Locked, never NotSetUp, never auto-unlocked.
        assertEquals(VaultLockState.Locked, restartedProcess.lockState.value)

        // The failed attempt from the PREVIOUS process still counts — the
        // brute-force counter is persisted in the security files.
        assertThrows(VaultSecurityException.BackoffRequired::class.java) {
            runBlocking { restartedProcess.unlock(password) }
        }

        // ── correct password after the backoff window ──────────────────
        now += 120_000
        runBlocking { restartedProcess.unlock(password) }
        assertEquals(VaultLockState.Unlocked, restartedProcess.lockState.value)

        // ── PROCESS RECREATION: a fresh process over the same files always
        // starts LOCKED again — the in-memory DEK/session is never restorable ──
        runBlocking { restartedProcess.lock() }
        val recreatedProcess = newColdProcess({ now })
        runBlocking { recreatedProcess.initialize() }
        assertEquals(VaultLockState.Locked, recreatedProcess.lockState.value)
        now += 120_000
        runBlocking { recreatedProcess.unlock(password) }
        assertEquals(VaultLockState.Unlocked, recreatedProcess.lockState.value)
    }

    @Test
    fun `restart with a corrupted storage reports corruption instead of unlocking`() {
        var now = 1_000_000L
        val first = newColdProcess({ now })
        runBlocking {
            first.initialize()
            first.setup(password)
            first.lock()
        }
        // Corrupt the persisted files, boot a "new process" and try to open it.
        securityDir.listFiles()?.forEach { file -> file.writeBytes(ByteArray((file.length() + 8).toInt())) }
        val next = newColdProcess({ now })
        runBlocking { next.initialize() } // lock resolution itself stays fail-closed-lenient
        val failure = runCatching { runBlocking { next.unlock(password) } }.exceptionOrNull()
        org.junit.Assert.assertTrue(
            "expected VaultSecurityException, got $failure",
            failure is VaultSecurityException,
        )
    }
}
