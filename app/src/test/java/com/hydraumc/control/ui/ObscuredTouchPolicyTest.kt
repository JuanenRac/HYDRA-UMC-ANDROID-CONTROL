// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/ObscuredTouchPolicyTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import com.hydraumc.control.EmptyAuthStore
import android.app.Application
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.MainDispatcherSwap
import com.hydraumc.control.network.ConnectionPrefs
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.viewmodel.RobotViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A touch through an overlay is dropped by default, and the switch in Settings can turn that off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ObscuredTouchPolicyTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = ConnectionPrefs(context)

    @Before
    fun setUp() {
        MainDispatcherSwap.set()
        forgetChoices() // the stored choices outlive a test: every test starts as a fresh install
    }

    @After
    fun tearDown() {
        forgetChoices()
        MainDispatcherSwap.reset()
    }

    private fun forgetChoices() = runBlocking {
        prefs.forgetFlag(ConnectionPrefs.Flags.FILTER_OBSCURED_TOUCHES)
        prefs.forgetFlag(ConnectionPrefs.Flags.PAIRING)
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val end = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < end) { "timed out waiting for $what" }
            Thread.sleep(10)
        }
    }

    private fun storedFilter(): Boolean? = runBlocking {
        val ifAbsentTrue = prefs.loadFlag(ConnectionPrefs.Flags.FILTER_OBSCURED_TOUCHES, true)
        val ifAbsentFalse = prefs.loadFlag(ConnectionPrefs.Flags.FILTER_OBSCURED_TOUCHES, false)
        if (ifAbsentTrue == ifAbsentFalse) ifAbsentTrue else null
    }

    @Test
    fun theProtectionIsOnByDefault() {
        assertTrue(ObscuredTouchPolicy.ENABLED_BY_DEFAULT)
        assertNull(storedFilter())
        // A second switch, read right after this one when the view model starts, tells when the saved choices have arrived.
        val other = !RobotCommandContract.PAIRING_ENABLED_BY_DEFAULT
        runBlocking { prefs.saveFlag(ConnectionPrefs.Flags.PAIRING, other) }
        val viewModel = RobotViewModel(context, EmptyAuthStore)
        assertTrue(viewModel.filterObscuredTouches.value)
        waitUntil("the saved choices") { viewModel.pairingEnabled.value == other }
        assertTrue(viewModel.filterObscuredTouches.value)
    }

    @Test
    fun aCoveredWindowDropsTheTouchOnlyWhileTheSwitchIsOn() {
        assertTrue(ObscuredTouchPolicy.drops(MotionEvent.FLAG_WINDOW_IS_OBSCURED, enabled = true))
        assertTrue(ObscuredTouchPolicy.drops(0x2, enabled = true))
        assertFalse(ObscuredTouchPolicy.drops(MotionEvent.FLAG_WINDOW_IS_OBSCURED, enabled = false))
        assertFalse(ObscuredTouchPolicy.drops(0, enabled = true))
    }

    @Test
    fun aStopButtonIsNeverDroppedByTheFilter() {
        val key = Any()
        ObscuredTouchPolicy.setStopZone(key, android.graphics.RectF(10f, 10f, 100f, 60f))
        try {
            assertFalse(ObscuredTouchPolicy.dropsOutsideStop(MotionEvent.FLAG_WINDOW_IS_OBSCURED, true, 50f, 30f))
            assertTrue(ObscuredTouchPolicy.dropsOutsideStop(MotionEvent.FLAG_WINDOW_IS_OBSCURED, true, 500f, 300f))
        } finally {
            ObscuredTouchPolicy.clearStopZone(key)
        }
        assertTrue(ObscuredTouchPolicy.dropsOutsideStop(MotionEvent.FLAG_WINDOW_IS_OBSCURED, true, 50f, 30f))
    }

    @Test
    fun theSwitchSetsTheFilterOfTheView() {
        val view = View(ApplicationProvider.getApplicationContext<Application>())
        ObscuredTouchPolicy.apply(view, true)
        assertTrue(view.filterTouchesWhenObscured)
        ObscuredTouchPolicy.apply(view, false)
        assertFalse(view.filterTouchesWhenObscured)
    }

    @Test
    fun turningTheSwitchOffIsKeptInTheViewModel() {
        val viewModel = RobotViewModel(context, EmptyAuthStore)
        viewModel.setFilterObscuredTouches(false)
        assertFalse(viewModel.filterObscuredTouches.value)
        waitUntil("off on disk") { storedFilter() == false }
        // Off and on many times in a row: the choice on disk is the last one made. The writes go in order, so once a later
        // choice of another switch is on disk, every earlier one is too.
        repeat(50) {
            viewModel.setFilterObscuredTouches(false)
            viewModel.setFilterObscuredTouches(true)
        }
        assertTrue(viewModel.filterObscuredTouches.value)
        val marker = !RobotCommandContract.PAIRING_ENABLED_BY_DEFAULT
        viewModel.setPairingEnabled(marker)
        waitUntil("the last write") { runBlocking { prefs.loadFlag(ConnectionPrefs.Flags.PAIRING, !marker) } == marker }
        assertEquals(true, storedFilter())
    }
}
