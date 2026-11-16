// =============================================================================
// HYDRA-UMC CONTROL - The stop state pushed by the server
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
//
// The `stop-state` message of the server (REMOTE_API.md section 3, sent to a connection that asked for remoteApiVersion=2): the
// one state of the physical emergency stop and the global stop. This reads the part the app shows: whether the server is stopped
// only because it restarted while stopped (`restoredAfterRestart`), which an administrator has to release.
// =============================================================================
package com.hydraumc.control.model

import org.json.JSONObject

object StopStateNotice {
    /**
     * True when [message] is a complete `stop-state` message that says the global stop is on and was restored after a restart;
     * false for a complete one that says anything else, and null for a message that is not a `stop-state` one (so the notice
     * stays as it was).
     */
    fun restoredAfterRestart(message: JSONObject): Boolean? {
        if (message.optString("type") != "stop-state") return null
        val state = message.optJSONObject("state") ?: return null
        if (state.optString("software") != "stopped" && state.optString("software") != "ready") return null
        return state.optString("software") == "stopped" && state.optBoolean("restoredAfterRestart", false)
    }
}
