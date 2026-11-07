// =============================================================================
// HYDRA-UMC CONTROL - Instrumented checks of the installed app
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control

import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hydraumc.control.util.BiometricHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks that need a real Android system and so run on an emulator (started by the manual workflow): what the package manager
 * keeps of the manifest on this version of Android, and what the biometric helper says on a device with nothing enrolled.
 */
@RunWith(AndroidJUnit4::class)
class InstalledAppInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun requested(): Set<String> {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions?.toSet() ?: emptySet()
    }

    @Test
    fun theAppIsTheOneThatWasBuilt() {
        assertEquals("com.hydraumc.control", context.packageName)
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        assertNotNull(info.versionName)
        assertTrue(info.versionName!!.isNotBlank())
    }

    @Test
    fun theLegacyBluetoothPermissionsAreOnlyRequestedUpToAndroid11() {
        val permissions = requested()
        val legacy = setOf("android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN")
        for (name in legacy) {
            assertEquals("$name on API ${Build.VERSION.SDK_INT}", Build.VERSION.SDK_INT <= 30, name in permissions)
        }
    }

    @Test
    fun theNewBluetoothPermissionsAreRequestedFromAndroid12() {
        val permissions = requested()
        if (Build.VERSION.SDK_INT >= 31) {
            assertTrue("android.permission.BLUETOOTH_SCAN" in permissions)
            assertTrue("android.permission.BLUETOOTH_CONNECT" in permissions)
        }
    }

    @Test
    fun theAppNeverAsksToSwitchTheWifiOnOrOff() {
        assertFalse("android.permission.CHANGE_WIFI_STATE" in requested())
    }

    @Test
    fun theAppNeedsTheNetworkAndNothingToInstallSilently() {
        val permissions = requested()
        assertTrue("android.permission.INTERNET" in permissions)
        // the app hands a verified APK to the system installer; it never installs by itself
        assertFalse("android.permission.INSTALL_PACKAGES" in permissions)
    }

    @Test
    fun withNothingEnrolledTheBiometricHelperSaysNoAndDoesNotCrash() {
        // A fresh emulator has no fingerprint or face: the login screen must then not offer the biometric button.
        assertFalse(BiometricHelper.isBiometricAvailable(context))
    }
}
