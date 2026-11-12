// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - network/ListenOnly.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject

/**
 * Listen only: the app shows the robots and their data but sends no robot command. The only ones that still go out are
 * STOP and the safe-mode disable, because refusing a stop would be the unsafe side. Off by default.
 */
object ListenOnly {
    private val ALWAYS_ALLOWED: Set<String> = setOf(RobotCommandContract.STOP, RobotCommandContract.DISABLE)

    /** Whether [command] may leave the app while Listen only is [on]. */
    fun allows(on: Boolean, command: String): Boolean = !on || command in ALWAYS_ALLOWED

    /** The switch as the network layer reads it; set by the view model, off until the person turns it on. */
    @Volatile
    var enabled: Boolean = false

    private val SESSION_NAMES: Set<String> = setOf("login", "refresh", "logout")

    /**
     * Whether a request may leave the app while Listen only is on: a read, STOP (the global one and a robot command
     * that is stop or disable) and the sign-in, the renewal and the end of the session. Every other write to the server
     * (settings, push and presence registration, files, users) is refused. The path is read by its segments.
     */
    fun allowsRequest(method: String, pathSegments: List<String>, bodyText: String?): Boolean {
        if (method.equals("GET", true) || method.equals("HEAD", true)) return true
        if (pathSegments.firstOrNull() != "api") return false
        val rest = pathSegments.drop(1)
        if (rest.size == 1 && (rest[0] in SESSION_NAMES || rest[0] == "stop-all")) return true
        if (rest.size == 2 && rest[0] == "device" && rest[1] == "login") return true
        if (rest.size == 3 && rest[0] == "robot" && rest[2] == "command" && bodyText != null) {
            return try {
                JSONObject(bodyText).optString("command") in ALWAYS_ALLOWED
            } catch (_: Exception) {
                false
            }
        }
        return false
    }
}

/** The door of the network for Listen only: a refused write is answered with a 423 here and never leaves the app. */
class ListenOnlyInterceptor(private val on: () -> Boolean = { ListenOnly.enabled }) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        if (!on()) return chain.proceed(request)
        val bodyText = request.body?.let { body -> Buffer().also { body.writeTo(it) }.readUtf8() }
        if (ListenOnly.allowsRequest(request.method, request.url.pathSegments.filter { it.isNotEmpty() }, bodyText)) return chain.proceed(request)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(423)
            .message("Locked")
            .body("{\"error\":\"Listen only is on: nothing is written from this app except STOP.\",\"code\":\"listen-only\"}".toResponseBody("application/json".toMediaType()))
            .build()
    }
}
