// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/ListenOnlyDoorTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Listen only at the door of the network: a refused write answers 423 and never reaches the next interceptor (the server). */
@RunWith(RobolectricTestRunner::class)
class ListenOnlyDoorTest {
    private val json = "application/json".toMediaType()

    private fun segs(path: String) = path.split("/").filter { it.isNotEmpty() }

    @Test
    fun `reads, STOP and the session go through`() {
        assertTrue(ListenOnly.allowsRequest("GET", segs("api/settings"), null))
        assertTrue(ListenOnly.allowsRequest("POST", segs("api/stop-all"), null))
        for (command in listOf("stop", "disable")) {
            assertTrue(command, ListenOnly.allowsRequest("POST", segs("api/robot/3/command"), "{\"command\":\"$command\"}"))
        }
        for (name in listOf("login", "refresh", "logout")) assertTrue(name, ListenOnly.allowsRequest("POST", segs("api/$name"), "{}"))
        assertTrue(ListenOnly.allowsRequest("POST", segs("api/device/login"), "{}"))
    }

    @Test
    fun `every other robot command and every administration write is refused`() {
        for (command in listOf("jog", "jogStart", "play", "pause", "reset", "enable", "tool", "valve", "pump", "speed", "vision")) {
            assertFalse(command, ListenOnly.allowsRequest("POST", segs("api/robot/3/command"), "{\"command\":\"$command\"}"))
        }
        assertFalse(ListenOnly.allowsRequest("POST", segs("api/robot/3/command"), "not json"))
        assertFalse(ListenOnly.allowsRequest("POST", segs("api/robot/3/command"), null))
        for ((method, path) in listOf("POST" to "api/settings", "PUT" to "api/settings", "POST" to "api/system/reboot", "DELETE" to "api/users/bob",
            "POST" to "api/push/device", "POST" to "api/presence", "PUT" to "api/server/config", "POST" to "api/claims")) {
            assertFalse("$method $path", ListenOnly.allowsRequest(method, segs(path), "{}"))
        }
    }

    @Test
    fun `the interceptor answers 423 to a refused write and sends the rest on`() {
        var reached = 0
        var on = false
        val client = OkHttpClient.Builder()
            .addInterceptor(ListenOnlyInterceptor { on })
            .addInterceptor { chain ->
                reached++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{}".toResponseBody(json)).build()
            }
            .build()
        val write = Request.Builder().url("http://127.0.0.1:3000/api/settings").post("{}".toRequestBody(json)).build()

        assertEquals(200, client.newCall(write).execute().use { it.code })
        assertEquals(1, reached)

        on = true
        val refused = client.newCall(write).execute()
        assertEquals(423, refused.code)
        assertTrue((refused.body?.string() ?: "").contains("listen-only"))
        assertEquals(1, reached)

        client.newCall(Request.Builder().url("http://127.0.0.1:3000/api/settings").build()).execute().close()
        client.newCall(Request.Builder().url("http://127.0.0.1:3000/api/stop-all").post("".toRequestBody(json)).build()).execute().close()
        assertEquals(3, reached)
    }
}
