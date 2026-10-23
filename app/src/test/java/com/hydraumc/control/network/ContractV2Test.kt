// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/network/ContractV2Test.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest

/** Conditional writes, the atomic table move and the audit record: pure rules (org.json needs the Robolectric runner). */
@RunWith(RobolectricTestRunner::class)
class ContractV2Test {

    // ---- which commands carry If-Match ----

    @Test
    fun `STOP is never conditional whatever the mode`() {
        for (mode in ConditionalMode.values()) assertFalse(mode.key, isConditional(mode, "stop"))
    }

    @Test
    fun `standard leaves out the commands that repeat many times a second`() {
        assertFalse(isConditional(ConditionalMode.STANDARD, "jog"))
        assertFalse(isConditional(ConditionalMode.STANDARD, "speed"))
        assertTrue(isConditional(ConditionalMode.STANDARD, "table"))
        assertTrue(isConditional(ConditionalMode.STANDARD, "claim"))
        assertTrue(isConditional(ConditionalMode.STANDARD, "play"))
    }

    @Test
    fun `strict checks everything but STOP and off checks nothing`() {
        assertTrue(isConditional(ConditionalMode.STRICT, "jog"))
        assertFalse(isConditional(ConditionalMode.OFF, "table"))
    }

    @Test
    fun `the mode is read from its key and falls back to standard`() {
        assertEquals(ConditionalMode.STRICT, ConditionalMode.fromKey("strict"))
        assertEquals(ConditionalMode.OFF, ConditionalMode.fromKey("off"))
        assertEquals(ConditionalMode.STANDARD, ConditionalMode.fromKey("nonsense"))
        assertEquals(ConditionalMode.STANDARD, ConditionalMode.fromKey(null))
    }

    // ---- the revision ----

    @Test
    fun `the revision is read from a body and nothing else`() {
        assertEquals("abc123", revisionOf(JSONObject("""{"revision":"abc123"}""")))
        assertNull(revisionOf(JSONObject("""{"revision":12}""")))
        assertNull(revisionOf(JSONObject("""{"revision":"has space"}""")))
        assertNull(revisionOf(JSONObject("""{"revision":null}""")))
        assertNull(revisionOf(null))
        assertEquals("\"abc\"", ifMatchHeader("abc"))
    }

    @Test
    fun `a 412 is a revision mismatch and carries the current revision`() {
        val error = HydraApiException("HTTP 412", httpCode = 412, serverCode = "revision-mismatch", serverMessage = "changed", revision = "now")
        assertTrue(error.isRevisionMismatch)
        assertEquals("now", error.revision)
        assertFalse(error.isReservedByAnother)
        assertFalse(HydraApiException("HTTP 409", httpCode = 409).isRevisionMismatch)
    }

    // ---- the tracker ----

    @Test
    fun `nothing is sent until a revision is known and then the last one`() {
        val tracker = RevisionTracker { ConditionalMode.STANDARD }
        val first = tracker.begin(1, "play")
        assertNull(first.ifMatch)
        tracker.finish(1, first.seq, "r1")
        assertEquals("r1", tracker.known(1))
        assertEquals("\"r1\"", tracker.begin(1, "play").ifMatch)
    }

    @Test
    fun `nothing is sent while another write to the same robot is on its way`() {
        val tracker = RevisionTracker { ConditionalMode.STRICT }
        tracker.learn(1, "r1")
        val a = tracker.begin(1, "table")
        val b = tracker.begin(1, "table")
        assertEquals("\"r1\"", a.ifMatch)
        assertNull(b.ifMatch)
        tracker.finish(1, a.seq, "r2")
        tracker.finish(1, b.seq, "r3")
        assertEquals("r3", tracker.known(1))
    }

    @Test
    fun `answers that come back out of order keep the newest revision`() {
        val tracker = RevisionTracker { ConditionalMode.STRICT }
        val a = tracker.begin(1, "play")
        val b = tracker.begin(1, "play")
        tracker.finish(1, b.seq, "newer")
        tracker.finish(1, a.seq, "older")
        assertEquals("newer", tracker.known(1))
    }

    @Test
    fun `an answer without a revision forgets what was known`() {
        val tracker = RevisionTracker { ConditionalMode.STANDARD }
        tracker.learn(1, "r1")
        val t = tracker.begin(1, "play")
        tracker.finish(1, t.seq, null)
        assertNull(tracker.known(1))
    }

    @Test
    fun `robots are independent and the mode is read each time`() {
        var mode = ConditionalMode.STANDARD
        val tracker = RevisionTracker { mode }
        tracker.learn(1, "r1")
        assertNull(tracker.begin(2, "play").ifMatch)
        assertEquals("\"r1\"", tracker.begin(1, "play").ifMatch)
        tracker.finish(1, 1, "r1")
        mode = ConditionalMode.OFF
        assertNull(tracker.begin(1, "play").ifMatch)
        tracker.forget()
        assertNull(tracker.known(1))
    }

