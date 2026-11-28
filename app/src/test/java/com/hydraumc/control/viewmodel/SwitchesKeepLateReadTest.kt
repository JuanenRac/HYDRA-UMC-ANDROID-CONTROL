// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/SwitchesKeepLateReadTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.EmptyAuthStore
import com.hydraumc.control.MainDispatcherSwap
import com.hydraumc.control.network.ConnectionPrefs
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.ui.ObscuredTouchPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The switches that the view model reads from storage when it starts: one the person changes before that read ends keeps the new value.
 * The stored choice is the default and the person picks the opposite at once; the check is made after the read had time to finish.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwitchesKeepLateReadTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private class Case(val flag: String, val default: Boolean, val state: (RobotViewModel) -> Boolean, val change: (RobotViewModel, Boolean) -> Unit)

    private val cases = listOf(
        Case(ConnectionPrefs.Flags.DEAD_MAN_JOG, RobotCommandContract.DEAD_MAN_JOG_ENABLED_BY_DEFAULT, { it.deadMan.enabled.value }, { m, on -> m.setDeadManEnabled(on) }),
        Case(ConnectionPrefs.Flags.LINK_BLOCK_JOG, RobotCommandContract.LINK_HEALTH_BLOCK_JOG_ON_BAD_LINK_DEFAULT, { it.deadMan.blockOnBadLink.value }, { m, on -> m.setLinkBlockOnBadLink(on) }),
        Case(ConnectionPrefs.Flags.LINK_INDICATOR, RobotCommandContract.LINK_HEALTH_INDICATOR_ENABLED_BY_DEFAULT, { it.deadMan.indicator.value }, { m, on -> m.setLinkIndicator(on) }),
        Case(ConnectionPrefs.Flags.FILTER_OBSCURED_TOUCHES, ObscuredTouchPolicy.ENABLED_BY_DEFAULT, { it.filterObscuredTouches.value }, { m, on -> m.setFilterObscuredTouches(on) }),
        Case(ConnectionPrefs.Flags.PAIRING, RobotCommandContract.PAIRING_ENABLED_BY_DEFAULT, { it.pairingEnabled.value }, { m, on -> m.setPairingEnabled(on) }),
        Case(ConnectionPrefs.Flags.WATCH_ALERTS, RobotCommandContract.WATCH_ALERTS_ENABLED_BY_DEFAULT, { it.watchAlertsEnabled.value }, { m, on -> m.setWatchAlertsEnabled(on) }),
        Case(ConnectionPrefs.Flags.PRESENCE_RELAY, RobotCommandContract.PRESENCE_RELAY_ENABLED_BY_DEFAULT, { it.presenceRelayEnabled.value }, { m, on -> m.setPresenceRelayEnabled(on) }),
        Case(ConnectionPrefs.Flags.VOICE_CONFIRM_RELAY, RobotCommandContract.VOICE_CONFIRM_ENABLED_BY_DEFAULT, { it.voiceConfirmRelayEnabled.value }, { m, on -> m.setVoiceConfirmRelayEnabled(on) }),
    )

    @Before
    fun setUp() {
        MainDispatcherSwap.set()
        // The stored choices outlive a test: every test starts from the defaults.
        runBlocking { cases.forEach { ConnectionPrefs(context).saveFlag(it.flag, it.default) } }
    }

    @After
    fun tearDown() {
        // Nothing is left chosen for the tests of other classes that run in the same process.
        runBlocking { cases.forEach { ConnectionPrefs(context).forgetFlag(it.flag) } }
        MainDispatcherSwap.reset()
    }

    @Test
    fun `a switch changed while the saved choices are being read keeps the new value`() {
        for (case in cases) {
            val model = RobotViewModel(context, EmptyAuthStore)
            case.change(model, !case.default)
            Thread.sleep(600) // the read of the saved choices (on another thread) has ended by now
            assertEquals("${case.flag} must keep the choice made at once", !case.default, case.state(model))
            // Leave the stored choice as the next case expects it.
            runBlocking { ConnectionPrefs(context).saveFlag(case.flag, case.default) }
        }
    }

    @Test
    fun `a switch nobody touched takes the saved choice`() {
        runBlocking { cases.forEach { ConnectionPrefs(context).saveFlag(it.flag, !it.default) } }
        val model = RobotViewModel(context, EmptyAuthStore)
        Thread.sleep(600)
        for (case in cases) assertEquals("${case.flag} must take the saved choice", !case.default, case.state(model))
    }
}
