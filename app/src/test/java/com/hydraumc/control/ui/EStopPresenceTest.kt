// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/EStopPresenceTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.R
import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.StopAllStatus
import com.hydraumc.control.network.UserProfile
import com.hydraumc.control.viewmodel.RobotState
import com.hydraumc.control.viewmodel.RobotViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The emergency stop is on screen where robots are driven, and one tap reaches the global stop of the view model. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class EStopPresenceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // The stored login is a fake that holds nothing, so the view model starts at once on the JVM (the encrypted store needs the Android keystore, which
    // this runner does not have, and used to make the view model wait for its failure).
    private val emptyStore = object : AuthStore {
        override suspend fun loadAuth() = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)
        override suspend fun saveAuth(profile: UserProfile) {}
    }

    private fun viewModel(): RobotViewModel {
        val model = RobotViewModel(ApplicationProvider.getApplicationContext<Application>(), emptyStore)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        return model
    }

    private fun aRobot() = RobotState(
        id = 1, name = "A", model = "Parol6", manufacturer = "", role = "", online = true, isPlaying = false, isPaused = false,
        activeStep = -1, hasXYTable = false, hasAtc = false, hasCamera = false, hasPnP = false, hasCNC = false, hasLaser = false,
        hasHeatedBed = false, hasVacuumTable = false, hasRack = false, combinedWith = emptyList(), currentTool = "", atcTools = emptyList(),
        speed = 100.0, acceleration = 100.0, posX = 0.0, posY = 0.0, posZ = 0.0, xyPosX = 0.0, xyPosY = 0.0,
        j1 = 0.0, j2 = 0.0, j3 = 0.0, j4 = 0.0, j5 = 0.0, j6 = 0.0, endstops = emptyMap(), valves = emptyList(), pumps = emptyList(),
        recordedPointsCount = 0, jogStep = 1.0,
    )

    private fun stopDescription(): String =
        ApplicationProvider.getApplicationContext<Application>().getString(R.string.stop_all_description)

    @Test
    fun theStopButtonIsDisplayedAndOneTapStartsTheGlobalStop() {
        val model = viewModel()
        composeTestRule.setContent { EStopButton(model) }

        composeTestRule.onNodeWithContentDescription(stopDescription()).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(stopDescription()).performClick()
        composeTestRule.waitForIdle()

        // No server is connected here: the stop must say so instead of looking done.
        assertTrue(model.stopAllStatus.value is StopAllStatus.NotConfirmed)
    }

    @Test
    fun thePlaybackConsoleCarriesTheStopButton() {
        val model = viewModel()
        composeTestRule.setContent { PlaybackConsole(model, aRobot()) }

        composeTestRule.onNodeWithContentDescription(stopDescription()).assertExists()
    }

    @Test
    fun theControlScreenCarriesTheStopButtonOnceARobotIsSelected() {
        val model = viewModel()
        model.robots.value = listOf(aRobot())
        model.selectedRobotId.value = 1
        composeTestRule.setContent { ControlScreen(model) }

        composeTestRule.onNodeWithContentDescription(stopDescription()).assertExists()
    }
}
