// =============================================================================
// HYDRA-UMC CONTROL - One stored session for each server
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// The keys the app has always read hold the session of the server in use. The sessions of the other servers are parked in
// the same encrypted store under their own prefix, and a switch moves both in one write: at no moment does the active
// entry mix the password of one server with the token or the address of another. The app only reads the active entry, and
// still checks which server it belongs to before it sends anything (SessionBinding).
package com.hydraumc.control.network

object ServerSessions {
    /** Which server the active entry belongs to (the key AuthPrefs has always used). */
    const val SERVER = "session_server"

    val TEXT_KEYS = listOf(SessionKeys.USERNAME, "password", "email", "sealed_password", SessionKeys.TOKEN, SessionKeys.REFRESH_TOKEN)
    val FLAG_KEYS = listOf("remember_me", "is_logged_in", "biometric_enabled", "biometric_strong")

    private fun parked(id: String, key: String) = "parked|$id|$key"
    private fun marker(id: String) = "parked|$id|"

    /**
     * Makes [id] the server of the active entry: what was active is parked under its own server, and what [id] had parked
     * (nothing, for a new server) becomes active. An active entry that no server claims yet is not touched by anybody
     * else's switch: the caller binds it first.
     */
    fun switchTo(kv: SecretKv, id: String) {
        val current = kv.getString(SERVER).orEmpty()
        if (current == id) return
        val wasParked = kv.getString(marker(id)) != null
        val activeText = TEXT_KEYS.associateWith { kv.getString(it) }
        val activeFlags = FLAG_KEYS.associateWith { kv.getBoolean(it, false) }
        val nextText = TEXT_KEYS.associateWith { kv.getString(parked(id, it)) }
        val nextFlags = FLAG_KEYS.associateWith { kv.getBoolean(parked(id, it), false) }
        kv.write {
            if (current.isNotEmpty()) {
                putString(marker(current), "1")
                activeText.forEach { (key, value) -> if (value == null) remove(parked(current, key)) else putString(parked(current, key), value) }
                activeFlags.forEach { (key, value) -> putBoolean(parked(current, key), value) }
            }
            nextText.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) }
            nextFlags.forEach { (key, value) -> if (wasParked) putBoolean(key, value) else remove(key) }
            TEXT_KEYS.forEach { remove(parked(id, it)) }
            FLAG_KEYS.forEach { remove(parked(id, it)) }
            remove(marker(id))
            putString(SERVER, id)
        }
    }

    /** Drops the parked session of [id]. The active one is left to the sign-out. */
    fun forget(kv: SecretKv, id: String) {
        kv.write {
            TEXT_KEYS.forEach { remove(parked(id, it)) }
            FLAG_KEYS.forEach { remove(parked(id, it)) }
            remove(marker(id))
        }
    }

    /** Whether the parked session of one of [ids] holds a password sealed by the Keystore key, which then must stay. */
    fun anySealed(kv: SecretKv, ids: List<String>): Boolean = ids.any { !kv.getString(parked(it, "sealed_password")).isNullOrEmpty() }
}
