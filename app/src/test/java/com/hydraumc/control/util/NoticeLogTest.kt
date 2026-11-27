// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/util/NoticeLogTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoticeLogTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theNewestComesFirstAndSurvivesAnotherReader() {
        NoticeLog(app).add(NoticeKind.ROBOT_OFFLINE, "A3", atMs = 1)
        NoticeLog(app).add(NoticeKind.JOB_DONE, "A1", atMs = 2)
        assertEquals(listOf("A1", "A3"), NoticeLog(app).all().map { it.detail })
        assertEquals(1, Notices.unseen(NoticeLog(app).all()))
        NoticeLog(app).markAllSeen()
        assertEquals(0, Notices.unseen(NoticeLog(app).all()))
    }

    @Test
    fun aVersionIsAnnouncedOnce() {
        val log = NoticeLog(app)
        log.addOnce(NoticeKind.UPDATE, "9.9.9")
        log.addOnce(NoticeKind.UPDATE, "9.9.9")
        assertEquals(1, log.all().count { it.kind == NoticeKind.UPDATE && it.detail == "9.9.9" })
    }

    @Test
    fun theListIsBoundedAndADamagedStoreIsEmpty() {
        var list = emptyList<Notice>()
        repeat(Notices.MAX + 5) { list = Notices.add(list, Notice(NoticeKind.JOB_DONE, "$it", it.toLong())) }
        assertEquals(Notices.MAX, list.size)
        assertEquals(list, Notices.decode(Notices.encode(list)))
        assertEquals(emptyList<Notice>(), Notices.decode("not json"))
        assertEquals(emptyList<Notice>(), Notices.decode("""[{"kind":"NOTHING"}]"""))
    }
}
