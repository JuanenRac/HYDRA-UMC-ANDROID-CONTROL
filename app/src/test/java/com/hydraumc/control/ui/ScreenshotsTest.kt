// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/ScreenshotsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.hydraumc.control.MainScreen
import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.UserProfile
import com.hydraumc.control.network.WsStatus
import com.hydraumc.control.ui.theme.HydraColors
import com.hydraumc.control.ui.theme.HydraTheme
import com.hydraumc.control.viewmodel.AppUpdateViewModel
import com.hydraumc.control.viewmodel.RobotState
import com.hydraumc.control.viewmodel.RobotViewModel
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pictures of the screens with invented data, into app/build/screenshots. Only the screenshots workflow asks for them
 * (HYDRA_UMC_SCREENSHOTS); in every other run these tests are skipped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w400dp-h860dp-xhdpi")
class ScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val emptyStore = object : AuthStore {
        override suspend fun loadAuth() = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)
        override suspend fun saveAuth(profile: UserProfile) {}
    }

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun onlyWhenAsked() {
        assumeTrue(System.getenv("HYDRA_UMC_SCREENSHOTS") != null)
        System.setProperty("roborazzi.test.record", "true")
    }

    private fun robot(id: Int, name: String, role: String, online: Boolean) = RobotState(
        id = id, name = name, model = "Parol6", manufacturer = "Example", role = role, online = online, isPlaying = false, isPaused = false,
        activeStep = -1, hasXYTable = false, hasAtc = false, hasCamera = true, hasPnP = true, hasCNC = false, hasLaser = false,
        hasHeatedBed = false, hasVacuumTable = false, hasRack = false, combinedWith = emptyList(), currentTool = "", atcTools = emptyList(),
        speed = 100.0, acceleration = 100.0, posX = 142.0, posY = 88.0, posZ = 210.0, xyPosX = 0.0, xyPosY = 0.0,
        j1 = 0.0, j2 = 0.0, j3 = 0.0, j4 = 0.0, j5 = 0.0, j6 = 0.0, endstops = emptyMap(), valves = emptyList(), pumps = emptyList(),
        recordedPointsCount = 0, jogStep = 1.0,
    )

    private fun viewModel(): RobotViewModel {
        val model = RobotViewModel(app, emptyStore)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        model.robots.value = listOf(robot(1, "Robot A1", "Pick & Place", true), robot(2, "Robot A3", "Inspection", false))
        model.selectedRobotId.value = 1
        model.connectionState.value = WsStatus.CONNECTED
        model.ipAddress.value = "192.168.1.50"
        return model
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            HydraTheme { Box(Modifier.fillMaxSize().background(HydraColors.Background)) { content() } }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun signIn() {
        val model = viewModel()
        shot("01_sign_in") { LoginScreen(model) }
    }

    @Test
    fun home() {
        val model = viewModel()
        val updates = AppUpdateViewModel(app)
        shot("03_home") { MainScreen(model, updates) }
    }

    @Test
    fun control() {
        val model = viewModel()
        shot("04_control") { ControlScreen(model) }
    }
}
