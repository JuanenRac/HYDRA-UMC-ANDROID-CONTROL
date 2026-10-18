// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/ui/JoystickHoldTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * A button held down keeps repeating its jog every 150 ms from a coroutine
 * that was started by the first touch. That coroutine used to keep calling the
 * lambda it captured then: change the jog step (or switch from the arm to the
 * XY table) while a button is held and the robot kept moving by the OLD step
 * or on the OLD target. The repeat must always call the current lambda.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JoystickHoldTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a held XY button repeats with the lambda of the latest composition`() {
        var step by mutableIntStateOf(1)
        val seenSteps = mutableListOf<Int>()

        composeRule.setContent {
            // The lambda reads `step` when it runs: it is a NEW lambda each time `step` changes.
            val currentStep = step
            JoystickXYPad(onJog = { _, _, _ -> seenSteps.add(currentStep) })
        }

        composeRule.onNodeWithTag("jog_1_0_0").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.runOnIdle { step = 50 }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithTag("jog_1_0_0").performTouchInput { up() }

        assertTrue("the button never repeated", seenSteps.size >= 3)
        assertEquals("the first tick uses the first step", 1, seenSteps.first())
        assertEquals("after the change, every tick uses the new step", 50, seenSteps.last())
    }
}
