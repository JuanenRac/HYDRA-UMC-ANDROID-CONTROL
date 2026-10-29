// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Lifecycle-aware application update state
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hydraumc.control.BuildConfig
import com.hydraumc.control.update.AvailableUpdate
import com.hydraumc.control.update.GitHubReleaseUpdater
import com.hydraumc.control.update.UpdateCheckResult
import com.hydraumc.control.update.UpdateDownloadResult
import com.hydraumc.control.wear.WatchCompanionVersionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The update UI never reports an update as installed: Android owns that final step. */
sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data class Available(val update: AvailableUpdate) : AppUpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Int?) : AppUpdateState
    data class InstallPermissionRequired(val update: AvailableUpdate) : AppUpdateState
    data object Installing : AppUpdateState
    data class Failed(val message: String) : AppUpdateState
}

/**
 * What the screen shows when the app comes back to the foreground after the
 * system installer was opened. A finished installation replaces the running
 * app, so still being here means the installer was cancelled or refused: the
 * update is offered again instead of staying on "installing" forever.
 */
internal fun stateAfterInstallerReturn(current: AppUpdateState, pending: AvailableUpdate?): AppUpdateState =
    if (current == AppUpdateState.Installing && pending != null) AppUpdateState.Available(pending) else current

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val updater = GitHubReleaseUpdater(application.applicationContext)
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()
    private var installingUpdate: AvailableUpdate? = null

    /** Safe to call at startup: it only reads release metadata, never downloads an APK. */
    fun checkForUpdate() {
        if (mutableState.value is AppUpdateState.Checking) return
        viewModelScope.launch {
            mutableState.value = AppUpdateState.Checking
            mutableState.value = when (val result = updater.checkForUpdate()) {
                UpdateCheckResult.UpToDate -> AppUpdateState.UpToDate
                is UpdateCheckResult.Available -> AppUpdateState.Available(result.update)
                is UpdateCheckResult.Failed -> AppUpdateState.Failed(result.message)
            }
        }
    }

    /** Called only after the operator explicitly chooses Download and install. */
    fun downloadAndInstall(update: AvailableUpdate) {
        viewModelScope.launch {
            val result = updater.download(update) { progress ->
                mutableState.value = AppUpdateState.Downloading(update, progress)
            }
            when (result) {
                is UpdateDownloadResult.Failed -> mutableState.value = AppUpdateState.Failed(result.message)
                is UpdateDownloadResult.ReadyToInstall -> {
                    if (updater.canRequestPackageInstalls()) {
                        installingUpdate = update
                        mutableState.value = AppUpdateState.Installing
                        updater.launchSystemInstaller(result.apk)
                    } else {
                        mutableState.value = AppUpdateState.InstallPermissionRequired(update)
                    }
                }
            }
        }
    }

    fun openInstallPermissionSettings() = updater.openInstallPermissionSettings()

    /**
     * Status-only payload for a future authenticated Android-to-Watch
     * transport. It contains no APK URL or installation command, by design.
     */
    fun currentWatchCompanionVersionStatus(): WatchCompanionVersionStatus =
        WatchCompanionVersionStatus(
            appVersion = BuildConfig.VERSION_NAME,
            updateAvailable = when (mutableState.value) {
                is AppUpdateState.Available,
                is AppUpdateState.Downloading,
                is AppUpdateState.InstallPermissionRequired,
                AppUpdateState.Installing -> true
                else -> false
            },
        )

    /**
     * Called when Android Control returns to the foreground after its
     * per-app unknown-sources permission screen. The APK was verified before
     * that screen was opened and is verified again here before launch.
     */
    fun resumeInstallAfterPermissionApproval() {
        val permissionState = mutableState.value as? AppUpdateState.InstallPermissionRequired ?: return
        installingUpdate = permissionState.update
        if (!updater.canRequestPackageInstalls()) return
        val apk = updater.cachedInstallableApk()
        if (apk == null) {
            mutableState.value = AppUpdateState.Failed(
                "The downloaded update is no longer available or valid. Check for updates again.",
            )
            return
        }
        mutableState.value = AppUpdateState.Installing
        updater.launchSystemInstaller(apk)
    }

    /** Called on every return to the foreground, before the permission check above. */
    fun installerClosed() {
        val next = stateAfterInstallerReturn(mutableState.value, installingUpdate)
        if (next != mutableState.value) mutableState.value = next
    }

    /** Hides a startup prompt; Settings can always issue a new explicit check. */
    fun dismiss() {
        mutableState.value = AppUpdateState.Idle
    }
}
