// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/ServerRecordTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.ReadingProblem
import com.hydraumc.control.network.ServerFaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/** The server's fault record and board health, read only and on demand, against a local server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerRecordTest {

    private val server = MockWebServer()
    private val seen = CopyOnWriteArrayList<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var faults = MockResponse().setBody(
        """{"active":[{"schema":"fault-event/1","source":"cell","robotId":"1","code":"E-DRIVE","severity":"critical","text":"drive","state":"raised","since":2000}],
           "recent":[{"at":1000,"source":"cell","robotId":null,"code":"E-TEMP","severity":"odd","text":"","state":"cleared"},{"at":2000,"source":"cell","code":"E-DRIVE","severity":"critical","state":"raised"},{"code":"no-source"}]}""",
    )
    private var board: (Int) -> MockResponse = { slot ->
        MockResponse().setBody(
            if (slot == 0) """{"online":true,"slot":0,"heard":true,"ageMs":2500,"sequence":7,"flags":1,"flagNames":["estop_latched"],"txErrors":1,"rxErrors":2,"stalls":0,"uptimeS":48,"source":"brain-health"}"""
            else """{"online":true,"slot":$slot,"heard":false,"source":"brain-health"}""",
        )
    }

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                seen += "${request.method} $path ${request.getHeader("Authorization")}"
                return when {
                    path.startsWith("/api/faults") -> faults
                    path.startsWith("/api/ecosystem/node-health") -> board(path.substringAfter("slot=").toInt())
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun stop() {
        scope.cancel()
        server.shutdown()
    }

    private var sessionAsked = 0
    private val client by lazy { HydraApiClient("127.0.0.1", server.port).also { it.authToken = "session" } }

    private fun read(record: ServerRecord): ServerReading {
        record.refresh()
        val deadline = System.currentTimeMillis() + 10_000
        while ((record.busy.value || record.reading == null) && System.currentTimeMillis() < deadline) Thread.sleep(20)
        return record.reading!!
    }

    @Test
    fun theRecordAndTheEightSlotsAreReadWithTheSessionAndNothingElse() {
        val reading = read(ServerRecord(scope, { client }, { sessionAsked++ }))

        assertEquals(listOf("E-DRIVE"), reading.faults!!.active.map { it.code })
        assertEquals(listOf("E-DRIVE", "E-TEMP"), reading.faults!!.recent.map { it.code })
        assertEquals("an unknown severity is not read as a mild one", "error", reading.faults!!.recent[1].severity)
        assertTrue(reading.faults!!.recent[1].cleared)
        assertEquals(8, reading.boards.size)
        assertEquals(listOf("estop_latched"), reading.boards[0].flags)
        assertEquals("GET /api/faults?limit=50 Bearer session", seen.first())
        assertTrue("read only", seen.all { it.startsWith("GET ") })
        assertEquals(9, seen.size)
    }

    @Test
    fun aServerWithBothOffIsSaidAndOneBoardSlotIsEnoughToKnow() {
        faults = MockResponse().setResponseCode(404)
        board = { MockResponse().setResponseCode(404) }

        val reading = read(ServerRecord(scope, { client }, { sessionAsked++ }))

        assertEquals(ReadingProblem.OFF, reading.faultsProblem)
        assertEquals(ReadingProblem.OFF, reading.boardsProblem)
        assertEquals(2, seen.size)
        assertEquals(0, sessionAsked)
    }

    @Test
    fun aViewerIsToldItMayNotAskAboutTheBoards() {
        board = { MockResponse().setResponseCode(403) }
        val reading = read(ServerRecord(scope, { client }, { sessionAsked++ }))
        assertEquals(ReadingProblem.FORBIDDEN, reading.boardsProblem)
        assertEquals(0, sessionAsked)
    }

    @Test
    fun whatWasReadFromOneServerIsNotShownForAnother() {
        var current = client
        val record = ServerRecord(scope, { current }, { sessionAsked++ })
        read(record)
        current = HydraApiClient("127.0.0.2", server.port)
        assertNull(record.reading)
    }

    @Test
    fun anAnswerThatIsNotTheRecordIsRefused() {
        assertNull(ServerFaults.parse("not json"))
        assertNull(ServerFaults.parse("""{"items":[]}"""))
    }
}
