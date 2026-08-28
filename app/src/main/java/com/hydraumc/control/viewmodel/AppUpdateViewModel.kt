// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Lifecycle-aware application update state
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hydraumc.control.update.AvailableUpdate
import com.hydraumc.control.update.GitHubReleaseUpdater
import com.hydraumc.control.update.UpdateCheckResult
import com.hydraumc.control.update.UpdateDownloadResult
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

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val updater = GitHubReleaseUpdater(application.applicationContext)
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

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

    /** Hides a startup prompt; Settings can always issue a new explicit check. */
    fun dismiss() {
        mutableState.value = AppUpdateState.Idle
    }
}
