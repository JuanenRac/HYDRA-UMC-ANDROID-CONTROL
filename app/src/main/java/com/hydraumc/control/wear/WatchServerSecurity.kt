// =============================================================================
// HYDRA-UMC-ANDROID-CONTROL - the security status of the server for the watch
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.wear

import com.hydraumc.control.network.ConnectionClient
import com.hydraumc.control.network.ConnectionHistory
import com.hydraumc.control.network.ServerOption
import com.hydraumc.control.network.ServerSecurity
import com.hydraumc.control.network.ServerSecurityOptions

/**
 * Builds the read-only security status that the watch shows from the two answers an administrator can read: the access options of the
 * server (GET /api/admin/server-config) and its history of clients (GET /api/connections/clients). Pure, so a plain JVM checks it. It
 * never writes anything and reports nothing it was not told: an option the server does not report is not counted as lowered.
 */
object WatchServerSecurity {
    /** The ids (section.key, as in the answer of the server) of the options whose value lowers the protection when it is this one. */
    private val LOWERED_WHEN: List<Triple<String, (ServerSecurityOptions) -> ServerOption?, Boolean>> = listOf(
        Triple("health.open", { o: ServerSecurityOptions -> o.healthOpen }, true),
        Triple("connections.recordAddress", { o: ServerSecurityOptions -> o.recordAddress }, true),
        Triple("camera.open", { o: ServerSecurityOptions -> o.cameraOpen }, true),
        Triple("websocket.allowQueryToken", { o: ServerSecurityOptions -> o.wsQueryToken }, true),
        Triple("logging.redactSecrets", { o: ServerSecurityOptions -> o.logRedaction }, false),
        Triple("camera.credentialsViaEnv", { o: ServerSecurityOptions -> o.cameraCredentialsViaEnv }, false),
        Triple("install.newInstallTls", { o: ServerSecurityOptions -> o.newInstallTls }, false),
    )

    fun build(options: ServerSecurityOptions, clients: List<ConnectionClient>): WatchServerSecurityStatus {
        val lowered = LOWERED_WHEN.filter { (_, read, risky) -> read(options)?.value == risky }.map { it.first }
        val all = listOf(
            options.healthOpen, options.publishGlobalStop, options.recordAddress, options.cameraOpen, options.wsQueryToken,
            options.logRedaction, options.voiceSignature, options.cameraCredentialsViaEnv, options.newInstallTls,
        )
        return WatchServerSecurityStatus(
            loweredSwitches = lowered,
            lockedSwitches = all.count { it?.locked == true },
            activeClients = clients.count { it.activeSessions > 0 }.coerceAtMost(WatchServerSecurityStatus.MAX_COUNT),
            failedLogins = clients.sumOf { it.loginsFailed.toLong().coerceAtLeast(0L) }.coerceAtMost(WatchServerSecurityStatus.MAX_COUNT.toLong()).toInt(),
        )
    }

    /** Reads the two answers; null when the options cannot be read (the caller then tells the watch the server is not available). */
    fun fromAnswers(serverConfigBody: String, clientsBody: String): WatchServerSecurityStatus? {
        val options = ServerSecurity.parse(serverConfigBody)?.takeUnless { it.isEmpty } ?: return null
        return build(options, ConnectionHistory.parseClients(clientsBody))
    }
}
