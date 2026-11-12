// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/wear/WatchRelayHandlerTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.hydraumc.control.wear

import com.hydraumc.control.network.HydraApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The five routes of the relay, with a recorder in place of the Data Layer and a stand-in for the server. */
class WatchRelayHandlerTest {
    private class Sent(val nodeId: String, val path: String, val json: JSONObject)

    private class FakeBackend : WatchRelayBackend {
        var voiceTurn: suspend (WatchVoiceTurn) -> WatchAssistantReply = { error("not set") }
        var status: suspend () -> WatchSystemStatus = { error("not set") }
        var security: suspend () -> WatchServerSecurityStatus? = { error("not set") }
        var presence: suspend (JSONObject) -> Unit = {}
        var confirm: suspend (WatchVoiceConfirm) -> WatchAssistantReply = { error("not set") }
        val presenceBodies = mutableListOf<JSONObject>()

        override suspend fun voiceTurn(turn: WatchVoiceTurn) = voiceTurn.invoke(turn)
        override suspend fun systemStatus() = status.invoke()
        override suspend fun serverSecurity() = security.invoke()
        override suspend fun presence(body: JSONObject) {
            presenceBodies += body
            presence.invoke(body)
        }
        override suspend fun voiceConfirm(confirm: WatchVoiceConfirm) = this.confirm.invoke(confirm)
    }

    private class Flags(var presence: Boolean = true, var confirm: Boolean = true) : WatchRelayFlags {
        override suspend fun presenceRelay() = presence
        override suspend fun voiceConfirmRelay() = confirm
    }

    private val texts = WatchRelayTexts("no connection", "offline", "server unreachable", "confirmation is off")

    private class Rig(scope: TestScope) {
        val sent = mutableListOf<Sent>()
        val backend = FakeBackend()
        val flags = Flags()
        val logs = mutableListOf<String>()
        val requests = BoundedRequestScope(CoroutineScope(StandardTestDispatcher(scope.testScheduler)))
    }

    private fun TestScope.rig(texts: WatchRelayTexts, timeoutMs: Long = 1_000L): Pair<Rig, WatchRelayHandler> {
        val rig = Rig(this)
        val handler = WatchRelayHandler(
            requests = rig.requests,
            backend = rig.backend,
            sender = MessageSender { node, path, payload -> rig.sent += Sent(node, path, JSONObject(payload.decodeToString())) },
            flags = rig.flags,
            texts = texts,
            requestTimeoutMs = timeoutMs,
            onLog = { rig.logs += it },
        )
        return rig to handler
    }

    private fun bytes(json: JSONObject) = json.toString().encodeToByteArray()

    private fun turnJson(id: String = "t1") =
        JSONObject().put("type", "voice_turn").put("requestId", id).put("transcript", "status").put("locale", "en")

    private fun beatJson(sequence: Long) =
        JSONObject().put("type", "presence_heartbeat").put("sequence", sequence).put("intervalSeconds", 5).put("state", "PRESENT")

    private fun confirmJson() = JSONObject().put("type", "voice_confirm").put("requestId", "c2").put("confirms", "t1")
        .put("confirmationToken", "abcdefghijklmnop1234")

    private fun reply(id: String, text: String) = WatchAssistantReply(id, text, "INFO", speak = false, requiresConfirmation = false)

    @Test
    fun aSpokenTurnIsAnsweredWithTheReplyOfTheServer() = runTest {
        val (rig, handler) = rig(texts)
        rig.backend.voiceTurn = { reply(it.requestId, "all fine") }
        handler.onMessage(WatchRelayPaths.VOICE_TURN, "node-1", bytes(turnJson()))
        advanceUntilIdle()
        assertEquals(1, rig.sent.size)
        assertEquals("node-1", rig.sent[0].nodeId)
        assertEquals(WatchRelayPaths.ASSISTANT_REPLY, rig.sent[0].path)
        assertEquals("all fine", rig.sent[0].json.getString("text"))
    }

    @Test
    fun aSpokenTurnThatFailsGetsTheConnectionUnavailableReply() = runTest {
        val (rig, handler) = rig(texts)
        rig.backend.voiceTurn = { throw HydraApiException("x", httpCode = 500) }
        handler.onMessage(WatchRelayPaths.VOICE_TURN, "n", bytes(turnJson("t9")))
        advanceUntilIdle()
        val json = rig.sent.single().json
        assertEquals("no connection", json.getString("text"))
        assertEquals("connection_unavailable", json.getString("errorCode"))
        assertEquals("t9", json.getString("requestId"))
    }

