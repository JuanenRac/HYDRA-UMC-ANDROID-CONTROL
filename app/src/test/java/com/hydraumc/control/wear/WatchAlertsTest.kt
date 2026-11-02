// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/wear/WatchAlertsTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Which changes of the plant earn an alert on the wrist, and how a burst is rationed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchAlertsTest {
    private var now = 100_000L
    private val rules = WatchAlertRules(clock = { now })

    private fun robot(id: Int = 1, online: Boolean = true, enabled: Boolean = true, playing: Boolean = false) =
        WatchAlertRobot(id, "Arm $id", online, enabled, playing)

    private fun input(vararg robots: WatchAlertRobot, connected: Boolean = true, stop: WatchAlertStop = WatchAlertStop.NONE) =
        WatchAlertInput(connected, if (robots.isEmpty()) listOf(robot()) else robots.toList(), stop)

    private fun kinds(events: List<WatchAlertEvent>) = events.map { it.kind }

    @Test
    fun theFirstObservationOnlyLearnsTheStartingPoint() {
        assertTrue(rules.observe(input(robot(online = false))).isEmpty())
        assertTrue(rules.observe(input(robot(online = false))).isEmpty())
    }

    @Test
    fun aConnectionThatNeverExistedIsNotALostConnection() {
        rules.observe(input(connected = false))
        now += 60_000
        assertTrue(rules.observe(input(connected = false)).isEmpty())
    }

    @Test
    fun aLostConnectionIsAnnouncedOnceAfterTheWaitAndAgainWhenItComesBack() {
        rules.observe(input())
        now += 1_000
        assertTrue(rules.observe(input(connected = false)).isEmpty())
        now += 9_000
        assertTrue("not yet 10 s", rules.observe(input(connected = false)).isEmpty())
        now += 2_000
        val lost = rules.observe(input(connected = false)).single()
        assertEquals(WatchAlertKind.CONNECTION_LOST, lost.kind)
        assertEquals(WatchAlertSeverity.WARNING, lost.severity)
        now += 5_000
        assertTrue("announced only once", rules.observe(input(connected = false)).isEmpty())
        now += 5_000
        assertEquals(listOf(WatchAlertKind.CONNECTION_RESTORED), kinds(rules.observe(input())))
    }

    @Test
    fun aConnectionLostWhileARobotWasRunningIsCritical() {
        rules.observe(input(robot(playing = true)))
        now += 1_000
        rules.observe(WatchAlertInput(false, emptyList(), WatchAlertStop.NONE))
        now += 11_000
        val lost = rules.observe(WatchAlertInput(false, emptyList(), WatchAlertStop.NONE)).single()
        assertEquals(WatchAlertSeverity.CRITICAL, lost.severity)
    }

    @Test
    fun aRobotThatGoesOfflineWhileRunningIsCriticalOtherwiseAWarning() {
        rules.observe(WatchAlertInput(true, listOf(robot(1, playing = true), robot(2)), WatchAlertStop.NONE))
        now += 1_000
        val events = rules.observe(WatchAlertInput(true, listOf(robot(1, online = false), robot(2, online = false)), WatchAlertStop.NONE))
        assertEquals(WatchAlertSeverity.CRITICAL, events.first { it.robotName == "Arm 1" }.severity)
        assertEquals(WatchAlertSeverity.WARNING, events.first { it.robotName == "Arm 2" }.severity)
    }

    @Test
    fun aRobotPutInSafeModeIsAWarning() {
        rules.observe(input())
        now += 1_000
        val event = rules.observe(input(robot(enabled = false))).single()
        assertEquals(WatchAlertKind.ROBOT_SAFE_MODE, event.kind)
        assertEquals("Arm 1", event.robotName)
    }

    @Test
    fun aStopTheServerDidNotConfirmIsCriticalAndTheConfirmationIsInformation() {
        rules.observe(input())
        now += 1_000
        rules.observe(input(stop = WatchAlertStop.SENDING))
        now += 1_000
        val bad = rules.observe(input(stop = WatchAlertStop.NOT_CONFIRMED)).single()
        assertEquals(WatchAlertKind.STOP_NOT_CONFIRMED, bad.kind)
        assertEquals(WatchAlertSeverity.CRITICAL, bad.severity)
        now += 5_000
        val done = rules.observe(input(stop = WatchAlertStop.DONE)).single()
        assertEquals(WatchAlertKind.STOP_CONFIRMED, done.kind)
        assertEquals(WatchAlertSeverity.INFO, done.severity)
    }

    @Test
    fun aStopThatIsConfirmedAtOnceIsOnlyInformationWhenItWasSent() {
        rules.observe(input())
        now += 1_000
        assertTrue(rules.observe(input(stop = WatchAlertStop.DONE)).isEmpty())
    }

    @Test
    fun theSameEventIsNotRepeatedWithinTheDedupeWindow() {
        rules.observe(input())
        now += 1_000
        assertEquals(1, rules.observe(input(robot(enabled = false))).size)
        now += 1_000
        rules.observe(input(robot(enabled = true)))
        now += 1_000
        assertTrue("same robot, same event, 3 s later", rules.observe(input(robot(enabled = false))).isEmpty())
        now += 40_000
        rules.observe(input(robot(enabled = true)))
        now += 1_000
        assertEquals(1, rules.observe(input(robot(enabled = false))).size)
    }

    @Test
    fun alertsThatAreNotCriticalAreSpacedAndCapped() {
        rules.observe(WatchAlertInput(true, (1..10).map { robot(it) }, WatchAlertStop.NONE))
        now += 1_000
        // ten robots enter safe mode together: what is found together goes out together, up to the cap of six a minute
        val first = rules.observe(WatchAlertInput(true, (1..10).map { robot(it, enabled = false) }, WatchAlertStop.NONE))
        assertEquals(6, first.size)
        now += 1_000
        rules.observe(WatchAlertInput(true, (1..10).map { robot(it, enabled = true) }, WatchAlertStop.NONE))
        now += 1_000
        // robot 7 is a new event (the cap dropped it before), but inside the same minute the cap still holds
        now += 3_000
        assertTrue(rules.observe(WatchAlertInput(true, (1..10).map { robot(it, enabled = it != 7) }, WatchAlertStop.NONE)).isEmpty())
        now += 61_000
        rules.observe(WatchAlertInput(true, (1..10).map { robot(it) }, WatchAlertStop.NONE))
        now += 1_000
        assertEquals(1, rules.observe(WatchAlertInput(true, (1..10).map { robot(it, enabled = it != 7) }, WatchAlertStop.NONE)).size)
    }

    @Test
    fun aCriticalAlertIsNeverHeldBackByTheCap() {
        rules.observe(WatchAlertInput(true, (1..8).map { robot(it, playing = it == 8) }, WatchAlertStop.NONE))
        now += 1_000
        val events = rules.observe(WatchAlertInput(true, (1..8).map { robot(it, enabled = false, online = it != 8) }, WatchAlertStop.NONE))
        // robot 8 went offline while running (critical); the others went into safe mode (warnings, capped at six in all)
        assertTrue(events.any { it.severity == WatchAlertSeverity.CRITICAL && it.robotName == "Arm 8" })
        assertEquals(6, events.size)
    }

    @Test
    fun theAlertIsTheMessageTheWatchAcceptsAndLongTextIsCut() {
        val json = WatchAlert.of(WatchAlertSeverity.CRITICAL, "Robot stopped").toJson()
        assertEquals("alert", json.getString("type"))
        assertEquals("CRITICAL", json.getString("severity"))
        assertEquals("Robot stopped", json.getString("message"))
        val long = WatchAlert.of(WatchAlertSeverity.INFO, "x".repeat(900))
        assertEquals(280, long.message.length)
        assertTrue(long.message.endsWith("…"))
        assertEquals("!", WatchAlert.of(WatchAlertSeverity.INFO, "   ").message)
        assertTrue(runCatching { WatchAlert(WatchAlertSeverity.INFO, " ") }.isFailure)
    }
}
