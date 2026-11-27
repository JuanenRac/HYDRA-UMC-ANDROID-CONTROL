// =============================================================================
// HYDRA-UMC CONTROL - Reading the server's fault record and board health, away from the screen
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// Read on demand (when the person opens the server's part of Notices or taps Refresh), never in the background. What was
// read keeps the address of the server it came from, so it is never shown as another server's.
package com.hydraumc.control.viewmodel

import androidx.compose.runtime.mutableStateOf
import com.hydraumc.control.network.BOARD_SLOTS
import com.hydraumc.control.network.BoardHealth
import com.hydraumc.control.network.FaultRecord
import com.hydraumc.control.network.HydraApiClient
import com.hydraumc.control.network.HydraApiException
import com.hydraumc.control.network.ReadingProblem
import com.hydraumc.control.network.ServerFaults
import com.hydraumc.control.network.readingProblemOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class ServerReading(
    /** The base address of the server this was read from. */
    val server: String,
    val faults: FaultRecord? = null,
    val faultsProblem: ReadingProblem? = null,
    val boards: List<BoardHealth> = emptyList(),
    val boardsProblem: ReadingProblem? = null,
)

class ServerRecord(
    private val scope: CoroutineScope,
    private val client: () -> HydraApiClient?,
    private val onSessionProblem: () -> Unit,
) {
    private val last = mutableStateOf<ServerReading?>(null)
    val busy = mutableStateOf(false)
    private var job: Job? = null

    /** What was read from the server in use; null before a read, and for a read of another server. */
    val reading: ServerReading? get() = last.value?.takeIf { it.server == client()?.baseUrl }

    fun refresh() {
        job?.cancel()
        val api = client()
        if (api == null) {
            last.value = null
            busy.value = false
            return
        }
        busy.value = true
        job = scope.launch {
            var result = ServerReading(api.baseUrl)
            var sessionEnded = false
            try {
                val record = ServerFaults.parse(api.getFaultsBody(ServerFaults.LIMIT))
                result = if (record != null) result.copy(faults = record) else result.copy(faultsProblem = ReadingProblem.UNAVAILABLE)
            } catch (e: HydraApiException) {
                result = result.copy(faultsProblem = readingProblemOf(e))
                sessionEnded = e.isSessionProblem
            }
            if (!sessionEnded) {
                val boards = mutableListOf<BoardHealth>()
                var problem: ReadingProblem? = null
                for (slot in BOARD_SLOTS) {
                    try {
                        val board = api.getBoardHealth(slot)
                        boards += board
                        // A brain that does not answer one slot answers none: the other seven are not asked.
                        if (!board.online) break
                    } catch (e: HydraApiException) {
                        problem = readingProblemOf(e)
                        sessionEnded = e.isSessionProblem
                        break
                    }
                }
                result = result.copy(boards = boards, boardsProblem = problem)
            }
            if (client()?.baseUrl == api.baseUrl) {
                last.value = result
                if (sessionEnded) onSessionProblem()
            }
            busy.value = false
        }
    }
}
