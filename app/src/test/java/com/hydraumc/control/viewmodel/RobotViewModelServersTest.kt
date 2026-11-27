// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelServersTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.MainDispatcherSwap
import com.hydraumc.control.MemoryAuthStore
import com.hydraumc.control.network.StopAllStatus
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/** Two real local servers: each keeps its own session, and no token, password or STOP ever reaches the other one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelServersTest {

    private class Fake(private val name: String) {
        val server = MockWebServer()
        val seen = CopyOnWriteArrayList<Triple<String, String?, String>>()

        fun start() {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty().substringBefore('?')
                    seen += Triple(path, request.getHeader("Authorization"), request.body.readUtf8())
                    return when (path) {
                        "/api/login" -> MockResponse().setBody("""{"success":true,"token":"token-$name","refreshToken":"refresh-$name"}""")
                        "/api/settings" -> MockResponse().setBody("""{"controllers":[]}""")
                        "/api/stop-all" -> MockResponse().setBody("""{"success":true,"stoppedCount":0,"failedCount":0,"persisted":true,"robots":[]}""")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            server.start()
        }

        /** Everything this server received that belongs to the server called [other]. */
        fun leaks(other: String) = seen.filter { (_, auth, body) -> auth?.contains("-$other") == true || body.contains("-$other") }

        fun stops() = seen.count { it.first == "/api/stop-all" }
    }

    private val a = Fake("alpha")
    private val b = Fake("beta")

    @Before
    fun setUp() {
        MainDispatcherSwap.set()
        a.start()
        b.start()
    }

    @After
    fun tearDown() {
        try {
            a.server.shutdown()
            b.server.shutdown()
        } catch (_: java.io.IOException) {
            // a socket of the live connection was still closing
        } finally {
            MainDispatcherSwap.reset()
        }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(what, condition())
    }

    private fun signIn(model: RobotViewModel, fake: Fake, password: String) {
        model.ipAddress.value = "127.0.0.1"
        model.port.value = fake.server.port.toString()
        model.login("ana", password, remember = true)
        waitUntil("signed in to ${fake.server.port}") {
            model.isLoggedIn.value && model.apiClient?.serverPort == fake.server.port && model.savedServers.value.any { it.port == fake.server.port }
        }
        waitUntil("state read") { fake.seen.any { it.first == "/api/settings" } }
    }

    private fun signedInToBoth(): RobotViewModel {
        val model = RobotViewModel(ApplicationProvider.getApplicationContext<Application>(), MemoryAuthStore())
        waitUntil("start-up read") { model.authCheckComplete.value }
        signIn(model, a, "secret-alpha")
        signIn(model, b, "secret-beta")
        return model
    }

    @Test
    fun eachServerKeepsItsOwnSessionAndNothingCrosses() {
        val model = signedInToBoth()
        assertEquals(2, model.savedServers.value.size)

        model.selectServer(model.savedServers.value.first { it.port == a.server.port })
        waitUntil("back on the first server with its own token") {
            model.apiClient?.serverPort == a.server.port && model.apiClient?.authToken == "token-alpha" && model.isLoggedIn.value
        }
        waitUntil("read again") { a.seen.count { it.first == "/api/settings" } >= 2 }

        assertEquals(emptyList<Any>(), a.leaks("beta"))
        assertEquals(emptyList<Any>(), b.leaks("alpha"))
    }

    @Test
    fun theStopGoesOnlyToTheServerInUseAndSaysWhichOne() {
        val model = signedInToBoth()

        model.globalStop()
        waitUntil("stop answered") { model.stopAllStatus.value is StopAllStatus.Done }

        assertEquals(1, b.stops())
        assertEquals(0, a.stops())
        assertTrue(model.stopAllServer.value.orEmpty().contains(b.server.port.toString()))

        model.selectServer(model.savedServers.value.first { it.port == a.server.port })
        waitUntil("moved") { model.apiClient?.serverPort == a.server.port }
        model.globalStop()
        waitUntil("second stop") { a.stops() == 1 }
        assertEquals(1, b.stops())
    }

    @Test
    fun aServerWithNoSessionGetsNoTokenAndAsksForTheSignIn() {
        val model = RobotViewModel(ApplicationProvider.getApplicationContext<Application>(), MemoryAuthStore())
        waitUntil("start-up read") { model.authCheckComplete.value }
        signIn(model, a, "secret-alpha")

        model.ipAddress.value = "127.0.0.1"
        model.port.value = b.server.port.toString()
        model.connect()
        waitUntil("asked the second server") { b.seen.isNotEmpty() }
        waitUntil("sign-in again") { !model.isLoggedIn.value }

        assertEquals(emptyList<Any>(), b.leaks("alpha"))
        assertTrue(b.seen.none { it.first == "/api/login" })
    }

    @Test
    fun forgettingAServerRemovesItAndSendsNothingToIt() {
        val model = signedInToBoth()
        val first = model.savedServers.value.first { it.port == a.server.port }
        val before = a.seen.size

        model.forgetServer(first.id)

        assertEquals(listOf(b.server.port), model.savedServers.value.map { it.port })
        Thread.sleep(300)
        assertEquals(before, a.seen.size)
    }
}
