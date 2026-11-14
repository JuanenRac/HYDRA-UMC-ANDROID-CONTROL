// =============================================================================
// HYDRA-UMC CONTROL - Live progress of a CAN flash cycle
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
//
// The `canota_progress` message of the server (REMOTE_API.md section 3): while a real flash cycle of a board runs, every connected
// administrator receives `{"type":"canota_progress","payload":{"phase","pages_sent","pages_total","percent","error"}}`. This is the
// reading of that message for the app, the same one the desktop and the web panels make. The app only shows it; it starts no flash.
// =============================================================================
package com.hydraumc.control.model

import com.hydraumc.common.optStr
import org.json.JSONObject

/** The phases of a flash cycle, named as the server writes them. */
enum class CanOtaPhase(val wire: String) {
    CONNECTING("connecting"),
    ENTERING_BOOTLOADER("entering_bootloader"),
    ERASING_FRAM("erasing_fram"),
    TRANSFERRING("transferring"),
    VERIFYING("verifying"),
    REBOOTING("rebooting"),
    DONE("done"),
    ERROR("error");

    companion object {
        /** The phase written [wire], or null for a name this app does not know. */
        fun fromWire(wire: String): CanOtaPhase? = entries.firstOrNull { it.wire == wire }
    }
}

/** One report of the progress of a flash cycle. */
data class CanOtaProgress(
    val phase: CanOtaPhase,
    /** 0 to 100, or null when the server sent no usable number. */
    val percent: Int?,
    val pagesSent: Int?,
    val pagesTotal: Int?,
    /** The reason the bootloader client reports, at most [MAX_REASON_CHARS] characters. */
    val error: String?,
) {
    /** True once the cycle ended, well or badly. */
    val finished: Boolean get() = phase == CanOtaPhase.DONE || phase == CanOtaPhase.ERROR

    companion object {
        const val MAX_REASON_CHARS = 300

        private fun count(value: Any?): Int? {
            val number = value as? Number ?: return null
            val whole = number.toDouble()
            return if (whole.isFinite() && whole >= 0 && whole == Math.floor(whole) && whole <= Int.MAX_VALUE) whole.toInt() else null
        }

        /** The progress in a `canota_progress` [message], or null when it carries no phase this app knows. Nothing is guessed: a missing number stays null. */
        fun fromMessage(message: JSONObject): CanOtaProgress? {
            val payload = message.optJSONObject("payload") ?: return null
            val phase = CanOtaPhase.fromWire(payload.optStr("phase")) ?: return null
            val percentRaw = payload.opt("percent") as? Number
            val percent = percentRaw?.toDouble()?.takeIf { it.isFinite() }?.let { Math.round(it).toInt().coerceIn(0, 100) }
            val reason = (payload.opt("error") as? String)?.takeIf { it.isNotBlank() }?.take(MAX_REASON_CHARS)
            return CanOtaProgress(phase, percent, count(payload.opt("pages_sent")), count(payload.opt("pages_total")), reason)
        }
    }
}
