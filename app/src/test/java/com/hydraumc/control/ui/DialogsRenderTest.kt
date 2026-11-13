// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/DialogsRenderTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.R
import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.ProtectionLowering
import com.hydraumc.control.network.ServerOptionsFailure
import com.hydraumc.control.network.UserProfile
import com.hydraumc.control.viewmodel.RobotViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The profile dialog, the voice assistant dialog and the server options section, each drawn with a view model that has no server,
 * and the words the options screen uses for a failure or a lowering of the protection: every kind has its own text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class DialogsRenderTest {

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
    fun theProfileDialogAsksForTheAccountFields() {
        val model = viewModel()
        composeTestRule.setContent { UserProfileDialog(model, onDismiss = {}) }
        composeTestRule.waitForIdle()

        assertShown(R.string.user_profile_title)
        assertShown(R.string.username_label)
        assertShown(R.string.password_label)
        assertShown(R.string.email_label)
    }

    @Test
    fun theVoiceAssistantDialogExplainsItselfAndOffersTheInput() {
        val model = viewModel()
        composeTestRule.setContent { VoiceAssistantDialog(model, onStartVoiceRecognition = {}, onDismiss = {}) }
        composeTestRule.waitForIdle()

        assertShown(R.string.voice_assistant_title)
        assertShown(R.string.voice_assistant_hint)
        assertShown(R.string.voice_assistant_input_label)
    }

    @Test
    fun theServerOptionsSectionNamesItselfBeforeTheServerAnswers() {
        val model = viewModel()
        composeTestRule.setContent { ServerOptionsSection(model) }
        composeTestRule.waitForIdle()

        assertShown(R.string.server_options_title)
        assertShown(R.string.server_options_intro)
        assertShown(R.string.connections_refresh)
    }

    @Test
    fun everyFailureOfTheOptionsHasItsOwnText() {
        val ids = ServerOptionsFailure.values().map { failureRes(it) }
        assertEquals(ServerOptionsFailure.values().size, ids.toSet().size)
        ids.forEach { assertTrue(text(it).isNotBlank()) }
    }

    @Test
    fun everyLoweringOfTheProtectionHasItsOwnQuestion() {
        val ids = ProtectionLowering.values().map { loweringRes(it) }
        assertEquals(ProtectionLowering.values().size, ids.toSet().size)
        ids.forEach { assertTrue(text(it).isNotBlank()) }
    }
}
