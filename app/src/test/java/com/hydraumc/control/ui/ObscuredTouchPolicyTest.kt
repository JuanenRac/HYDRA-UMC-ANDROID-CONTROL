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
import com.hydraumc.control.viewmodel.RobotViewModel
import org.junit.After
import org.junit.Assert.assertFalse
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
    @Before
    fun setUp() = MainDispatcherSwap.set()

    @After
    fun tearDown() = MainDispatcherSwap.reset()

    @Test
    fun theProtectionIsOnByDefault() {
        assertTrue(ObscuredTouchPolicy.ENABLED_BY_DEFAULT)
        assertTrue(RobotViewModel(ApplicationProvider.getApplicationContext<Application>(), EmptyAuthStore).filterObscuredTouches.value)
    }

    @Test
    fun aCoveredWindowDropsTheTouchOnlyWhileTheSwitchIsOn() {
        assertTrue(ObscuredTouchPolicy.drops(MotionEvent.FLAG_WINDOW_IS_OBSCURED, enabled = true))
        assertTrue(ObscuredTouchPolicy.drops(0x2, enabled = true))
        assertFalse(ObscuredTouchPolicy.drops(MotionEvent.FLAG_WINDOW_IS_OBSCURED, enabled = false))
        assertFalse(ObscuredTouchPolicy.drops(0, enabled = true))
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
        val viewModel = RobotViewModel(ApplicationProvider.getApplicationContext<Application>(), EmptyAuthStore)
        viewModel.setFilterObscuredTouches(false)
        assertFalse(viewModel.filterObscuredTouches.value)
        viewModel.setFilterObscuredTouches(true)
        assertTrue(viewModel.filterObscuredTouches.value)
    }
}
