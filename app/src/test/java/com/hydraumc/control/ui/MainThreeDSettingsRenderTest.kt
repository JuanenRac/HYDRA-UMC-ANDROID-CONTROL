// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/MainThreeDSettingsRenderTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.MainActivity
import com.hydraumc.control.MainScreen
import com.hydraumc.control.R
import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.UserProfile
import com.hydraumc.control.viewmodel.AppUpdateViewModel
import com.hydraumc.control.viewmodel.RobotViewModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The main scaffold, the 3D screen, the settings and the activity, drawn with a view model that has no server. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class MainThreeDSettingsRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val emptyStore = object : AuthStore {
        override suspend fun loadAuth() = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)
        override suspend fun saveAuth(profile: UserProfile) {}
    }

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun viewModel(): RobotViewModel {
        val model = RobotViewModel(app, emptyStore)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        return model
    }

    private fun assertDrawn() {
        assertTrue(composeTestRule.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun theMainScaffoldDrawsItsNavigationWithoutAServer() {
        val model = viewModel()
        val updates = AppUpdateViewModel(app)
        composeTestRule.setContent { MainScreen(model, updates) }
        composeTestRule.waitForIdle()
        assertDrawn()
        assertTrue(composeTestRule.onAllNodesWithText(app.getString(R.string.dashboard_title)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun theThreeDScreenDrawsWithoutARobot() {
        val model = viewModel()
        composeTestRule.setContent { ThreeDScreen(model) }
        composeTestRule.waitForIdle()
        assertDrawn()
    }

    @Test
    fun theSettingsScreenKeepsDrawingAfterTheSwitchesChange() {
        val model = viewModel()
        val updates = AppUpdateViewModel(app)
        composeTestRule.setContent { SettingsScreen(model, updates) }
        composeTestRule.waitForIdle()
        model.setDeadManEnabled(true)
        model.setLinkBlockOnBadLink(true)
        model.setLinkIndicator(true)
        model.setConfirmPlay(true)
        composeTestRule.waitForIdle()
        assertDrawn()
    }

    @Test
    fun theActivityStartsAndSurvivesAGlobalStopFromAnotherApp() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, Intent(app, MainActivity::class.java)).create().start().resume()
        assertNotNull(controller.get())
        controller.newIntent(Intent("ACTION_GLOBAL_ESTOP"))
        controller.pause().stop().destroy()
    }
}
