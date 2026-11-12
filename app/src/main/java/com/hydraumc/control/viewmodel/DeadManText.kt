// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/DeadManText.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import android.content.Context
import com.hydraumc.control.R
import com.hydraumc.control.util.DeadManJog

/** The sentences of the continuous jog (why it does not start, why it ended), in the language of the phone. */
object DeadManText {
    /** The phrase for a reason the continuous jog does not start. */
    fun block(context: Context, block: DeadManJog.Block): String = context.getString(
        when (block) {
            DeadManJog.Block.NONE -> R.string.home_reset_blocked_none
            DeadManJog.Block.OFF -> R.string.dead_man_blocked_off
            DeadManJog.Block.NOT_CONNECTED -> R.string.error_not_connected
            DeadManJog.Block.OFFLINE -> R.string.dead_man_blocked_offline
            DeadManJog.Block.SAFE_MODE -> R.string.dead_man_blocked_safe_mode
            DeadManJog.Block.MOVING -> R.string.dead_man_blocked_moving
            DeadManJog.Block.STOP_PENDING -> R.string.dead_man_blocked_stop
            DeadManJog.Block.NOT_ARMED -> R.string.error_jog_not_armed
            DeadManJog.Block.ROLE -> R.string.dead_man_blocked_role
            DeadManJog.Block.BAD_LINK -> R.string.dead_man_blocked_bad_link
            DeadManJog.Block.ALREADY_RUNNING -> R.string.dead_man_blocked_already
            DeadManJog.Block.NOT_SUPPORTED -> R.string.dead_man_blocked_not_supported
        },
    )

    /** What to tell the person when a continuous jog ended on its own; null when nothing needs saying. */
    fun end(context: Context, reason: DeadManJog.EndReason, stopConfirmed: Boolean): String? {
        if (!stopConfirmed) return context.getString(R.string.dead_man_stop_not_confirmed)
        return when (reason) {
            DeadManJog.EndReason.RELEASED, DeadManJog.EndReason.INTERRUPTED -> null
            DeadManJog.EndReason.LINK_LOST -> context.getString(R.string.dead_man_ended_link_lost)
            DeadManJog.EndReason.TOO_LONG -> context.getString(R.string.dead_man_ended_too_long)
            DeadManJog.EndReason.BAD_LINK -> context.getString(R.string.dead_man_ended_bad_link)
            DeadManJog.EndReason.SERVER_REFUSED -> context.getString(R.string.dead_man_ended_refused)
        }
    }
}
