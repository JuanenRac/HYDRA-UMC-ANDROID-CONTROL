// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/wear/WatchReleaseNoticePushTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import com.hydraumc.common.AvailableUpdate
import com.hydraumc.common.SemanticVersion
import com.hydraumc.common.UpdateCheckResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the phone tells the watch about a newer release: the message, and when. */
@RunWith(RobolectricTestRunner::class)
class WatchReleaseNoticePushTest {
    private fun available(version: SemanticVersion) =
        UpdateCheckResult.Available(AvailableUpdate(version, "HYDRA-UMC CONTROL $version", "", "https://example.invalid/app.apk"))

    private class Recorder(private val takes: Boolean = true) : WatchReleaseNoticeSink {
        val sent = mutableListOf<WatchReleaseNotice>()
        override suspend fun send(notice: WatchReleaseNotice): Boolean {
            sent += notice
            return takes
        }
    }

    @Test
    fun `the message carries the version in the shape the watch accepts`() {
        val json = WatchReleaseNotice("0.8.0.1").toJson()
        assertEquals("release_notice", json.getString("type"))
        assertEquals(1, json.getInt("protocolVersion"))
        assertEquals("0.8.0.1", json.getString("version"))
        assertEquals(WatchReleaseNotice("0.5.2"), WatchReleaseNotice.fromJson(WatchReleaseNotice("0.5.2").toJson()))
    }

    @Test
    fun `a version the watch would refuse is not built`() {
        for (build in listOf<() -> Any>(
            { WatchReleaseNotice("latest") },
            { WatchReleaseNotice("5") },
            { WatchReleaseNotice("1.2.3.4.5") },
            { WatchReleaseNotice("0.5.2 http://x") },
            { WatchReleaseNotice("12345.1") },
            { WatchReleaseNotice("0.5.2", protocolVersion = 2) },
        )) {
            try {
                build()
                fail("accepted an invalid notice")
            } catch (_: IllegalArgumentException) {
                // refused
            }
        }
    }

    @Test
    fun `only a release the gate accepted is told, and a failed or up to date check sends nothing`() = runBlocking {
        val sink = Recorder()
        val policy = ReleaseNoticeWatchPolicy()
        assertFalse(announceReleaseToWatch(UpdateCheckResult.UpToDate, policy, sink))
        assertFalse(announceReleaseToWatch(UpdateCheckResult.Failed("no network"), policy, sink))
        assertTrue(sink.sent.isEmpty())
        assertTrue(announceReleaseToWatch(available(SemanticVersion(0, 5, 2)), policy, sink))
        assertEquals(listOf(WatchReleaseNotice("0.5.2")), sink.sent)
    }

    @Test
    fun `a version is told once, a lower one never and a higher one again`() = runBlocking {
        val sink = Recorder()
        val policy = ReleaseNoticeWatchPolicy()
        announceReleaseToWatch(available(SemanticVersion(0, 5, 2)), policy, sink)
        announceReleaseToWatch(available(SemanticVersion(0, 5, 2)), policy, sink)
        announceReleaseToWatch(available(SemanticVersion(0, 5, 1)), policy, sink)
        announceReleaseToWatch(available(SemanticVersion(0, 8, 0, 1)), policy, sink)
        assertEquals(listOf("0.5.2", "0.8.0.1"), sink.sent.map { it.version })
        policy.reset()
        announceReleaseToWatch(available(SemanticVersion(0, 5, 2)), policy, sink)
        assertEquals(3, sink.sent.size)
    }

    @Test
    fun `no watch means false, the version is not used up, and a fourth component compares as zero`() = runBlocking {
        val policy = ReleaseNoticeWatchPolicy()
        assertFalse(announceReleaseToWatch(available(SemanticVersion(0, 5, 2)), policy, Recorder(takes = false)))
        val sink = Recorder()
        assertTrue("the next check tells it again", announceReleaseToWatch(available(SemanticVersion(0, 5, 2)), policy, sink))
        assertFalse("0.5.2.0 is not newer than 0.5.2", policy.accept(SemanticVersion(0, 5, 2, 0)))
        assertTrue(policy.accept(SemanticVersion(0, 5, 2, 1)))
    }

    @Test
    fun `with the switch off nothing is sent and the version is not remembered`() = runBlocking {
        val sink = Recorder()
        val policy = ReleaseNoticeWatchPolicy()
        assertFalse(announceReleaseToWatch(available(SemanticVersion(0, 5, 2)), policy, sink, enabled = false))
        assertFalse(announceReleaseToWatch(UpToDateResult, policy, sink, enabled = false))
        assertTrue(sink.sent.isEmpty())
        // the person turns it on again: the same version is told by the next check, because the off state did not use it up
        assertTrue(announceReleaseToWatch(available(SemanticVersion(0, 5, 2)), policy, sink, enabled = true))
        assertEquals(listOf("0.5.2"), sink.sent.map { it.version })
    }

    @Test
    fun `the switch is on when the caller says nothing, as it ships`() = runBlocking {
        val sink = Recorder()
        assertTrue(announceReleaseToWatch(available(SemanticVersion(0, 6, 0)), ReleaseNoticeWatchPolicy(), sink))
        assertEquals(1, sink.sent.size)
    }

    private val UpToDateResult: UpdateCheckResult = UpdateCheckResult.UpToDate
}
