// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/SessionVectorsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Replays the sequences the mobile apps share (the SDK's conformance/session-store/vectors.json, copied to the test
 * resources) against the Android session rules. The vectors that need a failing secure storage or a user name that
 * is forgotten at sign-out are for the Dart apps: this app has neither (see the SDK's SESSION_STORE.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionVectorsTest {

    private class MemoryKv : SecretKv {
        val data = HashMap<String, Any>()
        override fun getString(key: String): String? = data[key] as String?
        override fun getBoolean(key: String, default: Boolean): Boolean = data[key] as Boolean? ?: default
        override fun write(block: SecretKv.Writer.() -> Unit) {
            object : SecretKv.Writer {
                override fun putString(key: String, value: String) { data[key] = value }
                override fun putBoolean(key: String, value: Boolean) { data[key] = value }
                override fun remove(key: String) { data.remove(key) }
            }.block()
        }
    }

    private val skipped = setOf("secure_faults", "username_after_logout_cleared")

    private fun doc(): JSONObject {
        val text = javaClass.classLoader!!.getResourceAsStream("session-store-vectors.json")!!.bufferedReader().readText()
        return JSONObject(text)
    }

    private fun nullable(step: JSONObject, key: String): String? = if (step.isNull(key)) null else step.getString(key)

    @Test
    fun theVectorFileIsTheVersionThisTestKnows() {
        assertEquals(1, doc().getInt("version"))
    }

    @Test
    fun everyVectorThisAppCanRunPasses() {
        val vectors = doc().getJSONArray("vectors")
        var ran = 0
        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val requires = vector.optJSONArray("requires") ?: JSONArray()
            if ((0 until requires.length()).any { requires.getString(it) in skipped }) continue
            val name = vector.getString("name")
            val kv = MemoryKv()
            var session = SessionFields(kv)
            val steps = vector.getJSONArray("steps")
            for (j in 0 until steps.length()) {
                val step = steps.getJSONObject(j)
                when (val op = step.getString("op")) {
                    "login" -> session.saveSession(step.getString("token"), step.getString("username"), nullable(step, "refresh").takeIf { step.has("refresh") }, true)
                    "renew" -> session.saveSession(step.getString("token"), step.getString("username"), nullable(step, "refresh").takeIf { step.has("refresh") }, false)
                    "logout" -> session.clearSession()
                    "restart" -> session = SessionFields(kv)
                    "expect" -> {
                        if (step.has("token")) assertEquals("$name: token", nullable(step, "token"), session.token())
                        if (step.has("username")) assertEquals("$name: username", nullable(step, "username"), session.username())
                        if (step.has("refresh")) assertEquals("$name: refresh", nullable(step, "refresh"), session.refreshToken())
                    }
                    else -> throw AssertionError("$name: unknown operation $op")
                }
            }
            ran++
        }
        assertTrue("the vectors this app can run: $ran", ran >= 6)
    }

    @Test
    fun anEmptyStoredValueCountsAsNone() {
        val kv = MemoryKv().apply { data["token"] = ""; data["refresh_token"] = "" }
        val session = SessionFields(kv)
        assertNull(session.token())
        assertNull(session.refreshToken())
    }

    @Test
    fun signingOutKeepsTheUserNameForTheForm() {
        val session = SessionFields(MemoryKv())
        session.saveSession("t", "ana", "r", true)
        session.clearSession()
        assertEquals("ana", session.username())
        assertNull(session.token())
    }
}
