// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/update/GitHubReleaseUpdaterTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
// GitHubReleaseUpdater.download() had zero test coverage of its own -
// ReleaseMetadataParserTest only covers the pure metadata gate that runs
// BEFORE any bytes are fetched. This covers download()'s own real
// defensive checks and, crucially, that a rejected download leaves NO
// half-written / stale APK behind that cachedInstallableApk() could later
// hand to Android's installer (the "rollback after a failed install
// attempt" the 2026-09-08 audit named as untested).
//
// A real MockWebServer serves the APK bytes over a real loopback socket
// (download() only checks the URL scheme via ReleaseMetadataParser, not
// here, so http:// from MockWebServer is fine). Robolectric's
// ShadowPackageManager stands in for the real APK manifest parse
// (getPackageArchiveInfo) and the installed-app version lookup.
package com.hydraumc.control.update

import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class GitHubReleaseUpdaterTest {

    private lateinit var server: MockWebServer
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val updater = GitHubReleaseUpdater(context)
    private val cachedApk = File(File(context.cacheDir, "updates"), "HYDRA-UMC-ANDROID-CONTROL-update.apk")
    private val cachedPart = File(cachedApk.parentFile, "HYDRA-UMC-ANDROID-CONTROL-update.apk.part")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // Installed app reports versionCode 5 - a downloaded APK must beat that.
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).longVersionCode = 5L
    }

    @After
    fun tearDown() {
        server.shutdown()
        cachedApk.delete()
        cachedPart.delete()
    }

    private fun update() = AvailableUpdate(
        version = SemanticVersion(9, 9, 9),
        releaseName = "test",
        notes = "",
        assetUrl = server.url("/HYDRA-UMC-ANDROID-CONTROL-release.apk").toString(),
    )

    private fun apkBody(size: Int): MockResponse =
        MockResponse().setResponseCode(200).setBody(Buffer().write(ByteArray(size) { it.toByte() }))

    private fun registerArchive(packageName: String = context.packageName, versionCode: Long = 42L) {
        val info = PackageInfo().apply {
            this.packageName = packageName
            this.longVersionCode = versionCode
        }
        shadowOf(context.packageManager).setPackageArchiveInfo(cachedApk.absolutePath, info)
    }

    @Test
    fun `a valid newer APK downloads to a ready-to-install file`() = runBlocking {
        server.enqueue(apkBody(2048))
        registerArchive(versionCode = 42L)

        val result = updater.download(update()) {}

        assertTrue("expected ReadyToInstall, got $result", result is UpdateDownloadResult.ReadyToInstall)
        assertTrue("the final APK file must exist", cachedApk.isFile)
        assertFalse("no .part file must be left behind", cachedPart.exists())
        assertEquals(cachedApk, updater.cachedInstallableApk())
    }

    @Test
    fun `a mid-stream connection drop is rejected and never finalises an APK`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                .setBody(Buffer().write(ByteArray(64 * 1024) { it.toByte() })),
        )

        val result = updater.download(update()) {}

        assertTrue("a dropped download must never report success", result is UpdateDownloadResult.Failed)
        assertFalse("a failed download must not produce a finalised APK", cachedApk.exists())
        assertNull("nothing installable must be cached after a failed download", updater.cachedInstallableApk())
    }

    @Test
    fun `an APK for a different package is rejected and deleted`() = runBlocking {
        server.enqueue(apkBody(2048))
        registerArchive(packageName = "com.evil.other", versionCode = 42L)

        val result = updater.download(update()) {}

        assertTrue(result is UpdateDownloadResult.Failed)
        assertTrue((result as UpdateDownloadResult.Failed).message.contains("package name"))
        assertFalse("a wrong-package APK must be deleted, never left for cachedInstallableApk()", cachedApk.exists())
        assertNull(updater.cachedInstallableApk())
    }

    @Test
    fun `an APK not newer than the installed app is rejected and deleted`() = runBlocking {
        server.enqueue(apkBody(2048))
        registerArchive(versionCode = 5L) // equal to installed - not newer

        val result = updater.download(update()) {}

        assertTrue(result is UpdateDownloadResult.Failed)
        assertTrue((result as UpdateDownloadResult.Failed).message.contains("not newer"))
        assertFalse(cachedApk.exists())
        assertNull(updater.cachedInstallableApk())
    }

    @Test
    fun `a file that does not parse as an Android package is rejected and deleted`() = runBlocking {
        server.enqueue(apkBody(2048))
        // No registerArchive() call -> getPackageArchiveInfo returns null.

        val result = updater.download(update()) {}

        assertTrue(result is UpdateDownloadResult.Failed)
        assertTrue((result as UpdateDownloadResult.Failed).message.contains("valid Android package"))
        assertFalse(cachedApk.exists())
    }

    @Test
    fun `an HTTP error surfaces as Failed with the status code, no file written`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))

        val result = updater.download(update()) {}

        assertTrue(result is UpdateDownloadResult.Failed)
        assertTrue((result as UpdateDownloadResult.Failed).message.contains("404"))
        assertFalse(cachedApk.exists())
        assertFalse(cachedPart.exists())
    }

    @Test
    fun `cachedInstallableApk returns null once the installed app catches up to a previously cached APK`() = runBlocking {
        server.enqueue(apkBody(2048))
        registerArchive(versionCode = 42L)
        assertTrue(updater.download(update()) {} is UpdateDownloadResult.ReadyToInstall)
        assertEquals(cachedApk, updater.cachedInstallableApk())

        // Simulate the user having installed that update (or a newer one):
        // the same cached file is no longer "newer" and must not be offered.
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).longVersionCode = 42L

        assertNull(updater.cachedInstallableApk())
    }
}
