// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/util/WorkFilesTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import com.hydraumc.control.network.RobotCommandContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The work files (trajectories): the folder, the list, the points and when a work may be loaded into a robot. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkFilesTest {
    @Test
    fun theFolderIsTheConfiguredOneOrTheNameOfTheRobotWithoutSpaces() {
        assertEquals("WORKS/MyRobot", WorkFiles.folderFor(null, 1, "My Robot"))
        val configured = JSONObject().put("1", "data/jobs/arm1")
        assertEquals("data/jobs/arm1", WorkFiles.folderFor(configured, 1, "My Robot"))
        assertEquals("WORKS/Other", WorkFiles.folderFor(configured, 2, "Other"))
    }

    @Test
    fun aFolderThatLeavesTheServerIsRefused() {
        for (bad in listOf("../etc", "a/../b", "a//b", "C:\\x", "a\\b", "x/./y")) {
            assertNull(bad, WorkFiles.folderFor(JSONObject().put("1", bad), 1, "R"))
        }
        assertNull(WorkFiles.folderFor(JSONObject().put("1", "a".repeat(RobotCommandContract.WORKS_FOLDER_MAX + 1)), 1, "R"))
        assertEquals("abs/path", WorkFiles.folderFor(JSONObject().put("1", "/abs/path/"), 1, "R"))
    }

    @Test
    fun theIndexKeepsOnlySafeSortedNames() {
        val body = """["b.json", "a.json", "../x.json", "sub/c.json", "no-extension", 7, "a.json", ".hidden.json", "ok_1-2.json"]"""
        assertEquals(listOf("a.json", "b.json", "ok_1-2.json"), WorkFiles.parseIndex(body))
        assertNull(WorkFiles.parseIndex("{\"a\":1}"))
        assertNull(WorkFiles.parseIndex("not json"))
        assertEquals(emptyList<String>(), WorkFiles.parseIndex("[]"))
    }

    private fun joints(extra: String = "") = """{"j1":0,"j2":1,"j3":2,"j4":3,"j5":4,"j6":5$extra}"""

    @Test
    fun aListOfPointsIsReducedToTheFieldsTheServerKeeps() {
        val loaded = WorkFiles.parsePoints("""[${joints(""","x":1.5,"junk":"drop me","y":2,"z":3""")}, {"x":1,"y":2,"z":3}]""")
        loaded as WorkFiles.Loaded.Ok
        assertEquals(2, loaded.points.length())
        assertFalse(loaded.points.getJSONObject(0).has("junk"))
        assertEquals(1.5, loaded.points.getJSONObject(0).getDouble("x"), 0.0)
        assertFalse(loaded.usesTable)
    }

    @Test
    fun tablePointsAreReported() {
        val loaded = WorkFiles.parsePoints("""[${joints(""","tx":4,"ty":5""")}]""") as WorkFiles.Loaded.Ok
        assertTrue(loaded.usesTable)
    }

    @Test
    fun badFilesAreRefusedWithTheirReason() {
        fun problem(body: String) = (WorkFiles.parsePoints(body) as WorkFiles.Loaded.Refused)
        assertEquals(WorkFiles.Problem.NOT_JSON, problem("nope").problem)
        assertEquals(WorkFiles.Problem.NOT_A_LIST, problem("""{"points":[]}""").problem)
        assertEquals(WorkFiles.Problem.EMPTY, problem("[]").problem)
        assertEquals(WorkFiles.Problem.BAD_POINT, problem("""[{"x":1,"y":2}]""").problem)
        assertEquals(1, problem("""[${joints()}, 5]""").index)
        assertEquals(WorkFiles.Problem.BAD_POINT, problem("""[{"x":"1","y":2,"z":3}]""").problem)
        assertEquals(WorkFiles.Problem.BAD_POINT, problem("""[{"x":1,"y":2,"z":3,"motionType":"other"}]""").problem)
        assertEquals(WorkFiles.Problem.TOO_BIG, problem("[" + " ".repeat(RobotCommandContract.WORKS_FILE_MAX_BYTES) + "]").problem)
    }

    @Test
    fun moreThanTheMaximumOfPointsIsRefused() {
        val point = """{"x":1,"y":2,"z":3}"""
        val body = (0..RobotCommandContract.WORKS_MAX_POINTS).joinToString(",", "[", "]") { point }
        assertEquals(WorkFiles.Problem.TOO_MANY_POINTS, (WorkFiles.parsePoints(body) as WorkFiles.Loaded.Refused).problem)
    }

    @Test
    fun theParametersOfTheCommandCarryThePointsAndTheFileName() {
        val points = (WorkFiles.parsePoints("""[{"x":1,"y":2,"z":3}]""") as WorkFiles.Loaded.Ok).points
        val params = WorkFiles.trajectoryParams(points, "cut_1.json")
        assertEquals("cut_1.json", params.getString("selectedWorkFile"))
        assertEquals(1, params.getJSONArray("points").length())
        assertTrue(runCatching { WorkFiles.trajectoryParams(points, "../cut.json") }.isFailure)
    }

    private fun ready() = WorkFiles.Situation(
        connected = true, robotOnline = true, robotEnabled = true, robotPlaying = false,
        stopInProgress = false, role = "operator", tableMissing = false, serverSupports = true,
    )

    @Test
    fun aWorkIsNeverLoadedIntoARobotThatIsPlayingOrInSafeMode() {
        assertEquals(WorkFiles.Block.NONE, WorkFiles.blockOf(ready()))
        assertEquals(WorkFiles.Block.PLAYING, WorkFiles.blockOf(ready().copy(robotPlaying = true)))
        assertEquals(WorkFiles.Block.SAFE_MODE, WorkFiles.blockOf(ready().copy(robotEnabled = false)))
        assertEquals(WorkFiles.Block.OFFLINE, WorkFiles.blockOf(ready().copy(robotOnline = false)))
        assertEquals(WorkFiles.Block.NOT_CONNECTED, WorkFiles.blockOf(ready().copy(connected = false)))
        assertEquals(WorkFiles.Block.STOP_PENDING, WorkFiles.blockOf(ready().copy(stopInProgress = true)))
        assertEquals(WorkFiles.Block.ROLE, WorkFiles.blockOf(ready().copy(role = "viewer")))
        assertEquals(WorkFiles.Block.NO_TABLE, WorkFiles.blockOf(ready().copy(tableMissing = true)))
        assertEquals(WorkFiles.Block.NOT_SUPPORTED, WorkFiles.blockOf(ready().copy(serverSupports = false)))
    }
}
