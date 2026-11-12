// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/BlockText.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.content.Context
import com.hydraumc.control.R
import com.hydraumc.control.util.HomeReset
import com.hydraumc.control.util.WorkFiles
import com.hydraumc.control.wear.WatchAlertEvent
import com.hydraumc.control.wear.WatchAlertKind

/** The sentences for why the work files, HOME/RESET or the watch alerts say what they say, in the language of the phone. */
object BlockText {
    /** The phrase for a reason the work files cannot be used. */
    fun work(context: Context, block: WorkFiles.Block): String = context.getString(
        when (block) {
            WorkFiles.Block.NONE -> R.string.work_blocked_none
            WorkFiles.Block.NOT_CONNECTED -> R.string.error_not_connected
            WorkFiles.Block.OFFLINE -> R.string.work_blocked_offline
            WorkFiles.Block.SAFE_MODE -> R.string.work_blocked_safe_mode
            WorkFiles.Block.PLAYING -> R.string.work_blocked_playing
            WorkFiles.Block.STOP_PENDING -> R.string.work_blocked_stop
            WorkFiles.Block.ROLE -> R.string.work_blocked_role
            WorkFiles.Block.NO_TABLE -> R.string.work_blocked_no_table
            WorkFiles.Block.NOT_SUPPORTED -> R.string.work_not_supported
        },
    )

    /** The phrase for a reason HOME/RESET is not offered. */
    fun homeReset(context: Context, block: HomeReset.Block): String = context.getString(
        when (block) {
            HomeReset.Block.NONE -> R.string.home_reset_blocked_none
            HomeReset.Block.OFF -> R.string.home_reset_blocked_off
            HomeReset.Block.NOT_CONNECTED -> R.string.error_not_connected
            HomeReset.Block.OFFLINE -> R.string.home_reset_blocked_offline
            HomeReset.Block.SAFE_MODE -> R.string.home_reset_blocked_safe_mode
            HomeReset.Block.MOVING -> R.string.home_reset_blocked_moving
            HomeReset.Block.JOG_BUSY -> R.string.home_reset_blocked_jog
            HomeReset.Block.STOP_PENDING -> R.string.home_reset_blocked_stop
            HomeReset.Block.NOT_ARMED -> R.string.error_jog_not_armed
            HomeReset.Block.ROLE -> R.string.home_reset_blocked_role
            HomeReset.Block.NO_POSE -> R.string.home_reset_blocked_no_pose
            HomeReset.Block.NOT_SUPPORTED -> R.string.home_reset_not_supported
        },
    )

    /** The sentence of an alert. */
    fun watchAlert(context: Context, event: WatchAlertEvent): String {
        val name = event.robotName ?: ""
        return when (event.kind) {
            WatchAlertKind.CONNECTION_LOST -> context.getString(R.string.watch_alert_connection_lost)
            WatchAlertKind.CONNECTION_RESTORED -> context.getString(R.string.watch_alert_connection_restored)
            WatchAlertKind.ROBOT_OFFLINE -> context.getString(R.string.watch_alert_robot_offline, name)
            WatchAlertKind.ROBOT_SAFE_MODE -> context.getString(R.string.watch_alert_robot_safe_mode, name)
            WatchAlertKind.STOP_NOT_CONFIRMED -> context.getString(R.string.watch_alert_stop_not_confirmed)
            WatchAlertKind.STOP_CONFIRMED -> context.getString(R.string.watch_alert_stop_confirmed)
        }
    }
}
