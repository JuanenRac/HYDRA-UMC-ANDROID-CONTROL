// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - conditional writes, the atomic table move and the audit record
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Pure rules, no Android and no network, tested on the JVM. The second contract of the server:
//   - every robot has a revision (GET /api/robot/:id, and every answer of a command, a reservation or a
//     release). A write that sends it back as If-Match is refused with 412 when the robot changed in
//     between, so this phone never overwrites what someone else did to the robot without knowing it.
//     [RevisionTracker] keeps the last revision seen and decides when it is sent;
//   - the `table` command moves the XY table in one request: [tableParams], [tableLimitExceeded] and,
//     for a server that predates it, [tableAsJogs];
//   - the audit record (GET /api/audit, administrators only) is an append-only chain of SHA-256 hashes:
//     [AuditRecord] reads it and checks the chain of the lines it was given.
package com.hydraumc.control.network

import com.hydraumc.control.util.optStr
import org.json.JSONObject
import java.security.MessageDigest

// ---- conditional writes ---------------------------------------------------------------------------------

/** Which writes carry If-Match. The default is [STANDARD]. */
enum class ConditionalMode(val key: String) {
    /** Nothing is checked. */
    OFF("off"),
    /** Reservations, the table and the one-off commands; the continuous ones (jog, speed) go unchecked. */
    STANDARD("standard"),
    /** Every command except STOP. */
    STRICT("strict");

    companion object {
        fun fromKey(key: String?): ConditionalMode = values().firstOrNull { it.key == key } ?: STANDARD
    }
}

/** Commands sent many times a second while a control is held: a revision can not follow them. */
val CONTINUOUS_COMMANDS: Set<String> = setOf("jog", "speed", "jogStep")

/** Whether a command carries If-Match in a mode. Reservations are "claim" and "release". STOP never does. */
fun isConditional(mode: ConditionalMode, command: String): Boolean = when {
    command == "stop" || mode == ConditionalMode.OFF -> false
    mode == ConditionalMode.STRICT -> true
    else -> command !in CONTINUOUS_COMMANDS
}

private val REVISION_PATTERN = Regex("^[A-Za-z0-9_-]{1,128}$")

/** The revision in the body of an answer (`revision`), or null. */
fun revisionOf(json: JSONObject?): String? {
    val value = json?.opt("revision") as? String ?: return null
    return if (REVISION_PATTERN.matches(value)) value else null
}

/** The value of the If-Match header for a revision (quoted, as an ETag is). */
fun ifMatchHeader(revision: String): String = "\"$revision\""

/** What [RevisionTracker.begin] gives back: the header to send (null for none) and the number of this write. */
data class RevisionTicket(val ifMatch: String?, val seq: Long)

/**
 * The last revision seen of each robot of one server. Call [begin] before a write and [finish] when it was answered.
 * It sends nothing when no revision is known (the first write learns it), when another write to the same robot is
 * still on its way (its answer is what makes the revision stale, not a change by someone else), or when the mode
 * says the command is not checked.
 */
class RevisionTracker(private val mode: () -> ConditionalMode = { ConditionalMode.STANDARD }) {
    private var counter = 0L
    private val known = HashMap<Int, Pair<String, Long>>()
    private val inFlight = HashMap<Int, Int>()

    @Synchronized
    fun begin(robotId: Int, command: String): RevisionTicket {
        counter += 1
        val seq = counter
        val busy = (inFlight[robotId] ?: 0) > 0
        inFlight[robotId] = (inFlight[robotId] ?: 0) + 1
        val entry = known[robotId]
        val ifMatch = if (!busy && entry != null && isConditional(mode(), command)) ifMatchHeader(entry.first) else null
        return RevisionTicket(ifMatch, seq)
    }

    /** The write was answered: keep the revision it carried (when it is the newest answer) and stop counting it. */
    @Synchronized
    fun finish(robotId: Int, seq: Long, revision: String?) {
        val left = (inFlight[robotId] ?: 1) - 1
        if (left <= 0) inFlight.remove(robotId) else inFlight[robotId] = left
        if (revision == null) {
            // An answer without a revision (a refusal, an older server): what was known may be stale now.
            if (left <= 0) known.remove(robotId)
            return
        }
        val entry = known[robotId]
        if (entry == null || seq >= entry.second) known[robotId] = revision to seq
    }

    /** A revision learned some other way (the 412 answer, GET /api/robot/:id). */
    @Synchronized
    fun learn(robotId: Int, revision: String?) {
        if (revision == null) {
            known.remove(robotId)
            return
        }
        counter += 1
        known[robotId] = revision to counter
    }

    @Synchronized
    fun forget(robotId: Int? = null) {
        if (robotId == null) known.clear() else known.remove(robotId)
    }

    @Synchronized
    fun known(robotId: Int): String? = known[robotId]?.first
}

/** One robot as GET /api/robot/:id gives it, with its revision (null from a server that predates revisions). */
data class RobotRead(val robot: JSONObject, val revision: String?)

// ---- the atomic move of the XY table ---------------------------------------------------------------------

