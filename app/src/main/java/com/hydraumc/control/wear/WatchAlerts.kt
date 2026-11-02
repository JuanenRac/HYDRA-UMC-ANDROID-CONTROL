// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - Alerts the phone pushes to the watch: what is worth one, and how many
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import com.hydraumc.control.network.RobotCommandContract
import org.json.JSONObject

/** How serious an alert is; the watch vibrates by it (CRITICAL stays until opened). */
enum class WatchAlertSeverity { CRITICAL, WARNING, INFO }

/** The message the watch accepts on `/hydra-umc/alert/v1`: a severity and 1 to [RobotCommandContract.WATCH_ALERTS_MESSAGE_MAX] characters. */
data class WatchAlert(val severity: WatchAlertSeverity, val message: String) {
    init {
        require(message.isNotBlank() && message.length <= RobotCommandContract.WATCH_ALERTS_MESSAGE_MAX) {
            "an alert message has 1 to ${RobotCommandContract.WATCH_ALERTS_MESSAGE_MAX} characters"
        }
    }

    fun toJson(): JSONObject = JSONObject().put("type", "alert").put("severity", severity.name).put("message", message)

    companion object {
        /** The alert for [message], cut with an ellipsis when it is too long and never blank. */
        fun of(severity: WatchAlertSeverity, message: String): WatchAlert {
            val max = RobotCommandContract.WATCH_ALERTS_MESSAGE_MAX
            val text = message.trim().ifEmpty { "!" }
            return WatchAlert(severity, if (text.length > max) text.take(max - 1) + "…" else text)
        }
    }
}

/** Where an alert goes (the Wear Data Layer in the app, a fake in the tests). */
fun interface WatchAlertSink {
    /** Returns true when at least one watch took the message. */
    suspend fun send(alert: WatchAlert): Boolean
}

/** What the phone knows about one robot at the moment of an observation. */
data class WatchAlertRobot(val id: Int, val name: String, val online: Boolean, val enabled: Boolean, val playing: Boolean)

/** The progress of the global STOP, reduced to what matters here. */
enum class WatchAlertStop { NONE, SENDING, NOT_CONFIRMED, DONE }

data class WatchAlertInput(val connected: Boolean, val robots: List<WatchAlertRobot>, val stop: WatchAlertStop)

/** What happened, without words: the app turns it into a sentence in the language of the phone. */
enum class WatchAlertKind { CONNECTION_LOST, CONNECTION_RESTORED, ROBOT_OFFLINE, ROBOT_SAFE_MODE, STOP_NOT_CONFIRMED, STOP_CONFIRMED }

data class WatchAlertEvent(val kind: WatchAlertKind, val severity: WatchAlertSeverity, val robotName: String? = null, val key: String)

/**
 * Looks at the phone's view of the plant over time and says which changes deserve an alert on the wrist: the
 * connection to the server lost (and back), a robot that went offline or into safe mode, a STOP that the server did
 * not confirm (and the one that was). It holds no network and no Android class.
 *
 * Rationing, so a burst never floods the wrist: the same event is not repeated within the dedupe window; the
 * rest are spaced by a minimum gap and capped per minute. A CRITICAL alert is never held back by the gap or the cap
 * (only by the dedupe), because it is the one that must arrive. The numbers come from docs/robot_command_contract.json.
 */
