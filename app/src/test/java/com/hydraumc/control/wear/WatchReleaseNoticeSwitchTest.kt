// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/wear/WatchReleaseNoticeSwitchTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.EmptyAuthStore
import com.hydraumc.control.MainDispatcherSwap
import com.hydraumc.control.network.ConnectionPrefs
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.viewmodel.AppUpdateViewModel
import com.hydraumc.control.viewmodel.RobotViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The switch of the phone that stops it telling the watch about a newer release: on as it ships (what the app did), kept, read by the update screen, and in the contract. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchReleaseNoticeSwitchTest {
    // The stored choice lives in a file that outlives a test; another test of the run may have left it off, so every test starts from the default.
    @Before
    fun setUp() {
        MainDispatcherSwap.set()
        runBlocking { ConnectionPrefs(context).saveFlag(ConnectionPrefs.Flags.WATCH_RELEASE_NOTICE, RobotCommandContract.WATCH_ALERTS_RELEASE_NOTICE_ENABLED_BY_DEFAULT) }
    }

    @After
    fun tearDown() = MainDispatcherSwap.reset()

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the default is the one of the contract and the watch is not told until the person turns it on`() {
        val contract = JSONObject(File("../docs/robot_command_contract.json").readText()).getJSONObject("watchAlerts")
        assertEquals(contract.getBoolean("releaseNoticeEnabledByDefault"), RobotCommandContract.WATCH_ALERTS_RELEASE_NOTICE_ENABLED_BY_DEFAULT)
        assertFalse(RobotCommandContract.WATCH_ALERTS_RELEASE_NOTICE_ENABLED_BY_DEFAULT)
        assertFalse(RobotViewModel(context, EmptyAuthStore).watchReleaseNoticeEnabled.value)
    }

    @Test
    fun `the switch is kept and read back`() = runBlocking {
        val prefs = ConnectionPrefs(context)
        val default = RobotCommandContract.WATCH_ALERTS_RELEASE_NOTICE_ENABLED_BY_DEFAULT
        assertEquals(default, prefs.loadFlag(ConnectionPrefs.Flags.WATCH_RELEASE_NOTICE, default))
        prefs.saveFlag(ConnectionPrefs.Flags.WATCH_RELEASE_NOTICE, false)
        assertFalse(prefs.loadFlag(ConnectionPrefs.Flags.WATCH_RELEASE_NOTICE, default))
        prefs.saveFlag(ConnectionPrefs.Flags.WATCH_RELEASE_NOTICE, true)
        assertTrue(prefs.loadFlag(ConnectionPrefs.Flags.WATCH_RELEASE_NOTICE, default))
        assertFalse("it has a name of its own", ConnectionPrefs.Flags.WATCH_RELEASE_NOTICE == ConnectionPrefs.Flags.WATCH_ALERTS)
    }

    @Test
    fun `the update screen reads the switch of the settings`() = runBlocking {
        val screen = AppUpdateViewModel(context)
        assertFalse(screen.releaseNoticeSwitch())
        ConnectionPrefs(context).saveFlag(ConnectionPrefs.Flags.WATCH_RELEASE_NOTICE, true)
        assertTrue(screen.releaseNoticeSwitch())
        ConnectionPrefs(context).saveFlag(ConnectionPrefs.Flags.WATCH_RELEASE_NOTICE, true)
    }

    @Test
    fun `the setting changes the state at once`() {
        val model = RobotViewModel(context, EmptyAuthStore)
        model.setWatchReleaseNoticeEnabled(false)
        assertFalse(model.watchReleaseNoticeEnabled.value)
        model.setWatchReleaseNoticeEnabled(true)
        assertTrue(model.watchReleaseNoticeEnabled.value)
    }
}
