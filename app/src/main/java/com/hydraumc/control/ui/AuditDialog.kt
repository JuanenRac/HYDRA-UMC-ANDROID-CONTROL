// =============================================================================
// HYDRA-UMC CONTROL - the audit record of the server (administrators only)
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
// What the server wrote down about the changes that matter: reservations, forced releases, arming of the jog,
// changes of the safety limits and global stops. Every line is chained to the one before it by a SHA-256, so a line
// changed or removed later shows when the chain is verified: here against the lines this phone was given, and by the
// server against the whole record. The lines can be shared as the server stores them (.jsonl).
package com.hydraumc.control.ui

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.network.AuditLine
import com.hydraumc.control.network.AuditRecord
import com.hydraumc.control.viewmodel.AuditFailure
import com.hydraumc.control.viewmodel.RobotViewModel

/** The most lines the dialog draws; the filter and the size of the window narrow what the server sends. */
private const val MAX_LINES_DRAWN = 200

private fun typeRes(type: String): Int? = when (type) {
    "claim" -> R.string.audit_type_claim
    "force-claim" -> R.string.audit_type_force_claim
    "release" -> R.string.audit_type_release
    "force-release" -> R.string.audit_type_force_release
    "jog-arm" -> R.string.audit_type_jog_arm
    "safety-limits" -> R.string.audit_type_safety_limits
    "stop-all" -> R.string.audit_type_stop_all
    else -> null
}

private fun outcomeRes(outcome: String): Int? = when (outcome) {
    "ok" -> R.string.audit_outcome_ok
    "refused" -> R.string.audit_outcome_refused
    "failed" -> R.string.audit_outcome_failed
    else -> null
}

@StringRes
private fun failureRes(failure: AuditFailure): Int = when (failure) {
    AuditFailure.FORBIDDEN -> R.string.audit_failure_forbidden
    AuditFailure.UNAVAILABLE -> R.string.audit_failure_unavailable
    AuditFailure.SESSION -> R.string.audit_failure_session
    AuditFailure.NETWORK -> R.string.audit_failure_network
    AuditFailure.OTHER -> R.string.audit_failure_other
}

@Composable
fun AuditDialog(viewModel: RobotViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // 0 is "all"; 1.. are the types of line the server writes.
    var typeIndex by remember { mutableIntStateOf(0) }
    var robotIndex by remember { mutableIntStateOf(0) }
    var limitIndex by remember { mutableIntStateOf(1) }
    var verified by remember { mutableStateOf(false) }

    val robots = viewModel.robots.value
    val type = if (typeIndex == 0) null else AuditRecord.TYPES[typeIndex - 1]
    val robot = if (robotIndex == 0) null else robots.getOrNull(robotIndex - 1)
    val limit = AuditRecord.LIMITS[limitIndex]

    // Read when the dialog opens and whenever a filter changes; verifying is asked for with its own button.
    LaunchedEffect(typeIndex, robotIndex, limitIndex) {
        verified = false
        viewModel.refreshAudit(false, type, robot?.id, limit)
    }

    val read = viewModel.auditRead.value
    val chain = viewModel.auditChain.value
    val failure = viewModel.auditFailure.value
    val busy = viewModel.auditBusy.value

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audit_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.audit_intro), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { typeIndex = (typeIndex + 1) % (AuditRecord.TYPES.size + 1) }) {
                        val label = type?.let { t -> typeRes(t)?.let { stringResource(it) } ?: t } ?: stringResource(R.string.audit_all_types)
                        Text(label, maxLines = 1)
                    }
                    OutlinedButton(onClick = { robotIndex = (robotIndex + 1) % (robots.size + 1) }) {
                        Text(robot?.let { r -> r.name.ifBlank { "#${r.id}" } } ?: stringResource(R.string.audit_all_robots), maxLines = 1)
                    }
                    OutlinedButton(onClick = { limitIndex = (limitIndex + 1) % AuditRecord.LIMITS.size }) {
                        Text(stringResource(R.string.audit_lines_count, limit), maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { viewModel.refreshAudit(false, type, robot?.id, limit); verified = false }, enabled = !busy) {
                        Text(stringResource(R.string.audit_refresh))
                    }
                    Button(onClick = { viewModel.refreshAudit(true, type, robot?.id, limit); verified = true }, enabled = !busy) {
                        Text(stringResource(R.string.audit_verify))
                    }
                    TextButton(
                        onClick = {
                            viewModel.exportAudit(type, robot?.id, limit) { text ->
                                if (text != null) {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        this.type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(send, null))
                                }
                            }
                        },
                        enabled = !busy,
                    ) { Text(stringResource(R.string.audit_share)) }
                }

                if (failure != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(stringResource(failureRes(failure)), color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
                if (read != null && !read.enabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(if (read.lockedOff) R.string.audit_locked_off else R.string.audit_switched_off),
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (verified && read != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val verdict = read.verification
                    if (verdict != null) {
                        if (verdict.ok) {
                            Text(stringResource(R.string.audit_server_ok, verdict.entries), color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                stringResource(R.string.audit_server_broken, verdict.brokenAtSeq?.toString() ?: "?", verdict.reason ?: ""),
                                color = Color(0xFFF87171),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (chain != null) {
                        if (chain.ok) {
                            Text(stringResource(R.string.audit_local_ok, chain.checked, chain.links, chain.gaps), color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                stringResource(
                                    if (chain.reason == "link") R.string.audit_local_broken_link else R.string.audit_local_broken_changed,
                                    chain.brokenAtSeq?.toString() ?: "?",
                                ),
                                color = Color(0xFFF87171),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (read != null && read.lines.isEmpty() && failure == null) {
                    Text(stringResource(R.string.audit_empty), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                // The newest line first.
                read?.lines?.asReversed()?.take(MAX_LINES_DRAWN)?.forEach { line -> AuditLineRow(line, robots.associate { it.id to it.name }) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close_button)) } },
    )
}

@Composable
private fun AuditLineRow(line: AuditLine, robotNames: Map<Int, String>) {
    val typeName = typeRes(line.type)?.let { stringResource(it) } ?: line.type
    val outcomeName = outcomeRes(line.outcome)?.let { stringResource(it) } ?: line.outcome
    val outcomeColor = when (line.outcome) {
        "ok" -> Color(0xFF10B981)
        "refused" -> Color(0xFFF59E0B)
        else -> Color(0xFFF87171)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("#${line.seq}", color = Color.Gray, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text(typeName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Text(outcomeName, color = outcomeColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            line.ts.replace('T', ' ').substringBefore('.').removeSuffix("Z"),
            color = Color.Gray,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
        )
        val who = listOfNotNull(line.username, line.controllerId, line.ip).joinToString("  ")
        if (who.isNotEmpty()) Text(who, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        val robotText = line.robotId?.let { id -> stringResource(R.string.audit_robot_label, robotNames[id]?.ifBlank { null } ?: "#$id") }
        if (robotText != null) Text(robotText, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        if (line.detail.isNotEmpty()) Text(line.detail, color = Color.Gray, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
    }
}
