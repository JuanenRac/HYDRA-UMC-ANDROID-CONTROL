// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/wear/WatchBridgeTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import com.hydraumc.control.model.HydraState
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

/** What the watch sends the phone to pass on (presence, confirmation) and the plant summary the complication shows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchBridgeTest {
    private val token = "tok-ABCDEF0123456789"

    private fun beat(sequence: Long, state: String = "PRESENT") =
        JSONObject("""{"type":"presence_heartbeat","protocolVersion":1,"sequence":$sequence,"intervalSeconds":5,"state":"$state"}""")

    @Test
    fun aPresenceHeartbeatIsReadAndPassedOnWithTheMissedCount() {
        val heartbeat = WatchPresenceHeartbeat.fromJson(beat(7, "LEFT"))
        assertTrue(heartbeat.left)
        val body = heartbeat.toServerJson(2)
        assertEquals("presence_heartbeat", body.getString("type"))
        assertEquals(7L, body.getLong("sequence"))
        assertEquals("LEFT", body.getString("state"))
        assertEquals(2L, body.getLong("missed"))
    }

    @Test
    fun aMalformedPresenceHeartbeatIsRefused() {
        assertTrue(runCatching { WatchPresenceHeartbeat.fromJson(JSONObject("""{"type":"presence_heartbeat","sequence":-1,"intervalSeconds":5}""")) }.isFailure)
        assertTrue(runCatching { WatchPresenceHeartbeat.fromJson(JSONObject("""{"type":"presence_heartbeat","sequence":1,"intervalSeconds":61}""")) }.isFailure)
        assertTrue(runCatching { WatchPresenceHeartbeat.fromJson(JSONObject("""{"type":"presence_heartbeat","sequence":1,"intervalSeconds":0}""")) }.isFailure)
        assertTrue(runCatching { WatchPresenceHeartbeat.fromJson(JSONObject("""{"type":"presence_heartbeat","sequence":1,"intervalSeconds":5,"state":"AWAY"}""")) }.isFailure)
        assertTrue(runCatching { WatchPresenceHeartbeat.fromJson(JSONObject("""{"type":"presence_heartbeat","sequence":1,"intervalSeconds":5,"protocolVersion":2}""")) }.isFailure)
        assertTrue(runCatching { WatchPresenceHeartbeat.fromJson(JSONObject("""{"type":"other","sequence":1,"intervalSeconds":5}""")) }.isFailure)
    }

    @Test
    fun theTrackerCountsTheHeartbeatsThatNeverArrived() {
        val tracker = WatchPresenceTracker()
        fun missed(sequence: Long) = tracker.onHeartbeat(WatchPresenceHeartbeat.fromJson(beat(sequence)))
        assertEquals(0L, missed(10))
        assertEquals(0L, missed(11))
        assertEquals(3L, missed(15))
        assertEquals("a repeat is not a gap", 0L, missed(15))
        assertEquals("a restart of the watch counter is not a gap", 0L, missed(0))
        assertEquals(0L, missed(1))
    }

    @Test
    fun aConfirmationIsReadAndItsTokenNeverPrinted() {
        val confirm = WatchVoiceConfirm.fromJson(JSONObject("""{"type":"voice_confirm","requestId":"r-2","confirms":"r-1","confirmationToken":"$token"}"""))
        assertEquals("r-2", confirm.requestId)
        assertFalse(confirm.toString().contains(token))
        assertEquals(token, confirm.toServerJson().getString("confirmationToken"))
    }

    @Test
    fun aConfirmationWithABadIdOrTokenIsRefused() {
        fun parse(id: String, confirms: String, tok: String) =
            runCatching { WatchVoiceConfirm.fromJson(JSONObject("""{"type":"voice_confirm","requestId":"$id","confirms":"$confirms","confirmationToken":"$tok"}""")) }
        assertTrue(parse("a b", "r-1", token).isFailure)
        assertTrue(parse("r-2", "x".repeat(65), token).isFailure)
        assertTrue(parse("r-2", "r-1", "short").isFailure)
        assertTrue(parse("r-2", "r-1", "has space in the token!").isFailure)
        assertTrue(parse("r-2", "r-1", "t".repeat(RobotCommandContract.VOICE_CONFIRM_TOKEN_MAX + 1)).isFailure)
        assertTrue(parse("r-2", "r-1", "t".repeat(RobotCommandContract.VOICE_CONFIRM_TOKEN_MAX)).isSuccess)
    }

    @Test
    fun theRelaysAreOffUntilThePersonTurnsThemOn() {
        assertEquals(false, RobotCommandContract.PRESENCE_RELAY_ENABLED_BY_DEFAULT)
        assertEquals(false, RobotCommandContract.VOICE_CONFIRM_ENABLED_BY_DEFAULT)
    }

    @Test
    fun theReplyCarriesTheTokenOnlyWhenItIsValid() {
        val reply = WatchAssistantReply("r-1", "Confirm?", "ATTENTION", speak = true, requiresConfirmation = true, confirmationToken = token, confirmationValiditySeconds = 30)
        val json = reply.toJson()
        assertEquals(token, json.getString("confirmationToken"))
        assertEquals(30, json.getInt("confirmationValiditySeconds"))
        assertTrue(runCatching { reply.copy(confirmationValiditySeconds = 301) }.isFailure)
        assertTrue(runCatching { reply.copy(confirmationToken = "bad token") }.isFailure)
        // read back from the server: a bad token or validity is dropped and the reply itself survives
        val dirty = JSONObject(json.toString()).put("confirmationToken", "bad token").put("confirmationValiditySeconds", 9999)
        val read = WatchAssistantReply.fromJson(dirty)
        assertNull(read.confirmationToken)
        assertNull(read.confirmationValiditySeconds)
        assertEquals("Confirm?", read.text)
        // a reply with neither field is written as before
        assertFalse(WatchAssistantReply("r-1", "Hi", "NOMINAL", speak = false, requiresConfirmation = false).toJson().has("confirmationToken"))
    }

    @Test
    fun theStatusCarriesTheTwoNumbersOfTheComplicationOnlyWhenKnown() {
        val plain = WatchSystemStatus("OK", "", "NOMINAL", speak = false).toJson()
        assertFalse(plain.has("robotsRunning"))
        assertFalse(plain.has("alarmActive"))
        val full = WatchSystemStatus("OK", "", "NOMINAL", speak = false, robotsRunning = 3, alarmActive = true)
        assertEquals(3, full.toJson().getInt("robotsRunning"))
        assertTrue(full.toJson().getBoolean("alarmActive"))
        assertEquals(full, WatchSystemStatus.fromJson(full.toJson()))
        assertTrue(runCatching { full.copy(robotsRunning = 10001) }.isFailure)
    }

    private fun plant(vararg robots: String): HydraState = HydraState(
        JSONObject("""{"activeControllerId":"c1","controllers":[{"id":"c1","robots":[${robots.joinToString(",")}]}]}"""),
    )

    @Test
    fun robotsRunningCountsThePlayingOnesNotPaused() {
        val state = plant(
            """{"id":1,"online":true,"playbackState":{"isPlaying":true}}""",
            """{"id":2,"online":true,"playbackState":{"isPlaying":true,"isPaused":true}}""",
            """{"id":3,"online":true,"playbackState":{"isPlaying":true}}""",
            """{"id":4,"online":true}""",
        )
        assertEquals(2, WatchPlantSummary.robotsRunning(state))
        assertFalse(WatchPlantSummary.alarmActive(state))
    }

    @Test
    fun anOnlineRobotInSafeModeIsAnAlarmAnOfflineOneIsNot() {
        assertTrue(WatchPlantSummary.alarmActive(plant("""{"id":1,"online":true,"enabled":false}""")))
        assertFalse(WatchPlantSummary.alarmActive(plant("""{"id":1,"online":false,"enabled":false}""")))
        assertEquals(0, WatchPlantSummary.robotsRunning(plant()))
    }
}
