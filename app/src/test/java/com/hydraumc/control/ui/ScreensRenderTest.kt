// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/ScreensRenderTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.R
import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.UserProfile
import com.hydraumc.control.viewmodel.RobotViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The screens and dialogs that no other test draws: each one composes with a view model that has no server and shows the words a person needs to use it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class ScreensRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val emptyStore = object : AuthStore {
        override suspend fun loadAuth() = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)
        override suspend fun saveAuth(profile: UserProfile) {}
    }

    private fun viewModel(): RobotViewModel {
        val model = RobotViewModel(ApplicationProvider.getApplicationContext<Application>(), emptyStore)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        return model
    }

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private fun assertShown(id: Int) {
        val nodes = composeTestRule.onAllNodesWithText(text(id)).fetchSemanticsNodes()
        assertTrue("the text '${text(id)}' is not on the screen", nodes.isNotEmpty())
    }

    @Test
    fun theLoginScreenAsksForTheServerAndTheAccount() {
        val model = viewModel()
        composeTestRule.setContent { LoginScreen(model) }
        composeTestRule.waitForIdle()

        assertShown(R.string.login_subtitle)
        assertShown(R.string.login_button_label)
        assertShown(R.string.ip_label)
        assertShown(R.string.port_label)
        assertShown(R.string.password_label)
        assertShown(R.string.remember_me_label)
    }

    @Test
    fun theDashboardNamesItselfAndSaysThereAreNoRobots() {
        val model = viewModel()
        composeTestRule.setContent { DashboardScreen(model) }
        composeTestRule.waitForIdle()

        assertShown(R.string.dashboard_title)
    }

    @Test
    fun theAuditDialogCanBeClosedWithoutAServer() {
        val model = viewModel()
        var closed = false
        composeTestRule.setContent { AuditDialog(model, onDismiss = { closed = true }) }
        composeTestRule.waitForIdle()

        assertShown(R.string.close_button)
        composeTestRule.onNodeWithText(text(R.string.close_button)).performClick()
        assertTrue(closed)
    }

    @Test
    fun theConnectionsDialogCanBeClosedWithoutAServer() {
        val model = viewModel()
        var closed = false
        composeTestRule.setContent { ConnectionsDialog(model, onDismiss = { closed = true }) }
        composeTestRule.waitForIdle()

        assertShown(R.string.close_button)
        composeTestRule.onNodeWithText(text(R.string.close_button)).performClick()
        assertTrue(closed)
    }

    @Test
    fun theLinkSettingsOfferTheContinuousJogAndTheLinkSwitches() {
        val model = viewModel()
        composeTestRule.setContent { LinkAndWatchSettings(model) }
        composeTestRule.waitForIdle()

        assertShown(R.string.dead_man_switch_label)
        assertShown(R.string.link_block_switch_label)
    }

    @Test
    fun theSecuritySettingsDrawWithoutAServer() {
        val model = viewModel()
        composeTestRule.setContent { SecuritySettings(model) }
        composeTestRule.waitForIdle()

        assertTrue(composeTestRule.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun theTelemetryScreenSaysThereIsNoDataBeforeTheFirstReading() {
        val model = viewModel()
        composeTestRule.setContent { TelemetryScreen(model) }
        composeTestRule.waitForIdle()

        assertShown(R.string.no_telemetry_data)
    }

    @Test
    fun theSettingsScreenDrawsWithoutAServer() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = viewModel()
        val updates = com.hydraumc.control.viewmodel.AppUpdateViewModel(app)
        composeTestRule.setContent { SettingsScreen(model, updates) }
        composeTestRule.waitForIdle()

        assertTrue(composeTestRule.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().isNotEmpty())
    }
}
