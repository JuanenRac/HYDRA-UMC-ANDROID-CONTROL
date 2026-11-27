// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/HelpScreenTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-xhdpi")
class HelpScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theLevelChosenShowsItsText() {
        composeTestRule.setContent { HelpScreen(HelpTopic.CONTROL) }
        composeTestRule.onNodeWithText(app.getString(HelpTopic.CONTROL.basic)).assertExists()
        composeTestRule.onNodeWithTag("help-level-TECHNICAL").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(app.getString(HelpTopic.CONTROL.technical)).assertExists()
        composeTestRule.onNodeWithText(app.getString(HelpTopic.CONTROL.basic)).assertDoesNotExist()
    }

    @Test
    fun theSearchFindsATopicByAWordOfItsText() {
        val text = { id: Int -> app.getString(id) }
        assertEquals(HelpTopic.entries.toList(), helpSearch("  ", text))
        assertTrue(HelpTopic.CAMERAS in helpSearch("MJPEG", text))
        assertEquals(emptyList<HelpTopic>(), helpSearch("zzzz-nothing", text))
    }
}
