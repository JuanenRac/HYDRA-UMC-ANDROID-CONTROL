package com.hydraumc.control.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.MainActivity
import com.hydraumc.control.util.GlobalStopRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GlobalStopTileServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theTileAsksTheRunningAppForTheGlobalStopLikeTheWidgetDoes() {
        val intent = GlobalStopTileService.stopIntent(context)
        assertEquals("ACTION_GLOBAL_ESTOP", intent.action)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(GlobalStopRequest.isOurs(context, intent))
    }

    @Test
    fun aStopAskedByAnotherAppIsNotTaken() {
        GlobalStopTileService.stopIntent(context)
        val bare = Intent(context, MainActivity::class.java).setAction(GlobalStopRequest.ACTION)
        assertFalse(GlobalStopRequest.isOurs(context, bare))
        assertFalse(GlobalStopRequest.isOurs(context, Intent(bare).putExtra("com.hydraumc.control.extra.STOP_PROOF", "0".repeat(64))))
        assertFalse(GlobalStopRequest.isOurs(context, GlobalStopTileService.stopIntent(context).setAction("android.intent.action.VIEW")))
        assertFalse(GlobalStopRequest.isOurs(context, null))
    }

    @Test
    fun theManifestLetsOnlyTheSystemBindToTheTile() {
        val info = context.packageManager.getServiceInfo(
            android.content.ComponentName(context, GlobalStopTileService::class.java),
            0,
        )
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", info.permission)
    }
}
