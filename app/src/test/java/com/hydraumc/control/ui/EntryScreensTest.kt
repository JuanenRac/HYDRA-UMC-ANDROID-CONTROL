// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/EntryScreensTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.R
import com.hydraumc.control.model.ServerInfo
import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.UserProfile
import com.hydraumc.control.util.DiagnosticReport
import com.hydraumc.control.util.FirstRun
import com.hydraumc.control.viewmodel.RobotViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The first use of the app: the welcome, the search, the notices (with the STOP) and what the diagnostic report says. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-xhdpi")
class EntryScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private val emptyStore = object : AuthStore {
        override suspend fun loadAuth() = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)
        override suspend fun saveAuth(profile: UserProfile) {}
    }

    @Test
    fun theWelcomeShowsOnlyUntilSomebodyGetsIn() {
        assertTrue(FirstRun.isFirst(app, ""))
        assertFalse("a user name kept from an earlier version is not a first use", FirstRun.isFirst(app, "operator"))
        FirstRun.markSeen(app)
        assertFalse(FirstRun.isFirst(app, ""))
    }

    @Test
    fun choosingAFoundServerHandsItBack() {
        var chosen: ServerInfo? = null
        val server = ServerInfo(host = "192.168.1.50", hostname = "example-cell")
        composeTestRule.setContent { DiscoveryScreen(listOf(server), false, { chosen = it }, {}, {}) }
        composeTestRule.onNodeWithText(app.getString(R.string.discovery_choose)).performClick()
        composeTestRule.waitForIdle()
        assertEquals(server, chosen)
    }

    @Test
    fun theNoticesStepCarriesTheStopAndLeavesTheSwitchesAsTheyAre() {
        val model = RobotViewModel(app, emptyStore)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val before = model.notificationsEnabled.value to model.watchAlertsEnabled.value
        var done = false
        composeTestRule.setContent { NoticesStep(model) { done = true } }
        composeTestRule.onNodeWithContentDescription(app.getString(R.string.stop_all_description)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("notices-start").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(done)
        assertEquals(before, model.notificationsEnabled.value to model.watchAlertsEnabled.value)
    }

    @Test
    fun theDiagnosticReportDescribesTheAppAndThePhoneOnly() {
        val report = DiagnosticReport.render("1.2.3", 7, "14", 34, "Maker Phone", "es-ES", true)
        assertEquals(
            listOf("HYDRA-UMC CONTROL (Android)", "app: 1.2.3 (7)", "android: 14 (API 34)", "device: Maker Phone", "language: es-ES", "notifications allowed: yes"),
            report.trim().lines(),
        )
    }
}
