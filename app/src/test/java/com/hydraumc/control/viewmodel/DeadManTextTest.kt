// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - app/src/test/java/com/hydraumc/control/viewmodel/DeadManTextTest.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.util.DeadManJog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The continuous-jog sentences: each block has its own phrase, a release says nothing, an unconfirmed stop always speaks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeadManTextTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun everyBlockHasANonEmptyPhrase() {
        for (block in DeadManJog.Block.values()) assertTrue("$block", DeadManText.block(context, block).isNotBlank())
    }

    @Test
    fun aReleaseOrAnInterruptionSaysNothingWhenTheStopWasConfirmed() {
        assertNull(DeadManText.end(context, DeadManJog.EndReason.RELEASED, true))
        assertNull(DeadManText.end(context, DeadManJog.EndReason.INTERRUPTED, true))
    }

    @Test
    fun anUnconfirmedStopAlwaysSpeaksWhateverTheReason() {
        val texts = DeadManJog.EndReason.values().map { DeadManText.end(context, it, false) }
        for (text in texts) assertNotNull(text)
        assertEquals(1, texts.toSet().size)
    }

    @Test
    fun everyOtherReasonHasItsOwnPhrase() {
        val reasons = listOf(DeadManJog.EndReason.LINK_LOST, DeadManJog.EndReason.TOO_LONG, DeadManJog.EndReason.BAD_LINK, DeadManJog.EndReason.SERVER_REFUSED)
        val texts = reasons.map { DeadManText.end(context, it, true) }
        for (text in texts) assertTrue(!text.isNullOrBlank())
        assertEquals(reasons.size, texts.toSet().size)
    }
}
