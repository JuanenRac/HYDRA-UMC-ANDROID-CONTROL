package com.hydraumc.control.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.hydraumc.control.MainActivity
import org.junit.Assert.assertEquals
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
