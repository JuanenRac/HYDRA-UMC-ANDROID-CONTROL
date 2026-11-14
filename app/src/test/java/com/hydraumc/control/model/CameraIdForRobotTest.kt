// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/model/CameraIdForRobotTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `org.json` of a JVM unit test is a stub that throws; the runner of the other tests of the app gives it the real one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraIdForRobotTest {
    private fun state(vararg controllers: JSONObject): HydraState =
        HydraState(JSONObject().put("controllers", JSONArray().also { array -> controllers.forEach { array.put(it) } }))

    private fun controller(vararg cameras: Pair<Int, Int?>): JSONObject =
        JSONObject()
            .put("id", "c1")
            .put("robots", JSONArray())
            .put(
                "cameras",
                JSONArray().also { array ->
                    cameras.forEach { (id, robot) ->
                        array.put(JSONObject().put("id", id).also { camera -> if (robot != null) camera.put("assignedRobotId", robot) })
                    }
                },
            )

    @Test
    fun theCameraAssignedToTheRobotIsUsed() {
        assertEquals(3, state(controller(1 to 7, 3 to 2)).cameraIdForRobot(2))
    }

    @Test
    fun withoutAnAssignmentTheRobotIdIsTheCameraId() {
        assertEquals(2, state(controller(1 to null, 2 to null)).cameraIdForRobot(2))
        assertEquals(5, HydraState.empty().cameraIdForRobot(5))
    }

    @Test
    fun aRobotNoCameraNamesKeepsItsOwnId() {
        assertEquals(9, state(controller(1 to 1, 2 to 2)).cameraIdForRobot(9))
    }

    @Test
    fun theLowestCameraWinsWhenTwoNameTheSameRobot() {
        assertEquals(2, state(controller(4 to 6, 2 to 6)).cameraIdForRobot(6))
    }

    @Test
    fun camerasOfAnotherControllerAreFoundToo() {
        assertEquals(8, state(controller(1 to 1), controller(8 to 4)).cameraIdForRobot(4))
    }

    @Test
    fun aCameraWithoutAnIdOrWithAnEmptyListIsSkipped() {
        val broken = JSONObject().put("id", "c1").put("cameras", JSONArray().put(JSONObject().put("assignedRobotId", 3)).put("nope"))
        assertEquals(3, state(broken, JSONObject().put("id", "c2")).cameraIdForRobot(3))
    }
}
