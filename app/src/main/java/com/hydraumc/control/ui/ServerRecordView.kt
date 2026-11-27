// =============================================================================
// HYDRA-UMC CONTROL - The server's part of the Notices screen: fault record and board health
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.network.BoardHealth
import com.hydraumc.control.network.ReadingProblem
import com.hydraumc.control.network.ServerFault
import com.hydraumc.control.ui.theme.HydraColors
import com.hydraumc.control.ui.theme.Panel
import com.hydraumc.control.viewmodel.ServerReading
import java.text.DateFormat
import java.util.Date

/** What the screen needs: the last read (null before one), whether one is running and how to ask again. */
class ServerRecordUi(val reading: ServerReading?, val busy: Boolean, val onRefresh: () -> Unit)

@StringRes
private fun faultsProblemText(problem: ReadingProblem): Int = when (problem) {
    ReadingProblem.OFF -> R.string.record_faults_off
    else -> commonProblemText(problem)
}

@StringRes
private fun boardsProblemText(problem: ReadingProblem): Int = when (problem) {
    ReadingProblem.OFF -> R.string.record_boards_off
    ReadingProblem.UNAVAILABLE -> R.string.record_boards_unavailable
    else -> commonProblemText(problem)
}

@StringRes
private fun commonProblemText(problem: ReadingProblem): Int = when (problem) {
    ReadingProblem.FORBIDDEN -> R.string.record_forbidden
    ReadingProblem.SESSION -> R.string.connections_failure_session
    ReadingProblem.NETWORK -> R.string.connections_failure_network
    else -> R.string.connections_failure_other
}

@StringRes
private fun severityText(severity: String): Int = when (severity) {
    "info" -> R.string.record_severity_info
    "warning" -> R.string.record_severity_warning
    "critical" -> R.string.record_severity_critical
    else -> R.string.record_severity_error
}

private fun severityColor(severity: String): Color = when (severity) {
    "info" -> HydraColors.Muted
    "warning" -> HydraColors.Amber
    else -> HydraColors.Alert
}

@Composable
fun ServerRecordView(ui: ServerRecordUi) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = ui.onRefresh, enabled = !ui.busy) {
            Text(stringResource(if (ui.busy) R.string.record_reading else R.string.connections_refresh), color = HydraColors.Accent)
        }
        val reading = ui.reading
        if (reading == null) {
            if (!ui.busy) Text(stringResource(R.string.record_not_connected), color = HydraColors.Muted)
            return@Column
        }

        Text(stringResource(R.string.record_faults_title), style = MaterialTheme.typography.titleMedium)
        reading.faultsProblem?.let { Text(stringResource(faultsProblemText(it)), color = HydraColors.Muted) }
        reading.faults?.let { record ->
            Text(stringResource(R.string.record_faults_active, record.active.size), fontWeight = FontWeight.Bold)
            record.active.forEach { FaultRow(it, active = true) }
            if (record.recent.isNotEmpty()) Text(stringResource(R.string.record_faults_history), fontWeight = FontWeight.Bold)
            record.recent.forEach { FaultRow(it, active = false) }
        }

        Text(stringResource(R.string.record_boards_title), style = MaterialTheme.typography.titleMedium)
        reading.boardsProblem?.let { Text(stringResource(boardsProblemText(it)), color = HydraColors.Muted) }
        if (reading.boards.any { !it.online }) Text(stringResource(R.string.record_boards_no_answer), color = HydraColors.Amber)
        val answered = reading.boards.filter { it.online }
        answered.filter { it.heard }.forEach { BoardRow(it) }
        val silent = answered.filterNot { it.heard }.map { it.slot }
        if (silent.isNotEmpty()) {
            Text(stringResource(R.string.record_boards_silent, silent.joinToString(", ")), color = HydraColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FaultRow(fault: ServerFault, active: Boolean) {
    val tint = if (fault.cleared) HydraColors.Ok else severityColor(fault.severity)
    Panel(Modifier.fillMaxWidth(), tint = if (active) tint else null) {
        Column(Modifier.padding(12.dp)) {
            val state = if (fault.cleared) stringResource(R.string.record_cleared) else stringResource(severityText(fault.severity))
            Text("$state - ${fault.code}", color = tint, fontWeight = FontWeight.SemiBold)
            if (fault.text.isNotEmpty()) Text(fault.text)
            val where = listOfNotNull(fault.source, fault.robotId?.let { stringResource(R.string.record_robot, it) }).joinToString(" - ")
            val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(fault.atMs))
            Text("$where - $time", style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted)
        }
    }
}

@Composable
private fun BoardRow(board: BoardHealth) {
    Panel(Modifier.fillMaxWidth(), tint = if (board.flags.isEmpty()) null else HydraColors.Amber) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.record_board_slot, board.slot), fontWeight = FontWeight.SemiBold)
            board.ageMs?.let { Text(stringResource(R.string.record_board_heard, (it / 1000).toInt()), style = MaterialTheme.typography.bodySmall) }
            if (board.flags.isNotEmpty()) Text(board.flags.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = HydraColors.Amber)
            Text(
                stringResource(
                    R.string.record_board_counters,
                    board.txErrors?.toString() ?: "-", board.rxErrors?.toString() ?: "-", board.stalls?.toString() ?: "-", board.uptimeS?.toString() ?: "-",
                ),
                style = MaterialTheme.typography.bodySmall, color = HydraColors.Muted,
            )
        }
    }
}
