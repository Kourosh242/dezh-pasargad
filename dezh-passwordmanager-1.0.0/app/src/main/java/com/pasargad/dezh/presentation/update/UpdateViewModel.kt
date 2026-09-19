package com.pasargad.dezh.presentation.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasargad.dezh.data.update.ApkDownloadException
import com.pasargad.dezh.data.update.ApkDownloadGateway
import com.pasargad.dezh.data.update.ApkInstallGateway
import com.pasargad.dezh.data.update.ApkVerificationGateway
import com.pasargad.dezh.data.update.InstallOutcome
import com.pasargad.dezh.domain.update.ReleaseApkAsset
import com.pasargad.dezh.domain.update.UpdateChecker
import com.pasargad.dezh.domain.update.UpdateCheckResult
import com.pasargad.dezh.domain.update.Verdict
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lifecycle of one update-check round trip. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(
        val latestVersion: String,
        val releaseUrl: String,
        val asset: ReleaseApkAsset,
    ) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

/** Summary of the downloaded APK shown to the user before the install press. */
data class ApkSummary(
    val fileName: String,
    val sizeBytes: Long,
    val packageName: String,
    val versionCode: Long,
    val latestTag: String,
)

/** In-app install pipeline: download → inspect → permission → official installer. */
sealed interface InstallStep {
    data object NotStarted : InstallStep
    data class DownloadingApk(val percent: Int) : InstallStep
    data object InspectingApk : InstallStep

    /** APK facts shown to the user; the install button is the only next action. */
    data class ReadyToInstall(val summary: ApkSummary) : InstallStep

    /** "Install unknown apps" missing — exactly one settings round trip. */
    data class NeedInstallPermission(val summary: ApkSummary) : InstallStep
    data object Installing : InstallStep

    /** Official confirmation dialog launched — the system UI owns the screen now. */
    data object WaitingSystemConfirmation : InstallStep
    data object InstallSucceeded : InstallStep

    /** User cancelled the official dialog — stays retryable with the same APK. */
    data class InstallAborted(val summary: ApkSummary) : InstallStep
    data class InstallBlocked(val systemMessage: String?) : InstallStep
    data class InstallFailed(val systemMessage: String?) : InstallStep

    /** Pre-install admission refused the archive (wrong app / not newer / signature / unreadable). */
    data class InstallRefused(val reason: Verdict) : InstallStep

    /** The release channel published no APK asset — nothing to install in-app. */
    data object AssetMissing : InstallStep
}

/** One-shot actions the screen must perform outside the state (start system UI). */
sealed interface UpdateSideEffect {
    data class LaunchInstallConfirmation(val intent: Intent) : UpdateSideEffect
    data class RequestInstallPermission(val intent: Intent) : UpdateSideEffect
}

data class UpdateUiState(
    val currentVersion: String,
    val status: UpdateStatus = UpdateStatus.Idle,
    val installStep: InstallStep = InstallStep.NotStarted,
)

/**
 * Update-check + in-app self-update orchestrator. The install path is strictly:
 * download from the release channel → verify (package/versionCode/signature)
 * → at most one "install unknown apps" settings round trip → official
 * PackageInstaller session with the system's own confirmation dialog. No Play
 * Store interaction, no silent install, no interference with security checks.
 */
