// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - reservations, safety limits and jog arming (the server's contract)
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Pure data and rules, no Android and no network, so they are tested on the JVM:
//   - the controller id of this installation (a reservation belongs to the account AND to the
//     controller that made it, so two clients of one account no longer share one reservation),
//   - the reservations listed by GET /api/claims,
//   - the limits of GET /api/safety-limits?robotId=, and the check that keeps a jog above the
//     limit of the robot's model from being sent at all (the server checks it again).
package com.hydraumc.control.network

import com.hydraumc.control.util.optStr
import org.json.JSONObject
import android.util.Base64
import java.util.UUID

/**
 * The controller id this app sends in `claim`, `release`, `jog-arm` and `command` (body and
 * `X-Controller-Id` header): 1 to 64 characters of letters, digits and `. _ : -`. It is made once
 * per installation and kept (see ConnectionPrefs), so a reservation survives the app being
 * closed and can still be released from the same phone.
 */
object SessionController {
    private val pattern = Regex("^[A-Za-z0-9._:-]{1,64}$")

    @Volatile
    var id: String = generate()
        private set

    fun generate(): String = "android-" + UUID.randomUUID().toString().replace("-", "").take(12)

    fun isValid(candidate: String?): Boolean = candidate != null && pattern.matches(candidate)

    /** Adopts a stored id when it is valid; an unusable one is ignored and the current id stays. */
    fun adopt(stored: String?) {
        if (isValid(stored)) id = stored!!
    }
}

/** One active reservation of GET /api/claims. */
data class ClaimInfo(
    val robotId: Int,
    val robotName: String,
    val ownerUsername: String,
    /** The controller that holds it; null for a reservation made without one. */
    val controllerId: String?,
    /** Epoch milliseconds, the server's clock. */
    val expiresAt: Long,
) {
    companion object {
        fun listFromJson(json: JSONObject): List<ClaimInfo> {
            val array = json.optJSONArray("claims") ?: return emptyList()
            val out = mutableListOf<ClaimInfo>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (!item.has("robotId")) continue
                out += ClaimInfo(
                    robotId = item.optInt("robotId"),
                    robotName = item.optStr("robotName"),
                    ownerUsername = item.optStr("ownerUsername"),
                    controllerId = item.optStr("controllerId").ifEmpty { null },
                    expiresAt = item.optLong("expiresAt"),
                )
            }
            return out
        }
    }
}

/** Where a robot stands for this installation. */
enum class ClaimStanding { FREE, MINE_HERE, MINE_ELSEWHERE, OTHER }

/**
 * [claim] is the active reservation of the robot (null when free). The same account on another
 * controller is [ClaimStanding.MINE_ELSEWHERE]: the server refuses it like another account until
 * it is released there. A reservation made without a controller id is the account's own and
 * answers to any controller.
 */
fun claimStanding(claim: ClaimInfo?, myUsername: String, myControllerId: String): ClaimStanding = when {
    claim == null -> ClaimStanding.FREE
    // The controller id is random per installation: only this phone can hold a claim under it.
    claim.controllerId == myControllerId -> ClaimStanding.MINE_HERE
    myUsername.isEmpty() || claim.ownerUsername != myUsername -> ClaimStanding.OTHER
    claim.controllerId == null -> ClaimStanding.MINE_HERE
    else -> ClaimStanding.MINE_ELSEWHERE
}

/**
 * The role the session token carries (`admin` or `operator`), or null when it cannot be read. The server
 * checks the role again on every request; this only decides whether the take-over button is shown.
 */
fun jwtRole(token: String?): String? {
    val parts = token?.split('.') ?: return null
    if (parts.size < 2) return null
    return try {
        val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
        JSONObject(payload).optString("role").ifBlank { null }
    } catch (_: Exception) {
        null
    }
}

/** Whether the take-over button applies: the session is an administrator's and someone else holds the robot. */
fun canTakeOver(role: String?, standing: ClaimStanding): Boolean =
    role == "admin" && (standing == ClaimStanding.OTHER || standing == ClaimStanding.MINE_ELSEWHERE)

/** The size of a jog and the range of an absolute jog the server allows for one model. */
data class JogLimits(val maxAmount: Double, val maxAbsolute: Double)

/** The limits that apply to one robot, from GET /api/safety-limits?robotId=. */
data class RobotSafety(
    val robotId: Int,
    val jog: JogLimits,
    val bedMaxC: Double,
    /** The administrator requires the jog to be armed before it moves this robot. */
    val armRequired: Boolean,
    val armTtlMs: Long,
    /** Epoch milliseconds (the server's clock) until which the jog of this robot is armed for this controller; null when it is not. */
    val jogArmedUntil: Long?,
) {
    companion object {
        /** Null when the answer carries no limits for a robot (an older server, or no robotId asked). */
        fun fromJson(json: JSONObject): RobotSafety? {
            val robot = json.optJSONObject("robot") ?: return null
            val jog = robot.optJSONObject("jog") ?: return null
            val maxAmount = jog.optDouble("maxAmount", Double.NaN)
            val maxAbsolute = jog.optDouble("maxAbsolute", Double.NaN)
            if (maxAmount.isNaN() || maxAbsolute.isNaN() || maxAmount <= 0 || maxAbsolute <= 0) return null
            val global = json.optJSONObject("limits")?.optJSONObject("jog")
            return RobotSafety(
                robotId = robot.optInt("id"),
                jog = JogLimits(maxAmount, maxAbsolute),
                bedMaxC = robot.optJSONObject("temperature")?.optDouble("bedMaxC", Double.NaN)?.takeIf { !it.isNaN() } ?: 0.0,
                armRequired = global?.optBoolean("armRequired", false) ?: false,
                armTtlMs = global?.optLong("armTtlMs", 60_000L) ?: 60_000L,
                jogArmedUntil = if (robot.isNull("jogArmedUntil")) null else robot.optLong("jogArmedUntil").takeIf { it > 0 },
            )
        }
    }
}

/**
 * The limit a jog of [amount] exceeds, or null when it is within [limits] (or the limits are not
 * known: the server then decides). A relative jog is bounded by `maxAmount`; an absolute target,
 * in either direction, by `maxAbsolute`.
 */
fun jogLimitExceeded(amount: Double, limits: JogLimits?, absolute: Boolean = false): Double? {
    if (limits == null) return null
    val limit = if (absolute) limits.maxAbsolute else limits.maxAmount
    return if (amount.isNaN() || kotlin.math.abs(amount) > limit) limit else null
}
