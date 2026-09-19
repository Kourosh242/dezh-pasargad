package com.pasargad.dezh.e2e

import com.pasargad.dezh.backup.BACKUP_FORMAT_ID
import com.pasargad.dezh.backup.BackupCodec
import com.pasargad.dezh.backup.BackupManager
import com.pasargad.dezh.backup.BackupParseException
import com.pasargad.dezh.backup.ParseFailure
import com.pasargad.dezh.backup.RestoreMode
import com.pasargad.dezh.cryptography.AesGcmCipher
import com.pasargad.dezh.cryptography.KeyWrapper
import com.pasargad.dezh.cryptography.Pbkdf2HmacSha256Engine
import com.pasargad.dezh.data.security.FileVaultSecurityRepository
import com.pasargad.dezh.data.vault.FakeVaultEntryDao
import com.pasargad.dezh.data.vault.RoomVaultEntryRepository
import com.pasargad.dezh.domain.VaultEntryDraft
import com.pasargad.dezh.domain.VaultLockState
import com.pasargad.dezh.domain.VaultSecurityException
import com.pasargad.dezh.domain.VaultSortOption
import com.pasargad.dezh.domain.toDraft
import com.pasargad.dezh.generator.PasswordGenerator
import com.pasargad.dezh.security.FakeKeystoreGateway
import com.pasargad.dezh.security.VaultSecurityStorage
import com.pasargad.dezh.security.VaultSession
import com.pasargad.dezh.security.VaultSessionLockedException
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * End-to-end smoke test — the complete user journey in one chained run over
 * the real crypto stack (PBKDF2 + AES-GCM + file storage), exactly mirroring
 * the on-device flow order: startup → setup → wrong password → unlock → CRUD →
 * favorite → category → search/filter/sort → generator constraints → backup →
 * restore(replace) → persistence across relock/restart → corruption rejection →
 * wrong backup password rejection → locked-session fail-close.
 */
class FinalReleaseSmokeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val master = "دژ-پاسارگاد-1404!Final".toCharArray()
    private val backupPass = "پشتیبان-امن-1404#".toCharArray()
    private val testIterations = 4_000

    private lateinit var securityDir: File
    private val deviceKeystore = FakeKeystoreGateway()
    private val persistentDao = FakeVaultEntryDao()
    private var tick = 0L

    // journey state shared across phases (in test order only)
    private lateinit var session: VaultSession
    private lateinit var vault: RoomVaultEntryRepository
    private lateinit var gmailId: String
    private lateinit var backupBytes: ByteArray

    @Before
    fun setUp() {
        securityDir = tmp.newFolder("security")
    }

    /** A fresh process stack; the keystore + dao stand for persistent device state. */
    private fun newSecurityStack(securitySession: VaultSession): FileVaultSecurityRepository {
        val random = SecureRandom()
        val cipher = AesGcmCipher(random)
        return FileVaultSecurityRepository(
            storage = VaultSecurityStorage(securityDir),
            keyWrapper = KeyWrapper(Pbkdf2HmacSha256Engine(), cipher, random),
            keystoreGateway = deviceKeystore,
            session = securitySession,
            secureRandom = random,
            defaultIterations = testIterations,
            ioDispatcher = Dispatchers.Unconfined,
            timeSource = { tick += 1_000; tick },
        )
    }

    private fun newVaultRepository(vaultSession: VaultSession) = RoomVaultEntryRepository(
        dao = persistentDao,
        session = vaultSession,
        json = Json { ignoreUnknownKeys = true },
        ioDispatcher = Dispatchers.Unconfined,
        clock = { tick += 7_000; tick },
    )

    private fun draft(title: String, category: String = "عمومی", favorite: Boolean = false) = VaultEntryDraft(
        title = title, username = "user-$title", email = "$title@mail.example",
        password = "S3cr3t-$title-!", notes = "یادداشت $title", category = category, favorite = favorite,
    )

    @Test
    fun `complete user journey end to end`() {
        phaseSetupAndAuth()
        phaseVaultCrudFavoriteCategory()
        phaseSearchFilterSort()
        phaseGeneratorConstraints()
        phaseBackupExportAndRejections()
        phaseDeleteAndReplaceRestore()
        phaseRestartPersistenceAndLockFailClose()
    }

    private fun phaseSetupAndAuth() {
        session = VaultSession(AesGcmCipher(SecureRandom()))
        val security = newSecurityStack(session)
        runBlocking { security.initialize() }
        assertEquals(VaultLockState.NotSetUp, security.lockState.value)
        runBlocking { security.setup(master) }
        assertEquals(VaultLockState.Unlocked, security.lockState.value)

        runBlocking { security.lock() }
        val wrong = assertThrows(VaultSecurityException.WrongMasterPassword::class.java) {
            runBlocking { security.unlock("رمز-اشتباه".toCharArray()) }
        }
        assertTrue(wrong.nextAttemptDelayMillis > 0)
        tick += 120_000
        runBlocking { security.unlock(master) }
        assertEquals(VaultLockState.Unlocked, security.lockState.value)
        vault = newVaultRepository(session)
    }

    private fun phaseVaultCrudFavoriteCategory() {
        runBlocking {
            vault.createEntry(draft("بانک ملت", category = "بانکداری", favorite = true))
            gmailId = vault.createEntry(draft("جیمیل", category = "ایمیل"))
            vault.createEntry(draft("سرور شرکتی", category = "کار"))
        }
        assertEquals(3, runBlocking { vault.observeEntries().first().size })

        val read = runBlocking { vault.getEntry(gmailId) }
        assertEquals("user-جیمیل", read?.username)
        assertEquals("S3cr3t-جیمیل-!", read?.password)

        runBlocking { vault.updateEntry(gmailId, read!!.toDraft().copy(username = "new-mail", password = "Upd4ted!رمز")) }
        val edited = runBlocking { vault.getEntry(gmailId) }
        assertEquals("new-mail", edited?.username)
        assertEquals("Upd4ted!رمز", edited?.password)

        runBlocking { vault.setFavorite(gmailId, true) }
        val bankOnly = runBlocking { vault.observeEntries(category = "بانکداری").first() }
        assertEquals(1, bankOnly.size)
        assertTrue(bankOnly.single().favorite)
        assertEquals(2, runBlocking { vault.observeEntries(favoritesOnly = true).first().size })
    }

    private fun phaseSearchFilterSort() {
        val hits = runBlocking { vault.observeEntries(query = "یادداشت سرور").first() }
        assertEquals(1, hits.size)
        assertEquals("سرور شرکتی", hits.single().title)
        val byName = runBlocking { vault.observeEntries(sort = VaultSortOption.NAME).first().map { it.title } }
        assertEquals(byName.sortedBy { it.lowercase() }, byName)
        val newestFirst = runBlocking { vault.observeEntries(sort = VaultSortOption.UPDATED_NEWEST).first() }
        assertTrue(newestFirst.first().updatedAt >= newestFirst.last().updatedAt)
    }

    private fun phaseGeneratorConstraints() {
        val generator = PasswordGenerator(SecureRandom())
        val options = PasswordGenerator.Options(
            length = 24, minUppercase = 2, minLowercase = 4, minDigits = 4, minSymbols = 2,
        )
        repeat(20) {
            val pw = generator.generate(options)
            assertEquals(24, pw.length)
            assertTrue(pw.count { it.isUpperCase() } >= 2)
            assertTrue(pw.count { it.isDigit() } >= 4)
            assertTrue(pw.count { !it.isLetterOrDigit() } >= 2)
        }
        assertNotEquals(generator.generate(PasswordGenerator.Options()), generator.generate(PasswordGenerator.Options()))
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.Options(length = 8, minDigits = 9) }
    }

    private fun phaseBackupExportAndRejections() {
        val backupManager = BackupManager(BackupCodec(SecureRandom(), kdfIterations = testIterations), vault)
        backupBytes = runBlocking { backupManager.export(backupPass) }
        val backupText = backupBytes.decodeToString()
        assertTrue(backupText.contains(BACKUP_FORMAT_ID))
        assertTrue(!backupText.contains("S3cr3t-"))
        assertTrue(!backupText.contains("Upd4ted"))

        val wrongPw = assertThrows(BackupParseException::class.java) {
            backupManager.decrypt(backupBytes, "عبور-غلط".toCharArray())
        }
        assertEquals(ParseFailure.WRONG_PASSWORD, wrongPw.reason)

        // Flip one character INSIDE the base64 ciphertext: the envelope JSON stays
        // parseable, so the failing gate is the ciphertext checksum (integrity).
        val cipherKey = "\"ciphertext\""
        val keyIndex = backupText.indexOf(cipherKey)
        assertTrue(keyIndex > 0)
        val valueStart = backupText.indexOf('"', keyIndex + cipherKey.length) + 1
        val flipped = if (backupText[valueStart] != 'A') 'A' else 'B'
        val tampered = backupText.substring(0, valueStart) + flipped + backupText.substring(valueStart + 1)
        val corruptFailure = runCatching { backupManager.readHeader(tampered.encodeToByteArray()) }.exceptionOrNull()
        assertTrue(corruptFailure is BackupParseException)
        assertEquals(ParseFailure.INTEGRITY_FAILURE, (corruptFailure as BackupParseException).reason)
    }

    private fun phaseDeleteAndReplaceRestore() {
        val backupManager = BackupManager(BackupCodec(SecureRandom(), kdfIterations = testIterations), vault)
        val serverEntry = runBlocking { vault.observeEntries().first().single { it.title == "سرور شرکتی" } }
        runBlocking { vault.deleteEntry(serverEntry.id) }
        assertEquals(2, runBlocking { vault.observeEntries().first().size })

        val payload = backupManager.decrypt(backupBytes, backupPass)
        val summary = runBlocking { backupManager.restore(payload, RestoreMode.REPLACE) }
        assertEquals(2, summary.deletedExisting)
        assertEquals(3, summary.inserted)
        val restored = runBlocking { vault.observeEntries().first() }
        assertEquals(3, restored.size)
        assertEquals("Upd4ted!رمز", restored.single { it.id == gmailId }.password)
    }

    private fun phaseRestartPersistenceAndLockFailClose() {
        runBlocking {
            session.lock()
            assertEquals(VaultLockState.Locked, session.lockState.value)
        }

        // Real restart: a fresh, LOCKED session over the same persistent files.
        val restartedSession = VaultSession(AesGcmCipher(SecureRandom()))
        val restartedSecurity = newSecurityStack(restartedSession)
        runBlocking {
            restartedSecurity.initialize()
            assertEquals(VaultLockState.Locked, restartedSecurity.lockState.value)
            restartedSecurity.unlock(master)
        }
        val restartedVault = newVaultRepository(restartedSession)
        val afterRestart = runBlocking { restartedVault.observeEntries().first() }
        assertEquals(3, afterRestart.size)
        assertEquals("Upd4ted!رمز", afterRestart.single { it.id == gmailId }.password)
        assertTrue(afterRestart.single { it.title == "بانک ملت" }.favorite)

        runBlocking { restartedSecurity.lock() }
        assertThrows(VaultSessionLockedException::class.java) {
            runBlocking { restartedVault.observeEntries().first() }
        }
    }

}