    // ---- the table move ----

    @Test
    fun `table parameters need numbers and a known mode`() {
        val params = tableParams(10.0, 20.0, "absolute")!!
        assertEquals(10.0, params.getDouble("x"), 0.0)
        assertEquals("absolute", params.getString("mode"))
        assertFalse(params.has("rz"))
        assertTrue(tableParams(1.0, 2.0, "relative", 5.0)!!.has("rz"))
        assertNull(tableParams(Double.NaN, 1.0, "absolute"))
        assertNull(tableParams(1.0, Double.POSITIVE_INFINITY, "absolute"))
        assertNull(tableParams(1.0, 2.0, "sideways"))
    }

    @Test
    fun `a relative table move is bounded by the largest jog and an absolute one by the largest position`() {
        val limits = JogLimits(maxAmount = 100.0, maxAbsolute = 1000.0)
        assertNull(tableLimitExceeded(tableParams(100.0, 0.0, "relative")!!, limits))
        assertEquals(100.0, tableLimitExceeded(tableParams(0.0, -101.0, "relative")!!, limits)!!, 0.0)
        assertNull(tableLimitExceeded(tableParams(1000.0, -1000.0, "absolute")!!, limits))
        assertEquals(1000.0, tableLimitExceeded(tableParams(1001.0, 0.0, "absolute")!!, limits)!!, 0.0)
    }

    @Test
    fun `a relative move is also bounded by where it ends and unknown limits decide nothing`() {
        val limits = JogLimits(maxAmount = 100.0, maxAbsolute = 1000.0)
        assertEquals(1000.0, tableLimitExceeded(tableParams(50.0, 0.0, "relative")!!, limits, currentX = 980.0)!!, 0.0)
        assertNull(tableLimitExceeded(tableParams(50.0, 0.0, "relative")!!, limits, currentX = 900.0))
        assertNull(tableLimitExceeded(tableParams(5000.0, 0.0, "absolute")!!, null))
    }

    @Test
    fun `an older server is recognised by its answer`() {
        assertTrue(isUnknownCommand(HydraApiException("HTTP 400", httpCode = 400, serverMessage = "unknown command")))
        assertFalse(isUnknownCommand(HydraApiException("HTTP 400", httpCode = 400, serverCode = "table-invalid", serverMessage = "x and y must be finite numbers")))
        assertFalse(isUnknownCommand(HydraApiException("HTTP 500", httpCode = 500, serverMessage = "unknown command")))
    }

    @Test
    fun `the table move turned into the jogs it did before`() {
        val absolute = tableAsJogs(tableParams(10.0, 20.0, "absolute")!!)
        assertEquals(2, absolute.size)
        assertEquals("x", absolute[0].getString("axis"))
        assertEquals("xytable", absolute[0].getString("target"))
        assertTrue(absolute[0].getBoolean("absolute"))
        val relative = tableAsJogs(tableParams(2.0, 0.0, "relative")!!)
        assertEquals(1, relative.size)
        assertFalse(relative[0].has("absolute"))
    }

    // ---- the audit record ----

    private val genesis = "0".repeat(64)

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Lines written the way the server writes them: compact JSON, the hash of the line without `hash`, which is added last. */
    private fun chain(count: Int, start: Int = 1, username: String = "ana"): List<String> {
        val lines = mutableListOf<String>()
        var prev = genesis
        for (i in 0 until count) {
            val body = """{"seq":${start + i},"ts":"2030-01-01T00:00:0${i}.000Z","type":"claim","outcome":"ok","username":"$username","controllerId":"w1","ip":"10.0.0.5","robotId":1,"detail":{"ttlMs":60000,"note":"a } and a ] and a \" inside"},"prev":"$prev"}"""
            val digest = sha256(body)
            lines += body.dropLast(1) + ",\"hash\":\"$digest\"}"
            prev = digest
        }
        return lines
    }

    private fun answer(lines: List<String>, extra: String = "") = """{"enabled":true,"lockedOff":false,"entries":[${lines.joinToString(",")}]$extra}"""

    @Test
    fun `the lines are split exactly as the server wrote them, even with braces and quotes inside strings`() {
        val lines = chain(3)
        assertEquals(lines, AuditRecord.splitEntries(answer(lines, ""","verification":{"ok":true,"entries":3}""")))
        assertTrue(AuditRecord.splitEntries("""{"enabled":true,"entries":[]}""").isEmpty())
        assertTrue(AuditRecord.splitEntries("not json").isEmpty())
    }

