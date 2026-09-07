// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Robot state synchronization contract tests
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.model

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Guards the state aliases shared by Android Control, Server and Studio.
 *
 * A1/A2 historically had a legacy camera.connected=true alongside the newer
 * visionEnabled=false. The command field is authoritative so a user can turn
 * the camera off reliably, while old servers still work through the fallback.
 */
@RunWith(RobolectricTestRunner::class)
class RobotViewContractTest {
    @Test
    fun `visionEnabled overrides stale legacy embedded camera state`() {
        val robot = RobotView(
            JSONObject()
                .put("id", 1)
                .put("visionEnabled", false)
                .put("camera", JSONObject().put("connected", true))
        )

        assertFalse(robot.hasCamera)
    }

    @Test
    fun `legacy embedded camera state remains supported without visionEnabled`() {
        val robot = RobotView(
            JSONObject()
                .put("id", 1)
                .put("camera", JSONObject().put("connected", true))
        )

        assertTrue(robot.hasCamera)
    }

    @Test
    fun `explicit pause synchronizes all playback aliases`() {
        val robot = RobotView(JSONObject().put("id", 1))

        robot.setPaused(true)

        assertTrue(robot.playbackState.getBoolean("isPaused"))
        assertTrue(robot.playbackState.getBoolean("paused"))
        assertTrue(robot.playbackState.getBoolean("requestPause"))
        assertTrue(robot.playbackState.getBoolean("isPlaying"))
        assertTrue(robot.playbackState.getBoolean("playing"))
    }
}
