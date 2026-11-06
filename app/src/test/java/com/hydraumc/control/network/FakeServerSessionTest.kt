// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/FakeServerSessionTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The real client against the one stand-in server of the SDK (a vendored copy of
 * conformance/fake-server/hydra_fake_server.py, kept identical by tools/sync_sdk_answers.py), which keeps
 * state: a sign-in gives a token, a wrong password is refused and the global STOP stops every robot. The
 * test is skipped on a machine without Python.
 */
@RunWith(RobolectricTestRunner::class)
class FakeServerSessionTest {
    private var process: Process? = null
    private var port = 0

    private fun python(): String? = listOf("python3", "python", "py").firstOrNull {
        try {
            val probe = ProcessBuilder(it, "--version").redirectErrorStream(true).start()
            probe.inputStream.readBytes()
            probe.waitFor() == 0
        } catch (error: java.io.IOException) {
            false
        }
    }

    @Before
    fun setUp() {
        val interpreter = python()
        assumeTrue("Python is not available, the stand-in server cannot start", interpreter != null)
        val dir = listOf("src/test", "app/src/test").map { File(it) }.first { File(it, "fake-server/hydra_fake_server.py").isFile }
        val builder = ProcessBuilder(interpreter, File(dir, "fake-server/hydra_fake_server.py").absolutePath, "--port", "0")
        builder.environment()["HYDRA_FAKE_ANSWERS"] = File(dir, "resources/server-answers.json").absolutePath
        builder.redirectErrorStream(true)
        process = builder.start()
        val line = process!!.inputStream.bufferedReader().readLine()
        port = Regex("""http://127\.0\.0\.1:(\d+)""").find(line)!!.groupValues[1].toInt()
    }

    @After
    fun tearDown() {
        process?.destroyForcibly()
    }

    @Test
    fun `a sign-in gives a token, a wrong password is refused and the global stop stops every robot`() = runBlocking {
        val client = HydraApiClient("127.0.0.1", port)
        val session = client.login("admin", "admin")
        val token = session.optString("token")
        assertTrue(token.isNotEmpty())
        try {
            client.login("admin", "wrong")
            throw AssertionError("a wrong password must be refused")
        } catch (expected: java.io.IOException) {
            // refused with a status, as the server does
        } catch (expected: RuntimeException) {
            // an error type of the client
        }
        client.authToken = token
        val stop = client.postStopAll()
        assertEquals(2, stop.stoppedCount)
        assertEquals(0, stop.failedCount)
        assertFalse(stop.robots.any { !it.stopped })
    }
}
