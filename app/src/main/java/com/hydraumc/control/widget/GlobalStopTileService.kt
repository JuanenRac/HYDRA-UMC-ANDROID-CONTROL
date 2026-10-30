// =============================================================================
// HYDRA-UMC CONTROL - Quick Settings tile for the global stop
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.hydraumc.control.MainActivity
import com.hydraumc.control.R

/**
 * A tile in the quick settings panel that asks the app for the global stop,
 * the same request the home screen widget and the notification make. The app
 * opens and sends the stop through its own session; the tile holds no
 * credentials and talks to no server itself.
 */
class GlobalStopTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = getString(R.string.stop_all_action)
            updateTile()
        }
    }

    override fun onClick() {
        val intent = stopIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** The request MainActivity answers with the global stop (see its onNewIntent). */
        fun stopIntent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
            action = "ACTION_GLOBAL_ESTOP"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }
}
