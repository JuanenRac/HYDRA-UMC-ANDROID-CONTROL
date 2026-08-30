// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Safe GitHub Release update client
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hydraumc.control.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** A stable GitHub release and its explicitly named installable APK asset. */
data class AvailableUpdate(
    val version: SemanticVersion,
    val releaseName: String,
    val notes: String,
    val assetUrl: String,
)

/** Result of checking the trusted public release endpoint. */
sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val update: AvailableUpdate) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

/** Result of downloading the APK before control is handed to Android's installer. */
sealed interface UpdateDownloadResult {
    data class ReadyToInstall(val apk: File) : UpdateDownloadResult
    data class Failed(val message: String) : UpdateDownloadResult
}

/**
 * Queries the official release feed and downloads only the named stable asset.
 * Signature enforcement happens again in Android's package installer, which
 * refuses an update not signed by the same certificate as the installed app.
 */
class GitHubReleaseUpdater(private val context: Context) {
    private val client = OkHttpClient()

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "HYDRA-UMC-ANDROID-CONTROL/${BuildConfig.VERSION_NAME}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateCheckResult.Failed("GitHub returned HTTP ${response.code}.")
                }
                val payload = response.body?.string()
                    ?: return@withContext UpdateCheckResult.Failed("GitHub returned an empty release response.")
                ReleaseMetadataParser.parseLatestStable(payload, BuildConfig.VERSION_NAME)
            }
        } catch (error: IOException) {
            UpdateCheckResult.Failed("Unable to check GitHub Releases: ${error.message ?: "network error"}")
        } catch (error: Exception) {
            UpdateCheckResult.Failed("Invalid GitHub release metadata: ${error.message ?: "unknown error"}")
        }
    }

    suspend fun download(update: AvailableUpdate, onProgress: (Int?) -> Unit): UpdateDownloadResult =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(update.assetUrl)
                    .header("User-Agent", "HYDRA-UMC-ANDROID-CONTROL/${BuildConfig.VERSION_NAME}")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext UpdateDownloadResult.Failed("APK download returned HTTP ${response.code}.")
                    }
                    val body = response.body
                        ?: return@withContext UpdateDownloadResult.Failed("APK download was empty.")
                    val updateDirectory = File(context.cacheDir, UPDATE_DIRECTORY).apply { mkdirs() }
                    val partial = File(updateDirectory, "$APK_FILE_NAME.part")
                    val apk = File(updateDirectory, APK_FILE_NAME)
                    partial.delete()
                    apk.delete()
                    val expectedLength = body.contentLength()
                    var downloaded = 0L
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val bytesRead = input.read(buffer)
                                if (bytesRead == -1) break
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
                                onProgress(
                                    if (expectedLength > 0) ((downloaded * 100) / expectedLength).toInt() else null,
                                )
                            }
                            output.flush()
                        }
                    }
                    if (expectedLength >= 0 && downloaded != expectedLength) {
                        partial.delete()
                        return@withContext UpdateDownloadResult.Failed(
                            "APK download was incomplete: expected $expectedLength bytes, received $downloaded.",
                        )
                    }
                    if (!partial.renameTo(apk)) {
                        return@withContext UpdateDownloadResult.Failed("Could not finalise the downloaded APK.")
                    }
                    val packageInfo = packageArchiveInfo(apk)
                        ?: run {
                            apk.delete()
                            return@withContext UpdateDownloadResult.Failed("Downloaded file is not a valid Android package.")
                        }
                    if (packageInfo.packageName != context.packageName) {
                        apk.delete()
                        return@withContext UpdateDownloadResult.Failed("Downloaded APK has an unexpected package name.")
                    }
                    if (packageVersionCode(packageInfo) <= installedVersionCode()) {
                        apk.delete()
                        return@withContext UpdateDownloadResult.Failed("Downloaded APK is not newer than the installed application.")
                    }
                    UpdateDownloadResult.ReadyToInstall(apk)
                }
            } catch (error: IOException) {
                UpdateDownloadResult.Failed("Unable to download the APK: ${error.message ?: "network error"}")
            } catch (error: Exception) {
                UpdateDownloadResult.Failed("Unable to validate the APK: ${error.message ?: "unknown error"}")
            }
        }

    /** Android 8+ requires a one-time per-app approval before opening an APK installer. */
    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens only Android's own per-app unknown-source approval page. */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Delegates the final signature and user-consent checks to Android's package installer. */
    fun launchSystemInstaller(apk: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updateprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /**
     * Returns the already downloaded update only when it is still a valid,
     * newer APK for this exact application. This makes returning from
     * Android's unknown-sources approval screen resume the install without a
     * second network download or trusting a stale cache file.
     */
    fun cachedInstallableApk(): File? {
        val apk = File(File(context.cacheDir, UPDATE_DIRECTORY), APK_FILE_NAME)
        if (!apk.isFile) return null
        val packageInfo = packageArchiveInfo(apk) ?: return null
        if (packageInfo.packageName != context.packageName) return null
        return apk.takeIf { packageVersionCode(packageInfo) > installedVersionCode() }
    }

    @Suppress("DEPRECATION")
    private fun packageArchiveInfo(apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(packageInfo: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun installedVersionCode(): Long {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return packageVersionCode(packageInfo)
    }

    private companion object {
        const val REPOSITORY = "JuanenRac/HYDRA-UMC-ANDROID-CONTROL"
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
        const val UPDATE_DIRECTORY = "updates"
        const val APK_FILE_NAME = "HYDRA-UMC-ANDROID-CONTROL-update.apk"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