    @Test
    fun aSpokenTurnThatHangsIsAnsweredWhenTheTimeoutEnds() = runTest {
        val (rig, handler) = rig(texts, timeoutMs = 1_000L)
        rig.backend.voiceTurn = { awaitCancellation() }
        handler.onMessage(WatchRelayPaths.VOICE_TURN, "n", bytes(turnJson()))
        advanceTimeBy(999)
        assertTrue(rig.sent.isEmpty())
        advanceTimeBy(2)
        advanceUntilIdle()
        assertEquals("connection_unavailable", rig.sent.single().json.getString("errorCode"))
    }

    @Test
    fun aMessageThatDoesNotParseOrHasAnUnknownPathIsDropped() = runTest {
        val (rig, handler) = rig(texts)
        handler.onMessage(WatchRelayPaths.VOICE_TURN, "n", "not json".encodeToByteArray())
        handler.onMessage(WatchRelayPaths.VOICE_TURN, "n", bytes(JSONObject().put("type", "other")))
        handler.onMessage(WatchBridgePaths.PRESENCE, "n", "{".encodeToByteArray())
        handler.onMessage(WatchBridgePaths.VOICE_CONFIRM, "n", bytes(JSONObject()))
        handler.onMessage("/hydra-umc/unknown/v1", "n", bytes(turnJson()))
        advanceUntilIdle()
        assertTrue(rig.sent.isEmpty())
        assertEquals(0, rig.requests.activeCount)
    }

    @Test
    fun theStatusCardCarriesTheCountsAndFailsToTheOfflineCard() = runTest {
        val (rig, handler) = rig(texts)
        rig.backend.status = { WatchSystemStatus("ok", "all", "OK", speak = false, robotsRunning = 2, alarmActive = false) }
        handler.onMessage(WatchRelayPaths.STATUS_REQUEST, "n", ByteArray(0))
        advanceUntilIdle()
        assertEquals(WatchRelayPaths.SYSTEM_STATUS, rig.sent[0].path)
        assertEquals(2, rig.sent[0].json.getInt("robotsRunning"))

        rig.backend.status = { throw IllegalStateException("no session") }
        handler.onMessage(WatchRelayPaths.STATUS_REQUEST, "n", ByteArray(0))
        advanceUntilIdle()
        assertEquals("OFFLINE", rig.sent[1].json.getString("level"))
        assertEquals("offline", rig.sent[1].json.getString("errorCode"))
        assertEquals("offline", rig.sent[1].json.getString("headline"))
    }

    @Test
    fun theSecurityStatusGoesOnItsOwnPathAndAFailureGoesAsTheOfflineCard() = runTest {
        val (rig, handler) = rig(texts)
        rig.backend.security = { WatchServerSecurityStatus(listOf("safety.framErase"), 1, 2, 3) }
        handler.onMessage(WatchRelayPaths.SERVER_SECURITY_REQUEST, "n", ByteArray(0))
        advanceUntilIdle()
        assertEquals(WatchRelayPaths.SERVER_SECURITY, rig.sent[0].path)
        assertEquals(3, rig.sent[0].json.getInt("failedLogins"))

        rig.backend.security = { null }
        handler.onMessage(WatchRelayPaths.SERVER_SECURITY_REQUEST, "n", ByteArray(0))
        advanceUntilIdle()
        assertEquals(WatchRelayPaths.SYSTEM_STATUS, rig.sent[1].path)
        assertEquals("OFFLINE", rig.sent[1].json.getString("level"))

        rig.backend.security = { throw HydraApiException("x", httpCode = 403) }
        handler.onMessage(WatchRelayPaths.SERVER_SECURITY_REQUEST, "n", ByteArray(0))
        advanceUntilIdle()
        assertEquals(WatchRelayPaths.SYSTEM_STATUS, rig.sent[2].path)
    }

