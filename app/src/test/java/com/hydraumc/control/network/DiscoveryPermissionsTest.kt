// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/DiscoveryPermissionsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The permissions asked at start depend on the API level, and the local network one only exists from API 37. */
class DiscoveryPermissionsTest {
    @Test
    fun anOldSystemAsksOnlyForLocation() {
        assertEquals(listOf(DiscoveryPermissions.FINE_LOCATION, DiscoveryPermissions.COARSE_LOCATION), DiscoveryPermissions.forSdk(30))
    }

    @Test
    fun bluetoothComesWithApi31AndNotificationsAndNearbyWifiWithApi33() {
        assertTrue(DiscoveryPermissions.BLUETOOTH_SCAN in DiscoveryPermissions.forSdk(31))
        assertFalse(DiscoveryPermissions.POST_NOTIFICATIONS in DiscoveryPermissions.forSdk(32))
        assertTrue(DiscoveryPermissions.POST_NOTIFICATIONS in DiscoveryPermissions.forSdk(33))
        assertTrue(DiscoveryPermissions.NEARBY_WIFI_DEVICES in DiscoveryPermissions.forSdk(33))
    }

    @Test
    fun theLocalNetworkPermissionIsAskedOnlyFromApi37() {
        assertFalse(DiscoveryPermissions.ACCESS_LOCAL_NETWORK in DiscoveryPermissions.forSdk(36))
        assertTrue(DiscoveryPermissions.ACCESS_LOCAL_NETWORK in DiscoveryPermissions.forSdk(37))
    }

    @Test
    fun noPermissionIsAskedTwice() {
        for (sdk in listOf(24, 30, 31, 33, 36, 37, 40)) {
            val list = DiscoveryPermissions.forSdk(sdk)
            assertEquals(list.size, list.toSet().size)
        }
    }

    @Test
    fun everyPermissionAskedAtRuntimeIsDeclaredInTheManifest() {
        val manifest = listOf("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml")
            .map { File(it) }.first { it.isFile }.readText()
        for (permission in DiscoveryPermissions.forSdk(40)) {
            assertTrue("$permission is not declared in the manifest", manifest.contains("android:name=\"$permission\""))
        }
    }

    @Test
    fun theTargetSdkIsTheCompileSdk() {
        val build = listOf("app/build.gradle.kts", "build.gradle.kts").map { File(it) }.first { it.isFile }.readText()
        val compile = Regex("""compileSdk\s*=\s*(\d+)""").find(build)!!.groupValues[1]
        val target = Regex("""targetSdk\s*=\s*(\d+)""").find(build)!!.groupValues[1]
        assertEquals(compile, target)
    }
}
