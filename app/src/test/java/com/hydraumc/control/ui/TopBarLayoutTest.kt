// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/TopBarLayoutTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
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

/**
 * The header on a narrow phone, in Spanish and with a very long server name: the STOP, the voice and the account buttons
 * stay whole on the screen; the server name is the text that gives way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w320dp-h640dp-xhdpi")
class TopBarLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val emptyStore = object : AuthStore {
        override suspend fun loadAuth() = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)
        override suspend fun saveAuth(profile: UserProfile) {}
    }

    @Test
    fun aLongServerNameNeverPushesAButtonOffTheScreen() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val model = RobotViewModel(app, emptyStore)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        model.ipAddress.value = "un-nombre-de-servidor-extremadamente-largo-y-descriptivo.example"
        composeTestRule.setContent { HydraTopBar(model, onVoice = {}, onProfile = {}) }
        composeTestRule.waitForIdle()

        val width = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot.right
        for (id in listOf(R.string.stop_all_description, R.string.voice_assistant_title, R.string.help_title, R.string.user_profile_title)) {
            val node = composeTestRule.onNodeWithContentDescription(app.getString(id))
            node.assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("${app.getString(id)} ends at ${bounds.right}, the screen at $width", bounds.right <= width && bounds.width > 0)
        }
    }
}
