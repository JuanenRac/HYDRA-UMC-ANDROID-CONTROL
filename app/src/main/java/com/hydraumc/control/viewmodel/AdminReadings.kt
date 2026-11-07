// =============================================================================
// HYDRA-UMC CONTROL - viewmodel/AdminReadings.kt
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.viewmodel

import androidx.compose.runtime.mutableStateOf
import com.hydraumc.control.network.AuditRead
import com.hydraumc.control.network.AuditRecord
import com.hydraumc.control.network.ChainCheck
import com.hydraumc.control.network.ConnectionClient
import com.hydraumc.control.network.ConnectionEvent
import com.hydraumc.control.network.ConnectionHistory
import com.hydraumc.control.network.ConnectionsFailure
import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.ServerOptionsFailure
import com.hydraumc.control.network.ServerSecurity
import com.hydraumc.control.network.ServerSecurityOptions
import com.hydraumc.control.network.ServerSecurityValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How many lines of the history of sign-ins the screen asks for. */
private const val CONNECTION_EVENTS_SHOWN = 30

/** Why the audit record could not be read, for the sentence the screen shows. */
enum class AuditFailure { FORBIDDEN, UNAVAILABLE, SESSION, NETWORK, OTHER }

/**
 * The three readings of the administration screens: the audit record (administrators), who connects to the server (every account)
 * and the access options of the server (administrators). Each leaves what it read, a busy flag that always goes down and, when it
 * could not read, the reason; none raises an error on the screen. A rejected session asks [onSessionProblem] to renew it or sign out.
 * It was part of the view model; the view model keeps the same states and functions and hands them to this class.
 */
