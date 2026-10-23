// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/AccessModelsTest.kt
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

/** The controller id, the reservations, the limits and the jog check: pure rules (org.json needs the Robolectric runner). */
@RunWith(RobolectricTestRunner::class)
class AccessModelsTest {

    @Test
    fun `a generated controller id is valid for the server`() {
        val id = SessionController.generate()
        assertTrue(id, SessionController.isValid(id))
        assertTrue(id.length <= 64)
    }

    @Test
    fun `the controller id follows the server pattern`() {
        assertTrue(SessionController.isValid("a.b_c:d-1"))
        assertFalse(SessionController.isValid(""))
        assertFalse(SessionController.isValid(null))
        assertFalse(SessionController.isValid("has space"))
        assertFalse(SessionController.isValid("x".repeat(65)))
        assertTrue(SessionController.isValid("x".repeat(64)))
    }

    @Test
    fun `an unusable stored id is ignored`() {
        val before = SessionController.id
        SessionController.adopt("bad id!")
        assertEquals(before, SessionController.id)
        SessionController.adopt(before)
        assertEquals(before, SessionController.id)
    }

    @Test
    fun `reservations are read from the claims answer`() {
        val json = JSONObject(
            """{"claims":[
                {"robotId":1,"robotName":"A1","cellId":"c","ownerId":"u1","ownerUsername":"ana","controllerId":"android-1","claimedAt":1,"expiresAt":5000},
                {"robotId":2,"robotName":null,"ownerUsername":"bob","controllerId":null,"expiresAt":6000},
                {"nothing":true}
            ]}""",
        )
        val claims = ClaimInfo.listFromJson(json)
        assertEquals(2, claims.size)
        assertEquals("android-1", claims[0].controllerId)
        assertNull("a reservation without a controller id has none", claims[1].controllerId)
        assertEquals("", claims[1].robotName)
        assertEquals(6000L, claims[1].expiresAt)
        assertTrue(ClaimInfo.listFromJson(JSONObject("{}")).isEmpty())
    }

    @Test
    fun `where a robot stands for this installation`() {
        val mine = ClaimInfo(1, "A1", "ana", "android-1", 1)
        assertEquals(ClaimStanding.FREE, claimStanding(null, "ana", "android-1"))
        assertEquals(ClaimStanding.MINE_HERE, claimStanding(mine, "ana", "android-1"))
        assertEquals(ClaimStanding.MINE_ELSEWHERE, claimStanding(mine, "ana", "android-2"))
        assertEquals(ClaimStanding.OTHER, claimStanding(mine, "bob", "android-2"))
        assertEquals("a name that is not known is never taken for the owner", ClaimStanding.OTHER, claimStanding(mine, "", "android-2"))
        assertEquals("a claim under this controller id is ours whatever the name", ClaimStanding.MINE_HERE, claimStanding(mine, "", "android-1"))
        assertEquals("a reservation without a controller answers to any controller of the account",
            ClaimStanding.MINE_HERE, claimStanding(mine.copy(controllerId = null), "ana", "android-2"))
    }

    private val limitsAnswer = """{
        "limits":{"jog":{"armRequired":true,"armTtlMs":30000,"default":{"maxAmount":100,"maxAbsolute":1000},"models":{}},
                  "temperature":{"default":{"bedMaxC":110},"models":{}},"claims":{"requireControllerId":false}},
        "robot":{"id":3,"model":"Parol6 (6-DOF)","jog":{"maxAmount":20,"maxAbsolute":500},"temperature":{"bedMaxC":90},"jogArmedUntil":%s}}"""

    @Test
    fun `the limits of a robot are read from the safety answer`() {
        val safety = RobotSafety.fromJson(JSONObject(limitsAnswer.format("null")))!!
        assertEquals(3, safety.robotId)
        assertEquals(20.0, safety.jog.maxAmount, 0.0)
        assertEquals(500.0, safety.jog.maxAbsolute, 0.0)
        assertEquals(90.0, safety.bedMaxC, 0.0)
        assertTrue(safety.armRequired)
        assertEquals(30_000L, safety.armTtlMs)
        assertNull(safety.jogArmedUntil)
        assertEquals(1234567L, RobotSafety.fromJson(JSONObject(limitsAnswer.format("1234567")))!!.jogArmedUntil)
    }

    @Test
    fun `an answer without limits for the robot gives none`() {
        assertNull(RobotSafety.fromJson(JSONObject("""{"limits":{}}""")))
        assertNull(RobotSafety.fromJson(JSONObject("""{"robot":{"id":1,"jog":{"maxAmount":0,"maxAbsolute":10}}}""")))
        assertNull(RobotSafety.fromJson(JSONObject("""{"robot":{"id":1}}""")))
    }

    @Test
    fun `a jog above the limit of the model is caught`() {
        val limits = JogLimits(maxAmount = 20.0, maxAbsolute = 500.0)
        assertNull(jogLimitExceeded(20.0, limits))
        assertNull(jogLimitExceeded(-20.0, limits))
        assertEquals(20.0, jogLimitExceeded(20.5, limits)!!, 0.0)
        assertEquals(20.0, jogLimitExceeded(-50.0, limits)!!, 0.0)
        assertNull(jogLimitExceeded(499.0, limits, absolute = true))
        assertEquals(500.0, jogLimitExceeded(-501.0, limits, absolute = true)!!, 0.0)
        assertEquals(20.0, jogLimitExceeded(Double.NaN, limits)!!, 0.0)
    }

    @Test
    fun `without known limits the server decides`() {
        assertNull(jogLimitExceeded(10_000.0, null))
    }

    private fun token(payload: String): String {
        fun part(json: String) = android.util.Base64.encodeToString(json.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        return part("""{"alg":"HS256"}""") + "." + part(payload) + ".sig"
    }

    @Test
    fun `the role is read from the token and a damaged token gives none`() {
        assertEquals("admin", jwtRole(token("""{"role":"admin"}""")))
        assertEquals("operator", jwtRole(token("""{"role":"operator"}""")))
        assertNull(jwtRole(token("""{"username":"ana"}""")))
        assertNull(jwtRole("not-a-token"))
        assertNull(jwtRole("a.%%%.c"))
        assertNull(jwtRole(null))
        assertNull(jwtRole(""))
    }

    @Test
    fun `the take-over applies to an administrator on a robot someone else holds`() {
        assertTrue(canTakeOver("admin", ClaimStanding.OTHER))
        assertTrue(canTakeOver("admin", ClaimStanding.MINE_ELSEWHERE))
        assertFalse(canTakeOver("admin", ClaimStanding.FREE))
        assertFalse(canTakeOver("admin", ClaimStanding.MINE_HERE))
        assertFalse(canTakeOver("operator", ClaimStanding.OTHER))
        assertFalse(canTakeOver(null, ClaimStanding.OTHER))
    }
}
