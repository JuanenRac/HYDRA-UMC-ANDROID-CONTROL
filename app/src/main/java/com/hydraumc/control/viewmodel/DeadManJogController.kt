// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/DeadManJogController.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import androidx.compose.runtime.mutableStateOf
import com.hydraumc.control.network.CommandPoster
import com.hydraumc.control.network.DeadManJogRunner
import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.RobotCommandContract
import com.hydraumc.control.network.isUnknownCommand
import com.hydraumc.control.util.DeadManJog
import com.hydraumc.control.util.LinkHealth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What the screen sees of the continuous jog and of the health of the link: the switches (both start from the
 * contract, the jog OFF), the robots whose control is held, the level and the round-trip time of the link. It joins the
 * rules ([DeadManJog]), the runner that talks to the server and the link meter ([LinkHealth]); it holds no Android
 * class and says things only through the functions it is given, so it is tested on the JVM.
 *
 * Whatever ends a session (release, link lost, STOP, screen left) stops the axis through the runner; this class never
 * leaves a control marked as held after the session ended.
 */
class DeadManJogController(
    private val scope: CoroutineScope,
    poster: CommandPoster,
    /** One small request to the server; returns its round-trip time and throws when nobody answers. */
    private val pinger: suspend () -> Long,
    /** What the app knows of the robot, to decide whether the jog may start. */
    private val situation: (robotId: Int, switchedOn: Boolean, alreadyRunning: Boolean, linkBlocks: Boolean, serverSupports: Boolean) -> DeadManJog.Situation,
    private val say: (String?) -> Unit,
    private val blockText: (DeadManJog.Block) -> String,
    private val endText: (DeadManJog.EndReason, Boolean) -> String?,
    private val errorText: (Throwable) -> String,
    settings: DeadManJog.Settings = DeadManJog.Settings(),
    clock: () -> Long = { System.nanoTime() / 1_000_000L },
    val link: LinkHealth = LinkHealth(),
    private val probeIntervalMs: Long = RobotCommandContract.LINK_HEALTH_PROBE_INTERVAL_MS,
) {
    /** The continuous jog itself: off until the person turns it on. */
    val enabled = mutableStateOf(RobotCommandContract.DEAD_MAN_JOG_ENABLED_BY_DEFAULT)

    /** Whether a bad or lost link stops (and refuses) the continuous jog: on by default. */
    val blockOnBadLink = mutableStateOf(RobotCommandContract.LINK_HEALTH_BLOCK_JOG_ON_BAD_LINK_DEFAULT)

    /** Whether the link indicator is shown on the Control screen. */
    val indicator = mutableStateOf(RobotCommandContract.LINK_HEALTH_INDICATOR_ENABLED_BY_DEFAULT)

    /** Speed of the continuous jog, in the range of the contract. */
    val velocity = mutableStateOf(RobotCommandContract.DEAD_MAN_JOG_VELOCITY_DEFAULT.toFloat())

    /** False once the server answered that it does not know the continuous-jog commands. */
    val serverSupports = mutableStateOf(true)

    /** The robots whose control is held (the start may still be on the wire). */
    val held = mutableStateOf<Set<Int>>(emptySet())

    val linkLevel = mutableStateOf(LinkHealth.Level.UNKNOWN)
    val linkRttMs = mutableStateOf<Long?>(null)

    private val runner = DeadManJogRunner(
        scope = scope,
        poster = poster,
        settings = settings,
        clock = clock,
        linkAllows = { !linkBlocks() },
        onRoundTrip = { link.record(it); publish() },
        onEnded = { robotId, reason, stopConfirmed ->
            held.value = held.value - robotId
            endText(reason, stopConfirmed)?.let(say)
        },
    )

    private var probe: Job? = null

    private fun linkBlocks(): Boolean = blockOnBadLink.value && link.blocksJog()

    private fun publish() {
        linkLevel.value = link.level()
        linkRttMs.value = link.lastMs()
    }

    /** The first reason not to start for [robotId] now, [DeadManJog.Block.NONE] when it may. */
    fun blockOf(robotId: Int): DeadManJog.Block =
        DeadManJog.blockOf(situation(robotId, enabled.value, robotId in held.value, linkBlocks(), serverSupports.value))

    /** The control for [axis] of [target] was pressed; [direction] is +1 or -1. Nothing moves if a rule refuses. */
    fun press(robotId: Int, target: String, axis: String, direction: Int) {
        val block = blockOf(robotId)
        if (block != DeadManJog.Block.NONE) {
            say(blockText(block))
            return
        }
        held.value = held.value + robotId
        val signed = (if (direction < 0) -1 else 1) * velocity.value.toDouble()
        scope.launch {
            try {
                runner.start(robotId, target, axis, signed)
                // The finger left while the start was on the wire: stop at once instead of holding a jog nobody holds.
                if (robotId !in held.value) runner.release(robotId)
                say(null)
            } catch (e: CancellationException) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    held.value = held.value - robotId
                    say(errorText(e))
                } else {
                    held.value = held.value - robotId
                    throw e
                }
            } catch (e: Exception) {
                held.value = held.value - robotId
                if (e is HydraApiException && isUnknownCommand(e)) serverSupports.value = false
                say(errorText(e))
            }
        }
    }

    /** The control was released (or the finger slid off it). */
    fun release(robotId: Int) {
        held.value = held.value - robotId
        runner.release(robotId)
    }

    /** Stops every held jog now: a STOP is going out, the screen was left, the connection was lost. */
    fun interrupt() {
        if (held.value.isEmpty() && !runner.anyRunning()) return
        held.value = emptySet()
        runner.interrupt()
    }

    /**
     * Measures the link while [active] says so and while the indicator or the jog is switched on; ends by itself when either stops
     * being true, and [ensureProbe] starts it again. A probe that gets no answer counts as a failure.
     */
    fun ensureProbe(active: () -> Boolean) {
        if (probe?.isActive == true) return
        if (!active() || !(indicator.value || enabled.value)) return
        probe = scope.launch {
            while (active() && (indicator.value || enabled.value)) {
                try {
                    link.record(pinger())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    link.recordFailure()
                }
                publish()
                delay(probeIntervalMs)
            }
            if (!active()) {
                link.reset()
                publish()
            }
        }
    }

    fun stopProbe() {
        probe?.cancel()
        probe = null
        link.reset()
        publish()
    }
}