class WatchAlertRules(
    private val clock: () -> Long = System::currentTimeMillis,
    private val dedupeWindowMs: Long = RobotCommandContract.WATCH_ALERTS_DEDUPE_WINDOW_MS,
    private val minGapMs: Long = RobotCommandContract.WATCH_ALERTS_MIN_GAP_MS,
    private val maxPerMinute: Int = RobotCommandContract.WATCH_ALERTS_MAX_PER_MINUTE,
    private val connectionLostAfterMs: Long = RobotCommandContract.WATCH_ALERTS_CONNECTION_LOST_AFTER_MS,
) {
    private var initialised = false
    private var everConnected = false
    private var lostSince: Long? = null
    private var lostAnnounced = false
    private var playingWhenLost = false
    private var robots: Map<Int, WatchAlertRobot> = emptyMap()
    private var stop = WatchAlertStop.NONE

    private val lastSentByKey = HashMap<String, Long>()
    private val sentAt = ArrayDeque<Long>()
    private var lastSentAny = Long.MIN_VALUE / 2

    /** The events that deserve an alert now. Call it often (about once a second); the first call only learns the starting point. */
    @Synchronized
    fun observe(input: WatchAlertInput): List<WatchAlertEvent> {
        val now = clock()
        val found = mutableListOf<WatchAlertEvent>()
        if (!initialised) {
            initialised = true
            everConnected = input.connected
            robots = input.robots.associateBy { it.id }
            stop = input.stop
            return emptyList()
        }
        if (input.connected) {
            everConnected = true
            if (lostAnnounced) found += WatchAlertEvent(WatchAlertKind.CONNECTION_RESTORED, WatchAlertSeverity.INFO, key = "restored")
            lostSince = null
            lostAnnounced = false
            val now2 = input.robots.associateBy { it.id }
            for ((id, before) in robots) {
                val after = now2[id] ?: continue
                if (before.online && !after.online) {
                    val severity = if (before.playing) WatchAlertSeverity.CRITICAL else WatchAlertSeverity.WARNING
                    found += WatchAlertEvent(WatchAlertKind.ROBOT_OFFLINE, severity, after.name, "offline:$id")
                } else if (after.online && before.enabled && !after.enabled) {
                    found += WatchAlertEvent(WatchAlertKind.ROBOT_SAFE_MODE, WatchAlertSeverity.WARNING, after.name, "safe:$id")
                }
            }
            robots = now2
        } else if (everConnected) {
            val since = lostSince
            if (since == null) {
                lostSince = now
                playingWhenLost = robots.values.any { it.playing }
            } else if (!lostAnnounced && now - since >= connectionLostAfterMs) {
                lostAnnounced = true
                found += WatchAlertEvent(
                    WatchAlertKind.CONNECTION_LOST,
                    if (playingWhenLost) WatchAlertSeverity.CRITICAL else WatchAlertSeverity.WARNING,
                    key = "lost",
                )
            }
        }
        if (input.stop != stop) {
            if (input.stop == WatchAlertStop.NOT_CONFIRMED) {
                found += WatchAlertEvent(WatchAlertKind.STOP_NOT_CONFIRMED, WatchAlertSeverity.CRITICAL, key = "stop-not-confirmed")
            } else if (input.stop == WatchAlertStop.DONE && (stop == WatchAlertStop.SENDING || stop == WatchAlertStop.NOT_CONFIRMED)) {
                found += WatchAlertEvent(WatchAlertKind.STOP_CONFIRMED, WatchAlertSeverity.INFO, key = "stop-confirmed")
            }
            stop = input.stop
        }
        return ration(found, now)
    }

    private fun ration(events: List<WatchAlertEvent>, now: Long): List<WatchAlertEvent> {
        val out = mutableListOf<WatchAlertEvent>()
        // The gap is measured from the alerts of EARLIER observations: what was found together goes out together.
        val gapFrom = lastSentAny
        // The serious ones first, so they are never the ones that run into the cap.
        for (event in events.sortedBy { it.severity.ordinal }) {
            val last = lastSentByKey[event.key]
            if (last != null && now - last < dedupeWindowMs) continue
            if (event.severity != WatchAlertSeverity.CRITICAL) {
                while (sentAt.isNotEmpty() && now - sentAt.first() >= 60_000L) sentAt.removeFirst()
                if (now - gapFrom < minGapMs || sentAt.size >= maxPerMinute) continue
            }
            lastSentByKey[event.key] = now
            lastSentAny = now
            sentAt.addLast(now)
            out += event
        }
        return out
    }
}
