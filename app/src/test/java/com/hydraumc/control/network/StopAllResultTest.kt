// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/StopAllResultTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the global STOP answer carries, read field by field. */
@RunWith(RobolectricTestRunner::class)
class StopAllResultTest {

    @Test
    fun `a clean answer is clean`() {
        val result = StopAllResult.fromJson(
            JSONObject(
                """{"success":true,"stoppedCount":2,"failedCount":0,"persisted":true,"robots":[
                {"robotId":1,"controllerId":"c1","name":"A","stopped":true,"valvesOff":true,"pumpsOff":true},
                {"robotId":2,"controllerId":"c1","name":"B","stopped":true,"valvesOff":true,"pumpsOff":true,"reservedBy":"alice"}]}""",
            ),
        )
        assertTrue(result.isClean)
        assertEquals("alice", result.robots[1].reservedBy)
        assertNull(result.robots[0].reservedBy)
        assertEquals(emptyList<Any>(), result.failedRobots)
    }

    @Test
    fun `a failed robot or an unsaved stop is not clean`() {
        val failed = StopAllResult.fromJson(
            JSONObject("""{"success":false,"stoppedCount":1,"failedCount":1,"persisted":true,"robots":[
                {"robotId":1,"stopped":true},{"robotId":2,"stopped":false,"error":"no link"}]}"""),
        )
        assertFalse(failed.isClean)
        assertEquals(listOf(2), failed.failedRobots.map { it.robotId })
        assertEquals("no link", failed.failedRobots.single().error)

        val unsaved = StopAllResult.fromJson(JSONObject("""{"success":true,"failedCount":0,"persisted":false,"robots":[]}"""))
        assertFalse(unsaved.isClean)
    }

    @Test
    fun `the counts are derived from the list when the server leaves them out`() {
        val result = StopAllResult.fromJson(JSONObject("""{"robots":[{"robotId":3,"stopped":false},{"robotId":4,"stopped":true}]}"""))
        assertEquals(1, result.failedCount)
        assertEquals(1, result.stoppedCount)
        assertFalse(result.success)
        assertFalse("a missing persisted flag is not taken as saved", result.persisted)
    }

    @Test
    fun `fields of the wrong type read as their defaults and never throw`() {
        val result = StopAllResult.fromJson(
            JSONObject(
                """{"success":"yes","stoppedCount":"many","failedCount":null,"persisted":1,"robots":[
                5, null, "x", [],
                {"robotId":"abc","name":null,"stopped":"true","reservedBy":null,"error":null},
                {"robotId":{"a":1},"controllerId":7,"name":["n"]}]}""",
            ),
        )
        assertFalse(result.success)
        assertEquals(2, result.robots.size)
        assertEquals(0, result.robots[0].robotId)
        assertEquals("", result.robots[0].name)
        assertNull(result.robots[0].reservedBy)
        assertNull(result.robots[0].error)
        assertFalse(result.isClean)
    }

    @Test
    fun `a robots field that is not a list reads as no robots`() {
        for (json in listOf("""{"robots":5}""", """{"robots":"x"}""", """{"robots":{"a":1}}""", """{"robots":null}""", "{}")) {
            val result = StopAllResult.fromJson(JSONObject(json))
            assertEquals(json, 0, result.robots.size)
            assertFalse(json, result.isClean)
        }
    }
}

/** What each answer of the server means to the app. */
class HydraApiExceptionFlagsTest {

    private fun e(code: Int?, serverCode: String? = null) = HydraApiException("x", httpCode = code, serverCode = serverCode)

    @Test
    fun `a 401 and a 403 about the token are session problems`() {
        assertTrue(e(401).isSessionProblem)
        assertTrue(e(403, "TOKEN_EXPIRED").isSessionProblem)
        assertTrue(e(403, "TOKEN_INVALID").isSessionProblem)
        assertFalse(e(403).isSessionProblem)
        assertFalse(e(403, "OTHER").isSessionProblem)
    }

    @Test
    fun `a 403 about the role is a permission problem, not a session problem`() {
        assertTrue(e(403).isPermissionDenied)
        assertFalse(e(403, "TOKEN_EXPIRED").isPermissionDenied)
        assertFalse(e(401).isPermissionDenied)
    }

    @Test
    fun `the other statuses have their own flag`() {
        assertTrue(e(409).isReservedByAnother)
        assertTrue(e(400).isRejectedCommand)
        assertTrue(e(429).isRateLimited)
        assertTrue(e(404).isNotFound)
        for (flag in listOf(e(500), e(502), e(null))) {
            assertFalse(flag.isSessionProblem || flag.isPermissionDenied || flag.isReservedByAnother)
            assertFalse(flag.isRejectedCommand || flag.isRateLimited || flag.isNotFound)
        }
    }
}
