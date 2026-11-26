// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/TotpScreenRenderTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.app.Application
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.R
import com.hydraumc.control.network.AuthStore
import com.hydraumc.control.network.TotpEndpoint
import com.hydraumc.control.network.UserProfile
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.viewmodel.TotpFlow
import hydra.umc.client.HydraSuccess
import hydra.umc.client.HydraTotpConfirmed
import hydra.umc.client.HydraTotpEnrolment
import hydra.umc.client.HydraTotpStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The second-factor screen and the code field of the sign-in compose with a fake server and show the words a person needs; the key stays hidden until asked for. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class TotpScreenRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val key = "JBSWY3DPEHPK3PXP"

    private class Fake(val enabled: Boolean, val left: Long, val key: String) : TotpEndpoint {
        override suspend fun totpStatus() = HydraTotpStatus(enabled, left)
        override suspend fun totpBegin(password: String) = HydraTotpEnrolment(true, key, "otpauth://totp/HYDRA:ana?secret=$key")
        override suspend fun totpConfirm(code: String) = HydraTotpConfirmed(true, listOf("0a1b2-c3d4e"))
        override suspend fun totpDisable(password: String, code: String) = HydraSuccess(true)
        override suspend fun totpNewRecoveryCodes(password: String, code: String) = HydraTotpConfirmed(true, listOf("99999-88888"))
    }

    private fun text(id: Int, vararg args: Any): String = ApplicationProvider.getApplicationContext<Application>().getString(id, *args)

    private fun shown(text: String): Boolean = composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun flowOf(endpoint: TotpEndpoint) = TotpFlow(CoroutineScope(Dispatchers.Main)) { endpoint }

    @Test
    fun anAccountWithTheSecondFactorOffOffersToTurnItOn() {
        composeTestRule.setContent { TotpSection(flowOf(Fake(false, 0, key))) }
        composeTestRule.waitForIdle()
        assertTrue(shown(text(R.string.totp_title)))
        assertTrue(shown(text(R.string.totp_status_off)))
        assertTrue(shown(text(R.string.totp_start)))
        assertTrue(shown(text(R.string.totp_block_captures_title)))
    }

    @Test
    fun fewRecoveryCodesLeftShowsTheWarningAndTheButtonForNewOnes() {
        composeTestRule.setContent { TotpSection(flowOf(Fake(true, 1, key))) }
        composeTestRule.waitForIdle()
        assertTrue(shown(text(R.string.totp_status_on, 1)))
        assertTrue(shown(text(R.string.totp_low_warning, 1)))
        assertTrue(shown(text(R.string.totp_new_codes)))
        assertTrue(shown(text(R.string.totp_turn_off)))
    }

    @Test
    fun plentyOfCodesShowsNoWarning() {
        composeTestRule.setContent { TotpSection(flowOf(Fake(true, 8, key))) }
        composeTestRule.waitForIdle()
        assertTrue(shown(text(R.string.totp_status_on, 8)))
        assertFalse(shown(text(R.string.totp_low_warning, 8)))
    }

    @Test
    fun theKeyIsHiddenUntilThePersonAsksToSeeIt() {
        composeTestRule.setContent { TotpSection(flowOf(Fake(false, 0, key))) }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("pw")
        composeTestRule.onNodeWithText(text(R.string.totp_start)).performClick()
        composeTestRule.waitForIdle()

        assertTrue(shown(text(R.string.totp_key_show)))
        assertFalse(shown("JBSW Y3DP EHPK 3PXP"))
        composeTestRule.onNodeWithText(text(R.string.totp_key_show)).performClick()
        composeTestRule.waitForIdle()
        assertTrue(shown("JBSW Y3DP EHPK 3PXP"))
        assertTrue(shown(text(R.string.totp_key_hide)))
    }

    @Test
    fun theSignInAsksForTheCodeOnlyWhenTheServerSaidSo() {
        val store = object : AuthStore {
            override suspend fun loadAuth() = UserProfile("", "", "", rememberMe = false, isLoggedIn = false)
            override suspend fun saveAuth(profile: UserProfile) {}
        }
        val model = RobotViewModel(ApplicationProvider.getApplicationContext<Application>(), store)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.setContent { LoginScreen(model) }
        composeTestRule.waitForIdle()
        assertFalse(shown(text(R.string.login_code_label)))

        model.loginNeedsCode.value = true
        composeTestRule.waitForIdle()
        assertTrue(shown(text(R.string.login_code_label)))
    }
}
