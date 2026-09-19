package com.pasargad.dezh.di

import android.content.Context
import androidx.room.Room
import com.pasargad.dezh.cryptography.AesGcmCipher
import com.pasargad.dezh.cryptography.KeyWrapper
import com.pasargad.dezh.cryptography.Pbkdf2HmacSha256Engine
import com.pasargad.dezh.cryptography.PasswordKdfEngine
import com.pasargad.dezh.data.security.FileVaultSecurityRepository
import com.pasargad.dezh.data.update.GithubReleaseFetcher
import com.pasargad.dezh.data.update.HttpApkDownloader
import com.pasargad.dezh.data.update.PackageApkInstaller
import com.pasargad.dezh.data.update.PackageApkVerifier
import com.pasargad.dezh.data.update.PackageAppVersionProvider
import com.pasargad.dezh.data.vault.DatabaseMigrations
import com.pasargad.dezh.data.vault.DezhVaultDatabase
import com.pasargad.dezh.data.vault.RoomVaultEntryRepository
import com.pasargad.dezh.domain.CreateEntryUseCase
import com.pasargad.dezh.domain.DeleteEntryUseCase
import com.pasargad.dezh.domain.GetEntryUseCase
import com.pasargad.dezh.domain.LockVaultUseCase
import com.pasargad.dezh.domain.ObserveCategoriesUseCase
import com.pasargad.dezh.domain.ObserveEntriesUseCase
import com.pasargad.dezh.domain.ObserveEntryUseCase
import com.pasargad.dezh.domain.PasswordStrengthValidator
import com.pasargad.dezh.domain.SetupMasterPasswordUseCase
import com.pasargad.dezh.domain.ToggleFavoriteUseCase
import com.pasargad.dezh.domain.UnlockVaultUseCase
import com.pasargad.dezh.domain.UpdateEntryUseCase
import com.pasargad.dezh.domain.update.UpdateChecker
import com.pasargad.dezh.domain.VaultEntryRepository
import com.pasargad.dezh.backup.BackupCodec
import com.pasargad.dezh.backup.BackupManager
import com.pasargad.dezh.data.backup.BackupFileGateway
import com.pasargad.dezh.data.backup.SafBackupFileGateway
import com.pasargad.dezh.domain.PasswordStrengthMeter
import com.pasargad.dezh.domain.VaultSecurityRepository
import com.pasargad.dezh.generator.PasswordGenerator
import com.pasargad.dezh.settings.DataStoreSettingsRepository
import com.pasargad.dezh.settings.SettingsRepository
import com.pasargad.dezh.settings.ThemeModeController
import com.pasargad.dezh.security.AndroidKeystoreGateway
import com.pasargad.dezh.security.AutoLockController
import com.pasargad.dezh.security.KeystoreGateway
import com.pasargad.dezh.security.VaultSecurityStorage
import com.pasargad.dezh.security.VaultSession
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json

/**
 * Manual dependency container (single module, small graph — Hilt stays deferred
 * until a real need is justified, per project rules).
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // --- cryptography primitives -------------------------------------------------
    private val secureRandom = SecureRandom()
    private val aesGcmCipher = AesGcmCipher(secureRandom)
    private val kdfEngine: PasswordKdfEngine = Pbkdf2HmacSha256Engine()
    private val keyWrapper = KeyWrapper(kdfEngine, aesGcmCipher, secureRandom)
    private val json = Json { ignoreUnknownKeys = true }

    // --- security components ------------------------------------------------------
    val session = VaultSession(aesGcmCipher)
    private val keystoreGateway: KeystoreGateway =
        AndroidKeystoreGateway(keyAlias = KEY_ALIAS_SECURITY_META)
    private val storage = VaultSecurityStorage(File(appContext.filesDir, SECURITY_DIR))

    val securityRepository: VaultSecurityRepository = FileVaultSecurityRepository(
        storage = storage,
        keyWrapper = keyWrapper,
        keystoreGateway = keystoreGateway,
        session = session,
        secureRandom = secureRandom,
    )

    // --- vault database & repository (Phase 3) --------------------------------------
    @Suppress("SpreadOperator") // Room's API is varargs; the migration history is a small fixed array.
    val vaultDatabase: DezhVaultDatabase = Room.databaseBuilder(
        appContext,
        DezhVaultDatabase::class.java,
        DezhVaultDatabase.DATABASE_NAME,
    )
        .addMigrations(*DatabaseMigrations.ALL)
        .build()

    val vaultEntryRepository: VaultEntryRepository = RoomVaultEntryRepository(
        dao = vaultDatabase.vaultEntryDao(),
        session = session,
        json = json,
    )

    // --- security use cases ---------------------------------------------------------
    val setupMasterPassword = SetupMasterPasswordUseCase(securityRepository)
    val unlockVault = UnlockVaultUseCase(securityRepository)
    val lockVault = LockVaultUseCase(securityRepository)
    val passwordStrengthValidator = PasswordStrengthValidator()

    // --- vault use cases (Phase 3) ----------------------------------------------------
    val observeEntries = ObserveEntriesUseCase(vaultEntryRepository)
    val observeCategories = ObserveCategoriesUseCase(vaultEntryRepository)
    val observeEntry = ObserveEntryUseCase(vaultEntryRepository)
    val getVaultEntry = GetEntryUseCase(vaultEntryRepository)
    val createVaultEntry = CreateEntryUseCase(vaultEntryRepository)
    val updateVaultEntry = UpdateEntryUseCase(vaultEntryRepository)
    val deleteVaultEntry = DeleteEntryUseCase(vaultEntryRepository)
    val toggleVaultFavorite = ToggleFavoriteUseCase(vaultEntryRepository)

    // --- settings (Phase 5: DataStore, replaces SharedPreferences) ------------------
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(appContext)
    val autoLockController = AutoLockController(applicationScope, session)
    val themeModeController = ThemeModeController(settingsRepository, applicationScope)

    init {
        // Keep the auto-lock policy in sync with the user's setting at all times.
        applicationScope.launch {
            settingsRepository.settings
                .map { it.autoLockTimeoutMinutes }
                .distinctUntilChanged()
                .collect { autoLockController.updateTimeoutMinutes(it) }
        }
    }

    // --- backup / restore (Phase 5) --------------------------------------------------
    val backupFileGateway: BackupFileGateway = SafBackupFileGateway(appContext.contentResolver)
    val backupManager = BackupManager(BackupCodec(secureRandom), vaultEntryRepository)

    val passwordGenerator = PasswordGenerator(secureRandom)
    val passwordStrengthMeter = PasswordStrengthMeter()

    // --- update check + in-app self-update (official PackageInstaller) ---------------
    private val releaseFetcher = GithubReleaseFetcher()
    val apkDownloader = HttpApkDownloader(appContext)
    val apkVerifier = PackageApkVerifier(appContext)
    val apkInstaller = PackageApkInstaller(appContext)
    val updateChecker: UpdateChecker = UpdateChecker(
        versionProvider = PackageAppVersionProvider(appContext),
        releaseFetcher = releaseFetcher,
        assetFetcher = releaseFetcher,
    )

    private companion object {
        const val SECURITY_DIR = "dezh/security"
        const val KEY_ALIAS_SECURITY_META = "dezh_security_meta_v1"
    }
}
