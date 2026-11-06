// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - viewmodel/LiveSocket.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import com.hydraumc.control.network.HydraWebSocket
import com.hydraumc.control.network.WsStatus
import org.json.JSONObject

/** The part of a socket the owner needs: open it and close it (the real one in the app, a fake in the tests). */
interface SocketHandle {
    fun connect()
    fun disconnect()
}

/** Where to open the socket and with which token (null when the session has none). */
data class SocketTarget(val host: String, val port: Int, val useTls: Boolean, val token: String?)

/** What the socket reports. Every call has an empty default. */
interface SocketListener {
    /** Every status change, also while a server switch is under way. */
    fun onStatus(status: WsStatus) {}

    /** The socket closed while no server switch is under way (while one is, the close belongs to the old server). */
    fun onClosedIdle() {}

    /** The whole settings tree (the first message and every full write). */
    fun onSettings(payload: JSONObject) {}

    /** One targeted change of one robot. */
    fun onDelta(message: JSONObject) {}

    /** The list of reservations of the robots. */
    fun onClaims(message: JSONObject) {}

    /** The server closed the socket because the token is expired or invalid; not called while a server switch is under way. */
    fun onUnauthorized() {}

    /** A message for the person; not called while a server switch is under way. */
    fun onError(message: String) {}
}

/** The callbacks the factory hands to the socket it builds. */
class SocketCallbacks(
    val onStatus: (WsStatus) -> Unit,
    val onSettings: (JSONObject) -> Unit,
    val onDelta: (JSONObject) -> Unit,
    val onClaims: (JSONObject) -> Unit,
    val onUnauthorized: () -> Unit,
    val onError: (String) -> Unit,
)

/**
 * The live socket of the connection, away from the view model: it owns the one open socket (opening a new one closes the
 * previous one first, so two quick connects never leave an orphan that keeps reconnecting on its own) and keeps the rule
 * that a close, an error or an expired-token signal that arrives while the app switches server is ignored: it belongs to the
 * server being left, not to the one being opened.
 */
class LiveSocket(
    private val isSwitching: () -> Boolean,
    private val create: (SocketTarget, SocketCallbacks) -> SocketHandle = { target, callbacks -> realSocket(target, callbacks) },
) {
    private var current: SocketHandle? = null

    /** True while a socket is held (it may be closed already by the other side and reconnecting). */
    val isOpen: Boolean get() = current != null

    /** Opens the socket against [target]; the previous one is closed first. */
    fun open(target: SocketTarget, listener: SocketListener) {
        current?.disconnect()
        val handle = create(
            target,
            SocketCallbacks(
                onStatus = { status ->
                    listener.onStatus(status)
                    if (status == WsStatus.DISCONNECTED && !isSwitching()) listener.onClosedIdle()
                },
                onSettings = { listener.onSettings(it) },
                onDelta = { listener.onDelta(it) },
                onClaims = { listener.onClaims(it) },
                onUnauthorized = { if (!isSwitching()) listener.onUnauthorized() },
                onError = { message -> if (!isSwitching()) listener.onError(message) },
            ),
        )
        current = handle
        handle.connect()
    }

    /** Closes the socket and forgets it. Safe to call when there is none. */
    fun close() {
        current?.disconnect()
        current = null
    }

    companion object {
        /** The factory of the app: a [HydraWebSocket] over OkHttp. */
        fun realSocket(target: SocketTarget, callbacks: SocketCallbacks): SocketHandle {
            val socket = HydraWebSocket(
                host = target.host,
                port = target.port,
                useTls = target.useTls,
                token = target.token,
                onStatus = callbacks.onStatus,
                onSettings = callbacks.onSettings,
                onDelta = callbacks.onDelta,
                onUnauthorized = callbacks.onUnauthorized,
                onClaims = callbacks.onClaims,
                onError = callbacks.onError,
            )
            return object : SocketHandle {
                override fun connect() = socket.connect()
                override fun disconnect() = socket.disconnect()
            }
        }
    }
}
