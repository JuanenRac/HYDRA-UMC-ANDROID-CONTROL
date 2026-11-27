// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/PairingScannerTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.R
import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.UserProfile
import com.hydraumc.control.viewmodel.RobotViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** The QR code read with the camera goes through the same door as a pasted link: off by default, checked, and asked first. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-xhdpi")
class PairingScannerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private val emptyStore = object : AuthStore {
        override suspend fun loadAuth() = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)
        override suspend fun saveAuth(profile: UserProfile) {}
    }

    private class FakeReader(private val text: String?) : QrReader {
        var opened = 0
        override fun read(context: Context, onText: (String) -> Unit, onUnavailable: () -> Unit) {
            opened++
            if (text == null) onUnavailable() else onText(text)
        }
    }

    private fun show(model: RobotViewModel, reader: QrReader) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalQrReader provides reader) {
                Column(Modifier.verticalScroll(rememberScrollState())) { PairingSection(model) }
            }
        }
    }

    private fun scan() {
        composeTestRule.onNodeWithText(app.getString(R.string.pairing_scan_button)).performScrollTo().performClick()
    }

    private fun waitFor(condition: () -> Boolean) {
        composeTestRule.waitUntil(5_000) {
            ShadowLooper.idleMainLooper()
            condition()
        }
    }

    private val link = "hydra-umc://pair?host=192.168.0.180&port=3000&fp=" + "ab".repeat(32) + "&name=Workshop"

    @Test
    fun thereIsNoScanButtonWhilePairingIsOff() {
        val reader = FakeReader(link)
        show(RobotViewModel(app, emptyStore), reader)
        composeTestRule.onNodeWithText(app.getString(R.string.pairing_scan_button)).assertDoesNotExist()
        assertEquals("the camera is never opened by itself", 0, reader.opened)
    }

    @Test
    fun aScannedLinkIsOnlyShownAndAsked() {
        val model = RobotViewModel(app, emptyStore)
        val before = model.ipAddress.value
        val reader = FakeReader(link)
        model.setPairingEnabled(true)
        show(model, reader)
        assertEquals("the camera waits for the tap", 0, reader.opened)
        scan()
        waitFor { model.pairingOffer.value != null }
        assertEquals("192.168.0.180", model.pairingOffer.value?.host)
        assertEquals("nothing is kept before the person confirms", before, model.ipAddress.value)
        assertEquals("", model.tlsSettings.value.pinnedFingerprint)
    }

    @Test
    fun aCodeThatIsNotAPairingLinkIsRefused() {
        val model = RobotViewModel(app, emptyStore)
        model.setPairingEnabled(true)
        show(model, FakeReader("https://example.test/not-a-pairing"))
        scan()
        waitFor { model.lastError.value == app.getString(R.string.pairing_problem_not_link) }
        assertNull(model.pairingOffer.value)
    }

    @Test
    fun aLinkWithoutFingerprintIsRefusedAlsoWhenScanned() {
        val model = RobotViewModel(app, emptyStore)
        model.setPairingEnabled(true)
        show(model, FakeReader("hydra-umc://pair?host=192.168.0.180&port=3000"))
        scan()
        waitFor { model.lastError.value == app.getString(R.string.pairing_problem_missing_fingerprint) }
        assertNull(model.pairingOffer.value)
    }

    @Test
    fun aPhoneWithoutACodeReaderIsToldSo() {
        val model = RobotViewModel(app, emptyStore)
        model.setPairingEnabled(true)
        show(model, FakeReader(null))
        scan()
        waitFor { model.lastError.value == app.getString(R.string.pairing_scan_unavailable) }
    }

    @Test
    fun theAppAsksForNoCameraPermission() {
        val info = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
        assertFalse(info.requestedPermissions.orEmpty().contains(android.Manifest.permission.CAMERA))
    }
}
