// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - PLAY confirmation rules tests
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayConfirmationTest {
    @Test fun `PLAY asks first when the option is on and the robot is not playing`() {
        assertTrue(PlayConfirmation.needed(askBeforePlay = true, robotIsPlaying = false))
    }

    @Test fun `PLAY does not ask when the person turned the option off`() {
        assertFalse(PlayConfirmation.needed(askBeforePlay = false, robotIsPlaying = false))
    }

    @Test fun `a robot that is already playing has no new trajectory to confirm`() {
        assertFalse(PlayConfirmation.needed(askBeforePlay = true, robotIsPlaying = true))
        assertFalse(PlayConfirmation.needed(askBeforePlay = false, robotIsPlaying = true))
    }

    @Test fun `the speed is shown as a whole percent and a damaged value is 0`() {
        assertEquals(250, PlayConfirmation.speedPercent(250.0))
        assertEquals(13, PlayConfirmation.speedPercent(12.5))
        assertEquals(0, PlayConfirmation.speedPercent(Double.NaN))
        assertEquals(0, PlayConfirmation.speedPercent(Double.POSITIVE_INFINITY))
        assertEquals(0, PlayConfirmation.speedPercent(-5.0))
    }
}