class UpdateViewModel(
    private val checker: UpdateChecker,
    private val downloader: ApkDownloadGateway,
    private val verifier: ApkVerificationGateway,
    private val installer: ApkInstallGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState(currentVersion = checker.currentVersion))
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val _sideEffects = Channel<UpdateSideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    private var downloadedApk: File? = null
    private var lastReadySummary: ApkSummary? = null
    private var settingsRoundTrips = 0

    private val installListener: (InstallOutcome) -> Unit = { outcome -> onInstallOutcome(outcome) }

    init {
        installer.addInstallListener(installListener)
    }

    override fun onCleared() {
        installer.removeInstallListener(installListener)
        downloadedApk?.delete()
        downloadedApk = null
    }

    /** Update check — unchanged contract, now also carrying the installable asset. */
    fun check() {
        if (_uiState.value.status == UpdateStatus.Checking) return
        _uiState.update { it.copy(status = UpdateStatus.Checking) }
        viewModelScope.launch {
            val status = when (val result = checker.check()) {
                is UpdateCheckResult.UpToDate -> UpdateStatus.UpToDate
                is UpdateCheckResult.UpdateAvailable ->
                    result.asset?.let { asset ->
                        UpdateStatus.Available(result.latestVersion, result.releaseUrl, asset)
                    } ?: UpdateStatus.Failed(ASSET_MISSING_MESSAGE)
                is UpdateCheckResult.Failed -> UpdateStatus.Failed(result.message)
            }
            _uiState.update { it.copy(status = status) }
        }
    }

    /** CTA on the "update available" card: download then inspect, inside the app. */
    @Suppress("TooGenericExceptionCaught") // pipeline failures become UI states, not crashes
    fun downloadUpdate() {
        val available = _uiState.value.status as? UpdateStatus.Available ?: return
        _uiState.update { it.copy(installStep = InstallStep.DownloadingApk(percent = 0)) }
        viewModelScope.launch {
            try {
                val apk = downloader.download(available.asset, available.latestVersion) { percent ->
                    _uiState.update { state ->
                        state.copy(installStep = InstallStep.DownloadingApk(percent = percent))
                    }
                }
                inspect(apk, available.latestVersion)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Deliberately broad (like UpdateChecker.check): ANY pipeline
                // failure lands in the failed state instead of crashing the vault.
                _uiState.update {
                    it.copy(installStep = InstallStep.InstallFailed(systemMessage = failure.message))
                }
            }
        }
    }

    /** CTA on the "APK info" card: permission gate, then the official installer. */
    fun proceedToInstall() {
        val summary = (when (val step = _uiState.value.installStep) {
            is InstallStep.ReadyToInstall -> step.summary
            is InstallStep.InstallAborted -> step.summary
            else -> null
        }) ?: return
        if (installer.canRequestInstall()) {
            beginInstall()
        } else if (settingsRoundTrips == 0) {
            settingsRoundTrips++
            _uiState.update { it.copy(installStep = InstallStep.NeedInstallPermission(summary)) }
            viewModelScope.launch {
                _sideEffects.send(UpdateSideEffect.RequestInstallPermission(installer.unknownAppsSettingsIntent()))
            }
        } else {
            // One settings visit was already granted and the user still declined.
            _uiState.update { it.copy(installStep = InstallStep.InstallFailed(systemMessage = null)) }
        }
    }

    /**
     * Re-launches the same settings page if the first launch did not land
     * (e.g. the system dismissed it). Does NOT consume the single round trip —
     * only [onHostResumed] closes the permission gate.
     */
    fun reopenInstallSettings() {
        if (_uiState.value.installStep !is InstallStep.NeedInstallPermission) return
        viewModelScope.launch {
            _sideEffects.send(UpdateSideEffect.RequestInstallPermission(installer.unknownAppsSettingsIntent()))
        }
    }

    /** Called by the screen when the host activity resumes (settings round trip). */
    fun onHostResumed() {
        val step = _uiState.value.installStep
        if (step !is InstallStep.NeedInstallPermission) return
        if (installer.canRequestInstall()) {
            beginInstall()
        } else {
            _uiState.update { it.copy(installStep = InstallStep.InstallFailed(systemMessage = null)) }
        }
    }

    private suspend fun inspect(apk: File, latestTag: String) {
        _uiState.update { it.copy(installStep = InstallStep.InspectingApk) }
        val inspection = verifier.verify(apk, latestTag)
        val summary = inspection.archive?.let {
            ApkSummary(
                fileName = apk.name,
                sizeBytes = apk.length(),
                packageName = it.packageName,
                versionCode = it.versionCode,
                latestTag = latestTag,
            )
        }
        when {
            inspection.verdict == Verdict.Valid && summary != null -> {
                downloadedApk?.delete()
                downloadedApk = apk
                lastReadySummary = summary
                _uiState.update { it.copy(installStep = InstallStep.ReadyToInstall(summary)) }
            }
            // A concrete policy verdict is always reported as-is; only a valid
            // verdict WITHOUT parseable facts degrades to Unreadable.
            inspection.verdict != Verdict.Valid -> refuse(inspection.verdict, apk)
            else -> refuse(Verdict.Unreadable, apk)
        }
    }

    private suspend fun refuse(reason: Verdict, apk: File) {
        apk.delete()
        _uiState.update { it.copy(installStep = InstallStep.InstallRefused(reason)) }
    }

    @Suppress("TooGenericExceptionCaught") // installer failures become UI states, not crashes
    private fun beginInstall() {
        val apk = downloadedApk ?: run {
            _uiState.update { it.copy(installStep = InstallStep.InstallFailed(systemMessage = null)) }
            return
        }
        _uiState.update { it.copy(installStep = InstallStep.Installing) }
        viewModelScope.launch {
            try {
                installer.install(listOf(apk))
                // Outcome arrives through [installListener]; commit returning only
                // means the session was handed to the system successfully.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Deliberately broad (like UpdateChecker.check): ANY pipeline
                // failure lands in the failed state instead of crashing the vault.
                _uiState.update {
                    it.copy(installStep = InstallStep.InstallFailed(systemMessage = failure.message))
                }
            }
        }
    }

    private fun onInstallOutcome(outcome: InstallOutcome) {
        when (outcome) {
            is InstallOutcome.PendingUserAction -> {
                _uiState.update { it.copy(installStep = InstallStep.WaitingSystemConfirmation) }
                viewModelScope.launch {
                    _sideEffects.send(UpdateSideEffect.LaunchInstallConfirmation(outcome.activityIntent))
                }
            }
            InstallOutcome.Success ->
                _uiState.update { it.copy(installStep = InstallStep.InstallSucceeded) }
            InstallOutcome.Aborted -> {
                val summary = lastReadySummary ?: return
                _uiState.update { it.copy(installStep = InstallStep.InstallAborted(summary)) }
            }
            is InstallOutcome.Blocked ->
                _uiState.update { it.copy(installStep = InstallStep.InstallBlocked(outcome.systemMessage)) }
            is InstallOutcome.Failed ->
                _uiState.update { it.copy(installStep = InstallStep.InstallFailed(outcome.systemMessage)) }
        }
    }

    private companion object {
        const val ASSET_MISSING_MESSAGE = "release has no installable apk asset"
    }
}