private fun finite(value: Double) = !value.isNaN() && !value.isInfinite()

/** The parameters of a table move, or null when they are not numbers. [mode] is "absolute" or "relative". */
fun tableParams(x: Double, y: Double, mode: String, rz: Double? = null): JSONObject? {
    if (!finite(x) || !finite(y) || (rz != null && !finite(rz)) || (mode != "absolute" && mode != "relative")) return null
    val params = JSONObject().put("x", x).put("y", y).put("mode", mode)
    if (rz != null) params.put("rz", rz)
    return params
}

/**
 * The limit a table move exceeds, or null when it is within [limits] (or the limits are not known: the server then
 * decides). Every number of a relative move is bounded by the largest jog, every number of an absolute move by the
 * largest position, and the position a relative move ends in by the largest position too.
 */
fun tableLimitExceeded(params: JSONObject, limits: JogLimits?, currentX: Double = 0.0, currentY: Double = 0.0): Double? {
    if (limits == null) return null
    val relative = params.optString("mode") == "relative"
    val limit = if (relative) limits.maxAmount else limits.maxAbsolute
    val x = params.optDouble("x", Double.NaN)
    val y = params.optDouble("y", Double.NaN)
    val rz = if (params.has("rz")) params.optDouble("rz", Double.NaN) else null
    for (value in listOfNotNull(x, y, rz)) {
        if (!finite(value) || kotlin.math.abs(value) > limit) return limit
    }
    if (relative) {
        if (kotlin.math.abs(currentX + x) > limits.maxAbsolute || kotlin.math.abs(currentY + y) > limits.maxAbsolute) return limits.maxAbsolute
    }
    return null
}

/** The jogs that did the same on a server without the table command (one per axis that moves; no rotation). */
fun tableAsJogs(params: JSONObject): List<JSONObject> {
    val absolute = params.optString("mode") != "relative"
    val jogs = mutableListOf<JSONObject>()
    for (axis in listOf("x", "y")) {
        if (!params.has(axis)) continue
        val amount = params.optDouble(axis, Double.NaN)
        if (!finite(amount)) continue
        if (!absolute && amount == 0.0) continue
        val jog = JSONObject().put("axis", axis).put("amount", amount).put("target", "xytable")
        if (absolute) jog.put("absolute", true)
        jogs += jog
    }
    return jogs
}

/** An older server answers 400 "unknown command" to a command it does not have. */
fun isUnknownCommand(error: HydraApiException): Boolean =
    error.httpCode == 400 && error.serverCode == null && (error.serverMessage ?: "").contains("unknown command", ignoreCase = true)

// ---- the audit record --------------------------------------------------------------------------------------

/** One line of the audit record. */
data class AuditLine(
    val seq: Long,
    val ts: String,
    val type: String,
    val outcome: String,
    val username: String?,
    val controllerId: String?,
    val ip: String?,
    val robotId: Int?,
    /** The detail as `key=value` pairs, in key order. */
    val detail: String,
    val prev: String,
    val hash: String,
    /** The SHA-256 of the line as the server wrote it, without its hash; null when the line could not be re-read byte for byte. */
    val hashed: String?,
)

/** What the server says about the whole record when asked (`?verify=1`). */
data class AuditVerdict(val ok: Boolean, val entries: Int, val brokenAtSeq: Long?, val reason: String?)

data class AuditRead(
    val enabled: Boolean,
    val lockedOff: Boolean,
    val lines: List<AuditLine>,
    val verification: AuditVerdict?,
)

/** The outcome of checking the lines this phone was given. [reason] is "changed" or "link" when the chain is broken. */
data class ChainCheck(
    val ok: Boolean,
    val checked: Int,
    val links: Int,
    val gaps: Int,
    val unreadable: Int = 0,
    val brokenAtSeq: Long? = null,
    val reason: String? = null,
)

object AuditRecord {
    /** The types of line the server writes (the filter of the screen). */
    val TYPES: List<String> = listOf("claim", "force-claim", "release", "force-release", "jog-arm", "safety-limits", "stop-all")

    /** The sizes of the window of lines the screen offers (the server gives 1 to 1000). */
    val LIMITS: List<Int> = listOf(50, 100, 250, 1000)

    private val HASH_SUFFIX = Regex(""",\"hash\":\"([0-9a-f]{64})\"\}$""")

