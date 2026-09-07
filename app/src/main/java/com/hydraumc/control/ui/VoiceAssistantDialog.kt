// =============================================================================
// HYDRA-UMC CONTROL - In-app voice/text assistant dialog
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.ui.theme.metallicIndustrial
import com.hydraumc.control.viewmodel.RobotViewModel

/**
 * Lets the operator ask the local voice assistant (HYDRA-UMC-VOICE-UI, via
 * Server's POST /api/voice/turn) a question or give it an order - by typing,
 * or by tapping the mic to use this phone's own on-device speech
 * recognition (RecognizerIntent, wired up in MainActivity) exactly like
 * HYDRA-UMC-WATCH's own voice button already does. Never invokes a robot
 * command directly: a reply is text/status only, same real boundary
 * RobotViewModel.sendVoiceTurn()'s own doc comment describes.
 *
 * @param viewModel The shared RobotViewModel - reads/writes its voice-assistant state.
 * @param onStartVoiceRecognition Starts this phone's native speech recognizer (Activity-owned launcher).
 * @param onDismiss Callback to close the dialog.
 */
@Composable
fun VoiceAssistantDialog(
    viewModel: RobotViewModel,
    onStartVoiceRecognition: () -> Unit,
    onDismiss: () -> Unit,
) {
    var transcript by remember { mutableStateOf("") }
    val busy by viewModel.voiceAssistantBusy
    val reply by viewModel.latestVoiceAssistantReply
    val lastError by viewModel.lastError

    AlertDialog(
        onDismissRequest = {
            viewModel.clearVoiceAssistantReply()
            onDismiss()
        },
        confirmButton = {},
        title = { Text(stringResource(R.string.voice_assistant_title), fontWeight = FontWeight.ExtraBold) },
        text = {
            Box(modifier = Modifier.metallicIndustrial()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.voice_assistant_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = transcript,
                            onValueChange = { transcript = it },
                            label = { Text(stringResource(R.string.voice_assistant_input_label)) },
                            singleLine = false,
                            maxLines = 3,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                        )
                        // Same on-device speech recognition HYDRA-UMC-WATCH's own
                        // voice button already uses (RecognizerIntent, requested
                        // via MainActivity) - never audio uploaded to the Server,
                        // only the resulting transcript, same as the typed path
                        // below shares once it exists.
                        IconButton(onClick = onStartVoiceRecognition, enabled = !busy) {
                            Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.voice_start))
                        }
                        IconButton(
                            onClick = {
                                val text = transcript.trim()
                                if (text.isNotEmpty()) {
                                    viewModel.sendVoiceTurn(text)
                                    transcript = ""
                                }
                            },
                            enabled = !busy && transcript.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.voice_send))
                        }
                    }

                    if (busy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    reply?.let { r ->
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = r.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                            )
                            if (r.requiresConfirmation) {
                                Text(
                                    text = stringResource(R.string.voice_assistant_requires_confirmation),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    lastError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.clearVoiceAssistantReply()
                onDismiss()
            }) {
                Text(stringResource(R.string.close_button))
            }
        },
    )
}
