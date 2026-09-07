// =============================================================================
// HYDRA-UMC CONTROL - User profile dialog for managing account details
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.ui.theme.metallicIndustrial
import com.hydraumc.control.ui.theme.HydraButton
import com.hydraumc.control.util.BiometricHelper

/**
 * Dialog that allows the user to view and edit their profile information.
 * @param viewModel The shared RobotViewModel.
 * @param onDismiss Callback to close the dialog.
 */
@Composable
fun UserProfileDialog(viewModel: RobotViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var username by remember { mutableStateOf(viewModel.loginUsername.value) }
    var password by remember { mutableStateOf(viewModel.loginPassword.value) }
    var email by remember { mutableStateOf(viewModel.loginEmail.value) }
    var biometricEnabled by remember { mutableStateOf(viewModel.isBiometricEnabled.value) }

    val hasBiometricHardware = BiometricHelper.isBiometricAvailable(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {}, // Custom button inside text
        title = {
            Text(stringResource(R.string.user_profile_title), fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Box(modifier = Modifier.metallicIndustrial()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.username_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.email_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    if (hasBiometricHardware) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.biometric_setting_label),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = biometricEnabled,
                                onCheckedChange = { biometricEnabled = it }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HydraButton(
                            text = "",
                            icon = Icons.Default.Close,
                            onClick = onDismiss,
                            backgroundColor = Color.Gray,
                            modifier = Modifier.size(56.dp),
                        )
                        HydraButton(
                            text = "",
                            icon = Icons.AutoMirrored.Filled.Logout,
                            onClick = {
                                viewModel.logout()
                                onDismiss()
                            },
                            backgroundColor = Color(0xFFD32F2F),
                            modifier = Modifier.size(56.dp),
                        )
                        HydraButton(
                            text = "",
                            icon = Icons.Default.Check,
                            onClick = {
                                viewModel.saveUserProfile(username, password, email, biometricEnabled)
                                onDismiss()
                            },
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}