class AdminReadings(
    private val scope: CoroutineScope,
    private val client: () -> com.hydraumc.control.network.HydraApiClient?,
    private val onSessionProblem: () -> Unit,
) {
    // The audit record (contract v2, block 4), for administrators.
    val auditRead = mutableStateOf<AuditRead?>(null)
    val auditChain = mutableStateOf<ChainCheck?>(null)
    val auditBusy = mutableStateOf(false)
    val auditFailure = mutableStateOf<AuditFailure?>(null)

    // Who connects to the server (the list of clients and the latest sign-ins), for every signed-in account.
    val connectionClients = mutableStateOf<List<ConnectionClient>?>(null)
    val connectionEvents = mutableStateOf<List<ConnectionEvent>?>(null)
    val connectionsBusy = mutableStateOf(false)
    val connectionsFailure = mutableStateOf<ConnectionsFailure?>(null)

    // The access options of the server (administrators only).
    val serverOptions = mutableStateOf<ServerSecurityOptions?>(null)
    val serverOptionsBusy = mutableStateOf(false)
    val serverOptionsFailure = mutableStateOf<ServerOptionsFailure?>(null)
    val serverOptionsSaved = mutableStateOf(false)

    /**
     * Reads the audit record (administrators only): the newest lines for a filter, and with [verify] the server checks the
     * whole record while this app checks the chain of the lines it was given. Never raises an error on the screen:
     * the reason is left in [auditFailure].
     */
    fun refreshAudit(verify: Boolean, type: String?, robotId: Int?, limit: Int) {
        val api = client() ?: run {
            auditFailure.value = AuditFailure.NETWORK
            return
        }
        auditBusy.value = true
        scope.launch {
            try {
                val read = AuditRecord.parse(api.getAuditBody(AuditRecord.query(limit, type, robotId, verify)))
                auditRead.value = read
                auditChain.value = if (verify) AuditRecord.checkChain(read.lines) else null
                auditFailure.value = null
            } catch (e: HydraApiException) {
                auditRead.value = null
                auditChain.value = null
                auditFailure.value = when {
                    e.isSessionProblem -> AuditFailure.SESSION
                    e.httpCode == 403 -> AuditFailure.FORBIDDEN
                    e.isNotFound -> AuditFailure.UNAVAILABLE
                    e.httpCode == null -> AuditFailure.NETWORK
                    else -> AuditFailure.OTHER
                }
                if (e.isSessionProblem) onSessionProblem()
            } catch (_: Exception) {
                auditRead.value = null
                auditChain.value = null
                auditFailure.value = AuditFailure.NETWORK
            } finally {
                auditBusy.value = false
            }
        }
    }

    /** Reads the list of clients and the latest sign-ins; never raises an error on the screen: the reason is left in [connectionsFailure]. */
    fun refreshConnections() {
        val api = client() ?: run {
            connectionsFailure.value = ConnectionsFailure.NETWORK
            return
        }
        connectionsBusy.value = true
        scope.launch {
            try {
                val clients = ConnectionHistory.parseClients(api.getConnectionClientsBody())
                val events = ConnectionHistory.parseEvents(api.getConnectionEventsBody(CONNECTION_EVENTS_SHOWN))
                connectionClients.value = clients
                connectionEvents.value = events
                connectionsFailure.value = null
            } catch (e: HydraApiException) {
                connectionClients.value = null
                connectionEvents.value = null
                connectionsFailure.value = when {
                    e.isSessionProblem -> ConnectionsFailure.SESSION
                    e.isNotFound -> ConnectionsFailure.UNAVAILABLE
                    e.httpCode == null -> ConnectionsFailure.NETWORK
                    else -> ConnectionsFailure.OTHER
                }
                if (e.isSessionProblem) onSessionProblem()
            } catch (_: Exception) {
                connectionClients.value = null
                connectionEvents.value = null
                connectionsFailure.value = ConnectionsFailure.NETWORK
            } finally {
                connectionsBusy.value = false
            }
        }
    }

    private fun serverOptionsFailureOf(e: HydraApiException): ServerOptionsFailure = when {
        e.isSessionProblem -> ServerOptionsFailure.SESSION
        e.isPermissionDenied -> ServerOptionsFailure.FORBIDDEN
        e.isNotFound -> ServerOptionsFailure.UNAVAILABLE
        e.httpCode == null -> ServerOptionsFailure.NETWORK
        else -> ServerOptionsFailure.OTHER
    }

    /** Reads the access options of the server; the reason it could not is left in [serverOptionsFailure], never raised on the screen. */
    fun refreshServerOptions() {
        val api = client() ?: run {
            serverOptionsFailure.value = ServerOptionsFailure.NETWORK
            return
        }
        serverOptionsBusy.value = true
        scope.launch {
            try {
                val options = ServerSecurity.parse(api.getServerConfigBody())
                serverOptions.value = options
                serverOptionsFailure.value = if (options == null) ServerOptionsFailure.OTHER else null
            } catch (e: HydraApiException) {
                serverOptions.value = null
                serverOptionsFailure.value = serverOptionsFailureOf(e)
                if (e.isSessionProblem) onSessionProblem()
            } catch (_: Exception) {
                serverOptions.value = null
                serverOptionsFailure.value = ServerOptionsFailure.NETWORK
            } finally {
                serverOptionsBusy.value = false
            }
        }
    }

    /** Saves what changed in [values] (the screen has already asked about every change that lowers the protection) and shows the options as the server now holds them. */
    fun saveServerOptions(values: ServerSecurityValues) {
        val api = client() ?: return
        val current = serverOptions.value ?: return
        val body = ServerSecurity.patch(current, values) ?: return
        serverOptionsBusy.value = true
        serverOptionsSaved.value = false
        scope.launch {
            try {
                val answer = api.putServerConfigBody(body.toString())
                serverOptions.value = ServerSecurity.parse(answer) ?: current
                serverOptionsFailure.value = null
                serverOptionsSaved.value = true
            } catch (e: HydraApiException) {
                serverOptionsFailure.value = serverOptionsFailureOf(e)
                if (e.isSessionProblem) onSessionProblem()
            } catch (_: Exception) {
                serverOptionsFailure.value = ServerOptionsFailure.NETWORK
            } finally {
                serverOptionsBusy.value = false
            }
        }
    }

    /** The lines of the audit record as the server stores them (`.jsonl`), for sharing; [onReady] gets null when they could not be read. */
    fun exportAudit(type: String?, robotId: Int?, limit: Int, onReady: (String?) -> Unit) {
        val api = client() ?: run {
            onReady(null)
            return
        }
        scope.launch {
            val text = try {
                api.getAuditBody(AuditRecord.query(limit, type, robotId, format = "jsonl"))
            } catch (_: Exception) {
                null
            }
            onReady(text)
        }
    }
}