    @Test
    fun presenceIsPassedOnWithTheMissedCountOnlyWhileTheSwitchIsOn() = runTest {
        val (rig, handler) = rig(texts)
        handler.onMessage(WatchBridgePaths.PRESENCE, "n", bytes(beatJson(1)))
        advanceUntilIdle()
        handler.onMessage(WatchBridgePaths.PRESENCE, "n", bytes(beatJson(4)))
        advanceUntilIdle()
        assertEquals(listOf(0L, 2L), rig.backend.presenceBodies.map { it.getLong("missed") })

        rig.flags.presence = false
        handler.onMessage(WatchBridgePaths.PRESENCE, "n", bytes(beatJson(5)))
        advanceUntilIdle()
        assertEquals(2, rig.backend.presenceBodies.size)
        assertTrue(rig.sent.isEmpty())
    }

    @Test
    fun aServerWithoutThePresenceRouteSilencesTheRelayAfterOneRefusal() = runTest {
        val (rig, handler) = rig(texts)
        rig.backend.presence = { throw HydraApiException("x", httpCode = 404) }
        handler.onMessage(WatchBridgePaths.PRESENCE, "n", bytes(beatJson(1)))
        advanceUntilIdle()
        assertTrue(handler.presenceUnsupported)
        handler.onMessage(WatchBridgePaths.PRESENCE, "n", bytes(beatJson(2)))
        advanceUntilIdle()
        assertEquals(1, rig.backend.presenceBodies.size)
        assertTrue(rig.logs.single().contains("404"))
    }

    @Test
    fun anotherFailureOfThePresenceRelayIsLoggedAndTheNextBeatTriesAgain() = runTest {
        val (rig, handler) = rig(texts)
        rig.backend.presence = { throw IllegalStateException("down") }
        handler.onMessage(WatchBridgePaths.PRESENCE, "n", bytes(beatJson(1)))
        advanceUntilIdle()
        assertFalse(handler.presenceUnsupported)
        handler.onMessage(WatchBridgePaths.PRESENCE, "n", bytes(beatJson(2)))
        advanceUntilIdle()
        assertEquals(2, rig.backend.presenceBodies.size)
        assertEquals(2, rig.logs.size)
    }

    @Test
    fun aConfirmationIsRelayedWhenTheSwitchIsOn() = runTest {
        val (rig, handler) = rig(texts)
        rig.backend.confirm = { reply(it.requestId, "done") }
        handler.onMessage(WatchBridgePaths.VOICE_CONFIRM, "n", bytes(confirmJson()))
        advanceUntilIdle()
        assertEquals(WatchRelayPaths.ASSISTANT_REPLY, rig.sent.single().path)
        assertEquals("done", rig.sent.single().json.getString("text"))
    }

    @Test
    fun aConfirmationWithTheSwitchOffIsAnsweredLocallyAndNeverReachesTheServer() = runTest {
        val (rig, handler) = rig(texts)
        rig.flags.confirm = false
        rig.backend.confirm = { error("must not be called") }
        handler.onMessage(WatchBridgePaths.VOICE_CONFIRM, "n", bytes(confirmJson()))
        advanceUntilIdle()
        assertEquals("confirmation is off", rig.sent.single().json.getString("text"))
    }

    @Test
    fun aFailedConfirmationGetsTheUnavailableReplyAndLogsOnlyTheErrorClass() = runTest {
        val (rig, handler) = rig(texts)
        rig.backend.confirm = { throw HydraApiException("secret-token-abcdefghijklmnop", httpCode = 500) }
        handler.onMessage(WatchBridgePaths.VOICE_CONFIRM, "n", bytes(confirmJson()))
        advanceUntilIdle()
        assertEquals("connection_unavailable", rig.sent.single().json.getString("errorCode"))
        assertTrue(rig.logs.single().contains("HydraApiException"))
        assertFalse(rig.logs.single().contains("secret"))
    }

    @Test
    fun aRepeatedTurnWithTheSameIdRepliesOnce() = runTest {
        val (rig, handler) = rig(texts)
        var calls = 0
        rig.backend.voiceTurn = { calls++; awaitCancellation() }
        handler.onMessage(WatchRelayPaths.VOICE_TURN, "n", bytes(turnJson("same")))
        advanceTimeBy(10)
        handler.onMessage(WatchRelayPaths.VOICE_TURN, "n", bytes(turnJson("same")))
        advanceTimeBy(10)
        assertEquals(2, calls)
        assertEquals(1, rig.requests.activeCount)
        rig.requests.cancelAll()
        advanceUntilIdle()
        assertTrue(rig.sent.isEmpty())
    }
}
