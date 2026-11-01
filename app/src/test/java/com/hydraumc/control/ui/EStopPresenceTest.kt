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
import com.hydraumc.control.network.StopAllStatus
import com.hydraumc.control.viewmodel.RobotState
import com.hydraumc.control.viewmodel.RobotViewModel
import kotlinx.coroutines.test.runTest
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

    // The view model loads the stored login as soon as it exists; with no Android keystore on the JVM that load fails on a
    // background thread, and the Compose test rule would report it as an error before the test. It is collected here instead.
    private fun viewModel(): RobotViewModel {
        val model = RobotViewModel(ApplicationProvider.getApplicationContext<Application>())
        // The failure arrives some seconds later: collect until none has come for a while.
        val started = System.currentTimeMillis()
        var lastFailure = started
        while (System.currentTimeMillis() - started < 90_000L) {
            // The start-up work of the view model runs on the main looper, which the test lets go round here.
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(500)
            if (runCatching { runTest { } }.isFailure) lastFailure = System.currentTimeMillis()
            if (System.currentTimeMillis() - lastFailure >= 4_000L) break
        }
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
}
