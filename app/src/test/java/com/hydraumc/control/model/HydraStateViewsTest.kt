// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/model/HydraStateViewsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The thin views over the state the server sends: what an answer that says nothing reads as, the module flags, the
 * manufacturer of each model, the playback markers the setters write, the tool changer, the controllers, the jobs and the
 * description of a server found on the network. The views never throw on a payload that lacks a field.
 */
@RunWith(RobolectricTestRunner::class)
class HydraStateViewsTest {
    private fun robot(json: String = "{}") = RobotView(JSONObject(json))

    @Test
    fun anEmptyRobotReadsAsOfflineAndGeneric() {
        val r = robot()
        assertEquals(0, r.id)
        assertFalse(r.online)
        assertEquals("Generic", r.manufacturer)
        assertFalse(r.hasXYTable)
        assertFalse(r.hasPnP)
        assertFalse(r.hasCNC)
        assertFalse(r.hasLaser)
        assertFalse(r.hasHeatedBed)
        assertFalse(r.hasVacuumTable)
        assertFalse(r.hasCamera)
        assertFalse(r.hasAtc)
        assertFalse(r.hasRack)
        assertTrue(r.isEnabled)
        assertEquals(100.0, r.speed, 0.0)
        assertEquals(100.0, r.acceleration, 0.0)
        assertTrue(r.atcTools.isEmpty())
    }

    @Test
    fun theManufacturerFollowsTheModel() {
        val expected = mapOf(
            "Parol6" to "Source Robotics",
            "Faze4" to "Source Robotics",
            "AR3" to "Annin Robotics",
            "AR4" to "Annin Robotics",
            "UR5e" to "Universal Robots",
            "xArm 6" to "UFACTORY",
            "Lite 6" to "UFACTORY",
            "Gen3" to "Kinova",
            "Gen2" to "Kinova",
            "ViperX 300" to "Trossen Robotics",
            "WidowX" to "Trossen Robotics",
            "e.DO" to "Comau",
            "Z1" to "Unitree",
            "PiPER" to "AgileX",
            "Something else" to "Generic",
        )
        for ((model, maker) in expected) {
            assertEquals(model, maker, robot("""{"model": "$model"}""").manufacturer)
        }
    }

    @Test
    fun aModuleCountsOnlyWhenItsOwnFlagIsOn() {
        val on = robot(
            """{"juanenPnP":{"enabled":true},"juanenCNC":{"enabled":true},"juanenLaser":{"enabled":true},
                "heatedBed":{"enabled":true},"vacuumTable":{"enabled":true},"rackSystem":{"enabled":true},"atc":{}}""",
        )
        assertTrue(on.hasPnP && on.hasCNC && on.hasLaser && on.hasHeatedBed && on.hasVacuumTable && on.hasRack && on.hasAtc)
        val off = robot("""{"juanenPnP":{"enabled":false},"juanenCNC":{},"heatedBed":"yes"}""")
        assertFalse(off.hasPnP)
        assertFalse(off.hasCNC)
        assertFalse(off.hasHeatedBed)
        assertTrue(robot("""{"lumenPnP":{"enabled":true}}""").hasPnP)
    }

    @Test
    fun theSettersWriteIntoTheRawPayload() {
        val r = robot()
        r.setOnline(true)
        r.setTool("Gripper")
        r.setJogStep(2.5)
        r.setValve(1, true)
        r.setPump(0, true)
        r.setJoint("j3", 45.0)
        r.setPosAxis("x", 12.5)
        r.setXyTableAxis("x", 3.0)
        r.setEnabled(false)
        assertTrue(r.online)
        assertEquals("Gripper", r.tool)
        assertEquals(2.5, r.raw.getDouble("jogStep"), 0.0)
        assertEquals(true, r.valves.get(1))
        assertEquals(true, r.pumps.get(0))
        assertEquals(45.0, r.joints.getDouble("j3"), 0.0)
        assertEquals(12.5, r.posAxis("x"), 0.0)
        assertEquals(0.0, r.posAxis("y"), 0.0)
        assertEquals(3.0, r.xyTablePos.getDouble("x"), 0.0)
        assertFalse(r.isEnabled)
        assertSame(r.valves, r.valves)
    }

    @Test
    fun startingPlaybackClearsThePauseAndTheFinishedMarkers() {
        val r = robot("""{"playbackState":{"isPaused":true,"paused":true,"isFinished":true,"finished":true}}""")
        r.setPlaying(true)
        assertTrue(r.isPlaying)
        assertFalse(r.isPaused)
        assertFalse(r.isFinished)
        assertEquals(0, r.playbackState.getInt("activeStep"))
        assertFalse(r.playbackState.getBoolean("requestStop"))
    }

    @Test
    fun stoppingPlaybackAsksTheBrowserToStopToo() {
        val r = robot()
        r.setPlaying(true)
        r.setPaused(true)
        assertTrue(r.isPaused)
        assertTrue(r.playbackState.getBoolean("requestPause"))
        r.stop()
        assertFalse(r.isPlaying)
        assertFalse(r.isPaused)
        assertTrue(r.playbackState.getBoolean("requestStop"))
        assertEquals(-1, r.playbackState.getInt("activeStep"))
        r.setPlaying(false)
        assertFalse(r.isFinished)
        assertEquals(-1, r.playbackState.getInt("activeStep"))
    }

