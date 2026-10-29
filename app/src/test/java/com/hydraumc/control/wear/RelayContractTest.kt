// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - wear/RelayContractTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * docs/watch_relay_contract.json is shared with the watch app (an identical copy lives in its repository).
 * The voice turns of the file must be accepted or refused as the file says, and the replies and statuses this
 * app sends must come out exactly as the file writes them.
 */
@RunWith(RobolectricTestRunner::class)
class RelayContractTest {
    private val contract = JSONObject(File("../docs/watch_relay_contract.json").readText())

    /** A value written as {"repeat": "x", "count": n} becomes a text of n characters. */
    private fun expanded(vector: JSONObject): JSONObject {
        val out = JSONObject()
        for (key in vector.keys()) {
            if (key == "why") continue
            val value = vector.get(key)
            out.put(key, if (value is JSONObject) value.getString("repeat").repeat(value.getInt("count")) else value)
        }
        return out
    }

    private fun vectors(name: String): List<JSONObject> =
        contract.getJSONArray(name).let { array: JSONArray -> (0 until array.length()).map { array.getJSONObject(it) } }

    @Test
    fun `the contract speaks the protocol of this app`() {
        assertEquals(1, contract.getInt("protocolVersion"))
    }

    @Test
    fun `valid voice turns are accepted and written back as they came`() {
        for (vector in vectors("valid").filter { it.getString("type") == "voice_turn" }) {
            val turn = WatchVoiceTurn.fromJson(expanded(vector))
            assertEquals(expanded(vector).toString().length, turn.toJson().toString().length)
            assertEquals(vector.getString("transcript"), turn.toJson().getString("transcript"))
        }
    }

    @Test
    fun `invalid voice turns are refused`() {
        for (vector in vectors("invalid").filter { it.getString("type") == "voice_turn" }) {
            try {
                WatchVoiceTurn.fromJson(expanded(vector))
                fail("accepted an invalid voice turn (${vector.getString("why")})")
            } catch (_: IllegalArgumentException) {
                // refused, as the contract says
            }
        }
    }

    @Test
    fun `valid replies and statuses are written with the same fields and values`() {
        for (vector in vectors("valid")) {
            val written = when (vector.getString("type")) {
                "assistant_reply" -> WatchAssistantReply.fromJson(vector).toJson()
                "system_status" -> WatchSystemStatus.fromJson(vector).toJson()
                else -> continue
            }
            assertEquals(vector.keys().asSequence().toSet(), written.keys().asSequence().toSet())
            for (key in vector.keys()) assertEquals(key, vector.get(key), written.get(key))
        }
    }
}
