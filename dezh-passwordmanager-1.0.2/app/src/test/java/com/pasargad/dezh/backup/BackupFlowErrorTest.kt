package com.pasargad.dezh.backup

import com.pasargad.dezh.data.backup.BackupFileGateway
import com.pasargad.dezh.data.backup.FileFailure
import com.pasargad.dezh.data.backup.SafBackupFileGateway
import com.pasargad.dezh.domain.PasswordStrengthMeter
import com.pasargad.dezh.presentation.backup.BackupUiError
import com.pasargad.dezh.presentation.backup.BackupViewModel
import com.pasargad.dezh.vault.StubSettingsRepository
import com.pasargad.dezh.vault.StubVaultEntryRepository
import java.io.IOException
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Export-side failure handling at the ViewModel boundary: canceled picker,
 * insufficient storage, generic I/O, corrupted-output verification and the
 * happy path — plus the pure IOException classification table.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupFlowErrorTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Gateway double: records writes; read-back is configurable (verification). */
    private class FakeGateway(
        private val writeError: Exception? = null,
        private val tamperReadBack: Boolean = false,
    ) : BackupFileGateway {

        var lastWritten: ByteArray? = null

        override suspend fun write(uri: android.net.Uri, bytes: ByteArray) {
            writeError?.let { throw it }
            lastWritten = bytes
        }

        override suspend fun read(uri: android.net.Uri): ByteArray {
            val bytes = lastWritten ?: error("nothing written yet")
            if (!tamperReadBack) return bytes
            // Simulate a torn write: truncated + first byte flipped.
            return bytes.copyOf(bytes.size - 1).also { truncated -> truncated[0] = truncated[0].inc() }
        }

        override suspend fun displayName(uri: android.net.Uri): String? = null
    }

    private fun newViewModel(gateway: BackupFileGateway): BackupViewModel = BackupViewModel(
        backupManager = BackupManager(
            codec = BackupCodec(SecureRandom(), kdfIterations = 2_000),
            repository = StubVaultEntryRepository(),
            clock = { 1_760_000_000_000L },
        ),
        fileGateway = gateway,
        settingsRepository = StubSettingsRepository(),
        strengthMeter = PasswordStrengthMeter(),
    )

    private fun preparedViewModel(gateway: BackupFileGateway): BackupViewModel =
        newViewModel(gateway).apply {
            onPassphraseChanged("کلمهٔ عبور ۱۴۰۴!")
            onConfirmPassphraseChanged("کلمهٔ عبور ۱۴۰۴!")
        }

    private fun pick(): android.net.Uri = android.net.Uri.parse("content://docs/dezh-backup")

    @Test
    fun `canceled location picker surfaces CANCELED`() {
        val viewModel = preparedViewModel(FakeGateway())
        viewModel.onExportLocationPicked(null)
        assertEquals(BackupUiError.CANCELED, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.successCount)
    }

    @Test
    fun `insufficient storage on write is reported distinctly`() {
        val gateway = FakeGateway(
            writeError = SafBackupFileGateway.classifyIoException(
                IOException("write failed: ENOSPC (No space left on device)"),
            ),
        )
        val viewModel = preparedViewModel(gateway)
        viewModel.onExportLocationPicked(pick())
        assertEquals(BackupUiError.INSUFFICIENT_STORAGE, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.successCount)
    }

    @Test
    fun `generic io failure on write is reported as IO_ERROR`() {
        val gateway = FakeGateway(
            writeError = SafBackupFileGateway.classifyIoException(IOException("stream closed")),
        )
        val viewModel = preparedViewModel(gateway)
        viewModel.onExportLocationPicked(pick())
        assertEquals(BackupUiError.IO_ERROR, viewModel.uiState.value.error)
    }

    @Test
    fun `corrupted output - torn write fails verification before success is claimed`() {
        val viewModel = preparedViewModel(FakeGateway(tamperReadBack = true))
        viewModel.onExportLocationPicked(pick())
        assertEquals(BackupUiError.CORRUPTED_OUTPUT, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.successCount)
    }

    @Test
    fun `happy path verifies the written file end-to-end`() {
        val gateway = FakeGateway()
        val viewModel = preparedViewModel(gateway)
        viewModel.onExportLocationPicked(pick())
        assertNull(viewModel.uiState.value.error)
        assertEquals(0, viewModel.uiState.value.successCount) // stub vault is empty
        assertTrue(gateway.lastWritten!!.decodeToString().contains(BACKUP_FORMAT_ID))
    }

    @Test
    fun `io classification - no space vs generic`() {
        assertEquals(
            FileFailure.INSUFFICIENT_STORAGE,
            SafBackupFileGateway.classifyIoException(IOException("No space left on device")).failure,
        )
        assertEquals(
            FileFailure.IO_ERROR,
            SafBackupFileGateway.classifyIoException(IOException("EBADF: bad file descriptor")).failure,
        )
        // Suppressed causes are inspected too (some stacks hide ENOSPC there).
        val wrapped = IOException("write error").apply {
            addSuppressed(IOException("No space left on device"))
        }
        assertEquals(
            FileFailure.INSUFFICIENT_STORAGE,
            SafBackupFileGateway.classifyIoException(wrapped).failure,
        )
    }
}
