// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/BlockTextTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.util.HomeReset
import com.hydraumc.control.util.WorkFiles
import com.hydraumc.control.wear.WatchAlertEvent
import com.hydraumc.control.wear.WatchAlertKind
import com.hydraumc.control.wear.WatchAlertSeverity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every reason the work files or HOME/RESET are blocked and every watch alert has a sentence, and the sentences of the alerts carry the robot name. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockTextTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun everyWorkBlockHasAPhraseAndTheReasonsDiffer() {
        val texts = WorkFiles.Block.values().map { BlockText.work(context, it) }
        for (text in texts) assertTrue(text.isNotBlank())
        assertTrue("the reasons are told apart", texts.toSet().size >= WorkFiles.Block.values().size - 1)
    }

    @Test
    fun everyHomeResetBlockHasAPhrase() {
        val texts = HomeReset.Block.values().map { BlockText.homeReset(context, it) }
        for (text in texts) assertTrue(text.isNotBlank())
        assertTrue("the reasons are told apart", texts.toSet().size >= HomeReset.Block.values().size - 1)
    }

    @Test
    fun theAlertsOfARobotNameTheRobotAndTheOthersDoNot() {
        fun event(kind: WatchAlertKind) = WatchAlertEvent(kind, WatchAlertSeverity.WARNING, "ARM-7", "key")
        assertTrue(BlockText.watchAlert(context, event(WatchAlertKind.ROBOT_OFFLINE)).contains("ARM-7"))
        assertTrue(BlockText.watchAlert(context, event(WatchAlertKind.ROBOT_SAFE_MODE)).contains("ARM-7"))
        for (kind in listOf(WatchAlertKind.CONNECTION_LOST, WatchAlertKind.CONNECTION_RESTORED, WatchAlertKind.STOP_NOT_CONFIRMED, WatchAlertKind.STOP_CONFIRMED)) {
            val text = BlockText.watchAlert(context, event(kind))
            assertTrue("$kind", text.isNotBlank())
            assertTrue("$kind names no robot", !text.contains("ARM-7"))
        }
    }

    @Test
    fun aRobotWithoutANameStillGetsASentence() {
        val text = BlockText.watchAlert(context, WatchAlertEvent(WatchAlertKind.ROBOT_OFFLINE, WatchAlertSeverity.WARNING, null, "key"))
        assertTrue(text.isNotBlank())
    }
}