    @Test
    fun theTogglePausesAndResumesWithoutLeavingPlayback() {
        val r = robot()
        r.togglePaused()
        assertTrue(r.isPaused)
        assertTrue(r.isPlaying)
        r.togglePaused()
        assertFalse(r.isPaused)
        assertTrue(r.isPlaying)
    }

    @Test
    fun speedAndAccelerationAreKeptInThePlaybackState() {
        val r = robot()
        r.setSpeed(40.0)
        r.setAcceleration(25.0)
        assertEquals(40.0, r.speed, 0.0)
        assertEquals(25.0, r.acceleration, 0.0)
    }

    @Test
    fun anOldAliasOfPlaybackIsStillRead() {
        val r = robot("""{"playbackState":{"playing":true,"paused":true,"finished":true}}""")
        assertTrue(r.isPlaying)
        assertTrue(r.isPaused)
        assertTrue(r.isFinished)
    }

    @Test
    fun theToolChangerListsItsSlotsAndSkipsWhatIsNotAnObject() {
        val r = robot("""{"atc":{"tools":[{"slot":2,"tool":"Drill"},7,{"tool":"Pen"}]}}""")
        assertEquals(listOf(RobotView.AtcTool(2, "Drill"), RobotView.AtcTool(0, "Pen")), r.atcTools)
        assertTrue(robot("""{"atc":{}}""").atcTools.isEmpty())
    }

    @Test
    fun theTextOfARobotNamesItsIdentity() {
        assertEquals("RobotView(id=4, name=Arm, online=true)", robot("""{"id":4,"name":"Arm","online":true}""").toString())
    }

    @Test
    fun aControllerSetsTheAxesOfItsKinematicBrainStage() {
        val c = ControllerView(JSONObject("""{"id":"c1","robots":[{"id":1},{"id":2},"x"]}"""))
        assertEquals("c1", c.id)
        assertEquals("c1", c.name)
        assertEquals("", c.ip)
        c.setKbXyTableAxis("x", 1.0)
        c.setKbXyTableAxis("y", 2.0)
        c.setKbXyTableAxis("z", 3.0)
        c.setKbXyTableAxis("w", 9.0)
        assertEquals(1.0, c.kbXyTable.getDouble("x"), 0.0)
        assertEquals(2.0, c.kbXyTable.getDouble("y1"), 0.0)
        assertEquals(2.0, c.kbXyTable.getDouble("y2"), 0.0)
        assertEquals(3.0, c.kbXyTable.getDouble("z"), 0.0)
        assertFalse(c.kbXyTable.has("w"))
        assertEquals(2, c.robots.size)
        assertEquals(2, c.robotById(2)?.id)
        assertNull(c.robotById(9))
        assertTrue(ControllerView(JSONObject()).robots.isEmpty())
    }

    @Test
    fun theStateFlattensRobotsAndJobsAcrossControllers() {
        val state = HydraState(
            JSONObject(
                """{"activeControllerId":"b","controllers":[{"id":"a","robots":[{"id":1}]},{"id":"b","robots":[{"id":2},{"id":3}]}],
                    "jobs":[{"name":"cut.nc","size":120,"lastModified":"t"},{}, 5]}""",
            ),
        )
        assertEquals("b", state.activeControllerId)
        assertEquals(listOf(1, 2, 3), state.allRobots.map { it.id })
        assertEquals(3, state.robotById(3)?.id)
        assertNull(state.robotById(8))
        assertEquals(2, state.allJobs.size)
        assertEquals("cut.nc", state.allJobs[0].name)
        assertEquals(120L, state.allJobs[0].size)
        assertEquals("t", state.allJobs[0].lastModified)
        assertEquals(0L, state.allJobs[1].size)
        assertEquals("", state.allJobs[1].lastModified)
        assertSame(state.raw, state.toJson())
    }

    @Test
    fun theEmptyStateHasNoControllersAndNoJobs() {
        val state = HydraState.empty()
        assertTrue(state.controllers.isEmpty())
        assertTrue(state.allRobots.isEmpty())
        assertTrue(state.allJobs.isEmpty())
        assertEquals("", state.activeControllerId)
        assertTrue(state.raw.has("settings"))
        assertEquals(0, state.raw.getJSONArray("controllers").length())
        assertTrue(HydraState(JSONObject().put("controllers", JSONArray())).controllers.isEmpty())
    }

    @Test
    fun aServerFoundOnTheNetworkIsDescribedFromItsAnswer() {
        val info = ServerInfo.fromHydraInfo(
            "192.168.0.10",
            3100,
            JSONObject(
                """{"product":"HYDRA-UMC","remoteApiVersion":2,"appVersion":"1.2.3","hostname":"bench",
                    "controllerCount":2,"robotCount":5,"uptimeSeconds":90}""",
            ),
        )
        assertEquals("http://192.168.0.10:3100", info.baseUrl)
        assertEquals("ws://192.168.0.10:3100/ws", info.wsUrl)
        assertEquals("bench", info.displayName)
        assertEquals(2, info.remoteApiVersion)
        assertEquals(5, info.robotCount)
        assertEquals(90, info.uptimeSeconds)
        val bare = ServerInfo.fromHydraInfo("10.0.0.2", 3000, JSONObject())
        assertEquals("10.0.0.2", bare.displayName)
        assertEquals("", bare.product)
        assertEquals(0, bare.controllerCount)
    }
}