    /**
     * The raw text of each object of the top-level `entries` array, exactly as the server sent them: the hash of a
     * line is the SHA-256 of that text without its `hash` (the last key), so the text must not be re-serialised.
     */
    fun splitEntries(body: String): List<String> {
        var i = 0
        val n = body.length
        var depth = 0
        var lastKey: String? = null
        var arrayStart = -1
        while (i < n && arrayStart < 0) {
            when (body[i]) {
                '"' -> {
                    val (text, end) = readString(body, i)
                    var j = end
                    while (j < n && body[j].isWhitespace()) j++
                    if (depth == 1 && j < n && body[j] == ':') lastKey = text
                    i = end
                    continue
                }
                '{' -> depth++
                '[' -> {
                    if (depth == 1 && lastKey == "entries") {
                        arrayStart = i
                    } else {
                        depth++
                    }
                }
                '}', ']' -> depth--
                else -> Unit
            }
            i++
        }
        if (arrayStart < 0) return emptyList()
        val out = mutableListOf<String>()
        i = arrayStart + 1
        var objectDepth = 0
        var objectStart = -1
        while (i < n) {
            val c = body[i]
            if (c == '"') {
                i = readString(body, i).second
                continue
            }
            when (c) {
                '{' -> {
                    if (objectDepth == 0) objectStart = i
                    objectDepth++
                }
                '}' -> {
                    objectDepth--
                    if (objectDepth == 0 && objectStart >= 0) {
                        out += body.substring(objectStart, i + 1)
                        objectStart = -1
                    }
                }
                '[' -> objectDepth++
                ']' -> {
                    if (objectDepth == 0) return out
                    objectDepth--
                }
            }
            i++
        }
        return out
    }

    /** Reads the JSON string that starts at [start] (a quote); gives its text and the index just after it. */
    private fun readString(body: String, start: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var i = start + 1
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                sb.append(body[i + 1])
                i += 2
                continue
            }
            if (c == '"') return sb.toString() to (i + 1)
            sb.append(c)
            i++
        }
        return sb.toString() to body.length
    }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun parseLine(raw: String): AuditLine? {
        val json = try { JSONObject(raw) } catch (_: Exception) { return null }
        val hash = json.optStr("hash")
        val prev = json.optStr("prev")
        if (hash.isEmpty() || prev.isEmpty() || !json.has("seq")) return null
        val trimmed = raw.trim()
        val match = HASH_SUFFIX.find(trimmed)
        val hashed = if (match != null && !trimmed.contains('\n')) sha256Hex(trimmed.substring(0, match.range.first) + "}") else null
        val detail = json.optJSONObject("detail")
        val detailText = if (detail == null) "" else detail.keys().asSequence().sorted().joinToString(" ") { "$it=${detail.opt(it)}" }
        return AuditLine(
            seq = json.optLong("seq"),
            ts = json.optStr("ts"),
            type = json.optStr("type"),
            outcome = json.optStr("outcome", "ok"),
            username = json.optStr("username").ifEmpty { null },
            controllerId = json.optStr("controllerId").ifEmpty { null },
            ip = json.optStr("ip").ifEmpty { null },
            robotId = if (json.has("robotId") && !json.isNull("robotId")) json.optInt("robotId") else null,
            detail = if (detailText.length > 120) detailText.take(117) + "..." else detailText,
            prev = prev,
            hash = hash,
            hashed = hashed,
        )
    }

    /** Reads the answer of GET /api/audit: the lines (oldest first, as the server sends them) and what it says of the record. */
    fun parse(body: String): AuditRead {
        val json = try { JSONObject(body) } catch (_: Exception) { return AuditRead(true, false, emptyList(), null) }
        val verification = json.optJSONObject("verification")?.let { v ->
            AuditVerdict(
                ok = v.optBoolean("ok", false),
                entries = v.optInt("entries", 0),
                brokenAtSeq = if (v.has("brokenAtSeq") && !v.isNull("brokenAtSeq")) v.optLong("brokenAtSeq") else null,
                reason = v.optStr("reason").ifEmpty { null },
            )
        }
        return AuditRead(
            enabled = json.optBoolean("enabled", true),
            lockedOff = json.optBoolean("lockedOff", false),
            lines = splitEntries(body).mapNotNull(::parseLine),
            verification = verification,
        )
    }

    /** Checks the hash of every line and the link between lines that are neighbours (sequence numbers one apart). */
    fun checkChain(lines: List<AuditLine>): ChainCheck {
        var links = 0
        var gaps = 0
        var unreadable = 0
        for ((index, line) in lines.withIndex()) {
            val hashed = line.hashed
            if (hashed == null) unreadable++
            else if (hashed != line.hash) return ChainCheck(false, index, links, gaps, unreadable, line.seq, "changed")
            if (index == 0) continue
            val before = lines[index - 1]
            if (line.seq == before.seq + 1) {
                links++
                if (line.prev != before.hash) return ChainCheck(false, index + 1, links, gaps, unreadable, line.seq, "link")
            } else {
                gaps++
            }
        }
        return ChainCheck(true, lines.size, links, gaps, unreadable)
    }

    /** The query string of GET /api/audit for a filter (limit 1 to 1000). */
    fun query(limit: Int? = null, type: String? = null, robotId: Int? = null, verify: Boolean = false, format: String? = null): String {
        val pairs = mutableListOf<String>()
        if (limit != null) pairs += "limit=${limit.coerceIn(1, 1000)}"
        if (!type.isNullOrEmpty() && type in TYPES) pairs += "type=$type"
        if (robotId != null) pairs += "robotId=$robotId"
        if (verify) pairs += "verify=1"
        if (!format.isNullOrEmpty()) pairs += "format=$format"
        return if (pairs.isEmpty()) "" else "?" + pairs.joinToString("&")
    }
}
