// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/RobotViewModelConditionalTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.model.HydraState
import com.hydraumc.control.network.ConditionalMode
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.JogLimits
import com.hydraumc.control.network.RobotSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Conditional writes (If-Match with the revision of the robot), the atomic table move and the audit record, against a
 * real local HTTP server: a write carries the revision the last answer gave, never for STOP, never while another write
 * to the robot is on its way, and a 412 changes nothing, says so and reads the robot again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobotViewModelConditionalTest {

    private lateinit var server: MockWebServer
    private val seen = CopyOnWriteArrayList<RecordedRequest>()
    private val bodies = CopyOnWriteArrayList<String>()

    @Volatile private var commandAnswer: () -> MockResponse = { MockResponse().setResponseCode(200).setBody("""{"success":true}""") }
    @Volatile private var claimAnswer: () -> MockResponse = { MockResponse().setResponseCode(200).setBody("""{"success":true}""") }
    @Volatile private var robotAnswer: () -> MockResponse = { MockResponse().setResponseCode(404).setBody("{}") }
    @Volatile private var auditAnswer: () -> MockResponse = { MockResponse().setResponseCode(404).setBody("{}") }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                bodies += request.body.clone().readUtf8()
                val path = request.path ?: ""
                return when {
                    path.endsWith("/command") -> commandAnswer()
                    path.endsWith("/claim") || path.endsWith("/release") -> claimAnswer()
                    path.startsWith("/api/robot/1") && request.method == "GET" -> robotAnswer()
                    path.startsWith("/api/audit") -> auditAnswer()
                    path.startsWith("/api/claims") -> MockResponse().setResponseCode(200).setBody("""{"claims":[]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        try {
            server.shutdown()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun newViewModel(): RobotViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = RobotViewModel(application)
        viewModel.apiClient = HydraApiClient("127.0.0.1", server.port).also { it.authToken = "tok" }
        viewModel.state = HydraState(
            JSONObject("""{"activeControllerId":"c1","controllers":[{"id":"c1","robots":[{"id":1,"hasXYTable":true,"playbackState":{"isPlaying":false},"joints":{"j1":0}}]}]}"""),
        )
        viewModel.selectedRobotId.value = 1
        viewModel.setConditionalMode(ConditionalMode.STANDARD)
        return viewModel
    }

    private fun safety(maxAmount: Double = 20.0) =
        RobotSafety(robotId = 1, jog = JogLimits(maxAmount, 500.0), bedMaxC = 90.0, armRequired = false, armTtlMs = 60_000L, jogArmedUntil = null)

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }

    private fun requestsTo(suffix: String) = seen.withIndex().filter { it.value.path?.endsWith(suffix) == true && it.value.method == "POST" }

    private fun commandBodies() = requestsTo("/command").map { JSONObject(bodies[it.index]) }

    @Test
    fun `the first write has no If-Match, learns the revision, and the next one carries it quoted`() {
        val answers = ArrayDeque(listOf("rev-1", "rev-2"))
        commandAnswer = { MockResponse().setResponseCode(200).setBody("""{"success":true,"revision":"${answers.removeFirstOrNull() ?: "rev-x"}"}""") }
        val viewModel = newViewModel()

        viewModel.sendCommand("play")
        waitUntil { viewModel.revisions.known(1) == "rev-1" }
        assertNull(requestsTo("/command")[0].value.getHeader("If-Match"))

        viewModel.sendCommand("pause")
        waitUntil { viewModel.revisions.known(1) == "rev-2" }
        assertEquals("\"rev-1\"", requestsTo("/command")[1].value.getHeader("If-Match"))
    }

    @Test
    fun `a jog is unconditional in the standard mode and conditional in the strict one`() {
        val viewModel = newViewModel()
        viewModel.revisions.learn(1, "r0")

        viewModel.jog("robot", "x", 1.0)
        waitUntil { requestsTo("/command").size == 1 }
        assertNull(requestsTo("/command")[0].value.getHeader("If-Match"))

        // The answer carried no revision, so what was known was forgotten; learn one again and ask for the strict mode.
        waitUntil { viewModel.jogArmedUntilMs.value.isEmpty() && viewModel.revisions.known(1) == null }
        viewModel.setConditionalMode(ConditionalMode.STRICT)
        viewModel.revisions.learn(1, "r9")
        Thread.sleep(150)
        viewModel.jog("robot", "x", 1.0)
        waitUntil { requestsTo("/command").size == 2 }
        assertEquals("\"r9\"", requestsTo("/command")[1].value.getHeader("If-Match"))
    }

    @Test
    fun `STOP never carries If-Match`() {
        val viewModel = newViewModel()
        viewModel.setConditionalMode(ConditionalMode.STRICT)
        viewModel.revisions.learn(1, "r1")

        viewModel.sendCommand("stop")
        waitUntil { requestsTo("/command").isNotEmpty() }

        assertNull(requestsTo("/command")[0].value.getHeader("If-Match"))
    }

    @Test
    fun `with the check off nothing carries If-Match`() {
        val viewModel = newViewModel()
        viewModel.setConditionalMode(ConditionalMode.OFF)
        viewModel.revisions.learn(1, "r1")

        viewModel.sendCommand("play")
        waitUntil { requestsTo("/command").isNotEmpty() }

        assertNull(requestsTo("/command")[0].value.getHeader("If-Match"))
    }

    @Test
    fun `a 412 says so, undoes the change, and reads the robot again`() {
        commandAnswer = {
            MockResponse().setResponseCode(412).setBody("""{"error":"the robot changed since you read it","code":"revision-mismatch","revision":"now-rev"}""")
        }
        robotAnswer = { MockResponse().setResponseCode(200).setBody("""{"robot":{"id":1,"valves":[true,false]},"revision":"now-rev","cellId":"c1"}""") }
        val viewModel = newViewModel()
        viewModel.revisions.learn(1, "stale")

        viewModel.sendCommand("play")
        waitUntil { viewModel.lastError.value != null }

        val message = viewModel.lastError.value!!
        assertTrue(message, message.contains("changed since"))
        assertTrue("a refusal that applied nothing", message.contains("NOT applied"))
        assertEquals("\"stale\"", requestsTo("/command")[0].value.getHeader("If-Match"))
        waitUntil { seen.any { it.method == "GET" && it.path == "/api/robot/1" } }
        waitUntil { viewModel.revisions.known(1) == "now-rev" }
        waitUntil { viewModel.state.robotById(1)?.raw?.optJSONArray("valves")?.length() == 2 }
        assertFalse("the play was undone", viewModel.state.robotById(1)!!.isPlaying)
        assertEquals("the rest of the robot is kept", true, viewModel.state.robotById(1)!!.raw.optBoolean("hasXYTable"))
    }

    @Test
    fun `reserving and releasing carry the revision, a take-over does not`() {
        claimAnswer = { MockResponse().setResponseCode(200).setBody("""{"success":true,"revision":"after-claim"}""") }
        val viewModel = newViewModel()

        viewModel.claimSelectedRobot()
        waitUntil { viewModel.revisions.known(1) == "after-claim" }
        assertNull(requestsTo("/claim")[0].value.getHeader("If-Match"))

        viewModel.releaseSelectedRobot()
        waitUntil { requestsTo("/release").isNotEmpty() }
        assertEquals("\"after-claim\"", requestsTo("/release")[0].value.getHeader("If-Match"))
    }

    @Test
    fun `a 412 on a release is said and the robot is read again`() {
        claimAnswer = { MockResponse().setResponseCode(412).setBody("""{"error":"changed","code":"revision-mismatch","revision":"cur"}""") }
        robotAnswer = { MockResponse().setResponseCode(200).setBody("""{"robot":{"id":1},"revision":"cur"}""") }
        val viewModel = newViewModel()
        viewModel.revisions.learn(1, "old")

        viewModel.releaseSelectedRobot()
        waitUntil { viewModel.lastError.value != null }

        assertTrue(viewModel.lastError.value!!.contains("changed since"))
        waitUntil { viewModel.revisions.known(1) == "cur" }
    }

    // ---- the XY table ----

    @Test
    fun `a jog of the XY table is one relative table command`() {
        val viewModel = newViewModel()

        viewModel.jog("xytable", "x", 5.0)
        waitUntil { requestsTo("/command").isNotEmpty() }

        val sent = commandBodies()[0]
        assertEquals("table", sent.getString("command"))
        val params = sent.getJSONObject("params")
        assertEquals(5.0, params.getDouble("x"), 0.0)
        assertEquals(0.0, params.getDouble("y"), 0.0)
        assertEquals("relative", params.getString("mode"))
        assertEquals(5.0, viewModel.state.robotById(1)!!.xyTablePos.optDouble("x"), 0.0)
    }

    @Test
    fun `a table move above the limit of the model is not sent`() {
        val viewModel = newViewModel()
        viewModel.safetyByRobot.value = mapOf(1 to safety(maxAmount = 5.0))

        viewModel.jog("xytable", "y", 10.0)

        assertNotNull(viewModel.lastError.value)
        assertTrue(viewModel.lastError.value!!, viewModel.lastError.value!!.contains("limit of 5"))
        Thread.sleep(200)
        assertTrue("nothing may reach the server", requestsTo("/command").isEmpty())
    }

    @Test
    fun `a server that does not know the table command gets the jogs it understands, and is remembered`() {
        val calls = AtomicInteger(0)
        commandAnswer = {
            if (calls.getAndIncrement() == 0) MockResponse().setResponseCode(400).setBody("""{"error":"unknown command"}""")
            else MockResponse().setResponseCode(200).setBody("""{"success":true}""")
        }
        val viewModel = newViewModel()

        viewModel.jog("xytable", "x", 5.0)
        waitUntil { requestsTo("/command").size == 2 }
        val first = commandBodies()
        assertEquals("table", first[0].getString("command"))
        assertEquals("jog", first[1].getString("command"))
        assertEquals("xytable", first[1].getJSONObject("params").getString("target"))
        assertEquals(5.0, first[1].getJSONObject("params").getDouble("amount"), 0.0)

        Thread.sleep(300)
        viewModel.jog("xytable", "y", 2.0)
        waitUntil { requestsTo("/command").size == 3 }
        assertEquals("the next move goes straight to the jogs", "jog", commandBodies()[2].getString("command"))
    }

    // ---- the audit record ----

    @Test
    fun `the audit record is read with its filter and the chain is checked when asked`() {
        val line = """{"seq":1,"ts":"2030-01-01T00:00:00.000Z","type":"claim","outcome":"ok","username":"ana","prev":"${"0".repeat(64)}"}"""
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(line.toByteArray()).joinToString("") { "%02x".format(it) }
        val stored = line.dropLast(1) + ",\"hash\":\"$digest\"}"
        auditAnswer = {
            MockResponse().setResponseCode(200).setBody("""{"enabled":true,"lockedOff":false,"entries":[$stored],"verification":{"ok":true,"entries":1}}""")
        }
        val viewModel = newViewModel()

        viewModel.refreshAudit(verify = true, type = "claim", robotId = 1, limit = 50)
        waitUntil { viewModel.auditRead.value != null }

        val asked = seen.first { it.path?.startsWith("/api/audit") == true }.path
        assertEquals("/api/audit?limit=50&type=claim&robotId=1&verify=1", asked)
        assertEquals(1, viewModel.auditRead.value!!.lines.size)
        assertTrue(viewModel.auditChain.value!!.ok)
        assertNull(viewModel.auditFailure.value)
        assertTrue(viewModel.auditRead.value!!.verification!!.ok)
    }

    @Test
    fun `an account that is not an administrator, or an older server, is told so`() {
        auditAnswer = { MockResponse().setResponseCode(403).setBody("""{"error":"admin only"}""") }
        val viewModel = newViewModel()

        viewModel.refreshAudit(verify = false, type = null, robotId = null, limit = 100)
        waitUntil { viewModel.auditFailure.value != null }
        assertEquals(AuditFailure.FORBIDDEN, viewModel.auditFailure.value)
        assertNull(viewModel.auditRead.value)

        auditAnswer = { MockResponse().setResponseCode(404).setBody("""{"error":"no","code":"audit-unavailable"}""") }
        viewModel.refreshAudit(verify = false, type = null, robotId = null, limit = 100)
        waitUntil { viewModel.auditFailure.value == AuditFailure.UNAVAILABLE }
    }

    @Test
    fun `the audit lines are exported as the server stores them`() {
        auditAnswer = { MockResponse().setResponseCode(200).setBody("{\"seq\":1}\n") }
        val viewModel = newViewModel()
        var exported: String? = "unset"

        viewModel.exportAudit(type = null, robotId = null, limit = 10) { exported = it }
        waitUntil { exported != "unset" }

        assertEquals("{\"seq\":1}\n", exported)
        assertTrue(seen.any { it.path == "/api/audit?limit=10&format=jsonl" })
    }

    // ---- the pushed reservations ----

    @Test
    fun `a recent push lets the poll of the reservations rest`() {
        val viewModel = newViewModel()
        assertFalse(viewModel.claimsArePushed())
        assertEquals(0L, viewModel.claimsPushedAtMs)
        viewModel.refreshAccess(fromPoll = true)
        waitUntil { seen.any { it.path?.startsWith("/api/claims") == true } }
    }
}