    @Test
    fun `an entries key inside a string or a nested object is not the list`() {
        val body = """{"note":"\"entries\":[{\"x\":1}]","nested":{"entries":[{"y":2}]},"entries":[{"seq":1}]}"""
        assertEquals(listOf("""{"seq":1}"""), AuditRecord.splitEntries(body))
    }

    @Test
    fun `the answer is read with its fields and the verdict of the server`() {
        val read = AuditRecord.parse(answer(chain(2), ""","verification":{"ok":false,"entries":2,"brokenAtSeq":2,"reason":"a line was changed"}"""))
        assertTrue(read.enabled)
        assertEquals(2, read.lines.size)
        assertEquals("ana", read.lines[0].username)
        assertEquals(1, read.lines[0].robotId)
        assertTrue(read.lines[0].detail.contains("ttlMs=60000"))
        assertNotNull(read.lines[0].hashed)
        assertEquals(false, read.verification!!.ok)
        assertEquals(2L, read.verification!!.brokenAtSeq)
        assertEquals("a line was changed", read.verification!!.reason)
        val off = AuditRecord.parse("""{"enabled":false,"lockedOff":true,"entries":[]}""")
        assertFalse(off.enabled)
        assertTrue(off.lockedOff)
        assertTrue(AuditRecord.parse("not json").lines.isEmpty())
    }

    @Test
    fun `an intact chain passes and a window in the middle of the record too`() {
        val check = AuditRecord.checkChain(AuditRecord.parse(answer(chain(5))).lines)
        assertTrue(check.ok)
        assertEquals(5, check.checked)
        assertEquals(4, check.links)
        assertEquals(0, check.gaps)
        assertTrue(AuditRecord.checkChain(AuditRecord.parse(answer(chain(4, start = 100))).lines).ok)
    }

    @Test
    fun `a changed line is found`() {
        val lines = chain(4).toMutableList()
        lines[2] = lines[2].replace("\"username\":\"ana\"", "\"username\":\"mallory\"")
        val check = AuditRecord.checkChain(AuditRecord.parse(answer(lines)).lines)
        assertFalse(check.ok)
        assertEquals(3L, check.brokenAtSeq)
        assertEquals("changed", check.reason)
    }

    @Test
    fun `a replaced line whose successor still names the old one is found`() {
        val lines = chain(3)
        val body = lines[1].substringBefore(",\"hash\":\"") + "}"
        val rewritten = body.replace("\"username\":\"ana\"", "\"username\":\"someone-else\"")
        val replaced = rewritten.dropLast(1) + ",\"hash\":\"" + sha256(rewritten) + "\"}"
        val check = AuditRecord.checkChain(AuditRecord.parse(answer(listOf(lines[0], replaced, lines[2]))).lines)
        assertFalse(check.ok)
        assertEquals(3L, check.brokenAtSeq)
        assertEquals("link", check.reason)
    }

    @Test
    fun `a filtered view is not called broken, its holes are only counted`() {
        val lines = chain(6)
        val check = AuditRecord.checkChain(AuditRecord.parse(answer(listOf(lines[0], lines[2], lines[5]))).lines)
        assertTrue(check.ok)
        assertEquals(0, check.links)
        assertEquals(2, check.gaps)
    }

    @Test
    fun `a line that cannot be re-read byte for byte is counted, not called broken`() {
        val pretty = chain(1)[0].replace("\",\"", "\",\n\"")
        val check = AuditRecord.checkChain(AuditRecord.parse(answer(listOf(pretty))).lines)
        assertTrue(check.ok)
        assertEquals(1, check.unreadable)
    }

    @Test
    fun `the query string keeps the limit in range and only known types`() {
        assertEquals("", AuditRecord.query())
        assertEquals("?limit=1000&type=claim&robotId=2&verify=1", AuditRecord.query(5000, "claim", 2, true))
        assertEquals("?limit=1", AuditRecord.query(0))
        assertEquals("?limit=100", AuditRecord.query(100, "no-such-type"))
        assertEquals("?limit=10&format=jsonl", AuditRecord.query(10, format = "jsonl"))
    }

    @Test
    fun `a reservation carries when the jog of its robot is armed`() {
        val claims = ClaimInfo.listFromJson(
            JSONObject("""{"claims":[{"robotId":1,"ownerUsername":"ana","controllerId":"a","expiresAt":9,"jogArmedUntil":5000},{"robotId":2,"ownerUsername":"bob","jogArmedUntil":null}]}"""),
        )
        assertEquals(5000L, claims[0].jogArmedUntil)
        assertNull(claims[1].jogArmedUntil)
    }
}
