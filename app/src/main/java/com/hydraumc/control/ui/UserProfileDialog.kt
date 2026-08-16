// =============================================================================
// HYDRA-UMC CONTROL - User profile dialog for managing account details
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.ui.theme.metallicIndustrial
import com.hydraumc.control.ui.theme.HydraButton

/**
 * Dialog that allows the user to view and edit their profile information.
 * @param viewModel The shared RobotViewModel.
 * @param onDismiss Callback to close the dialog.
 */
@Composable
fun UserProfileDialog(viewModel: RobotViewModel, onDismiss: () -> Unit) {
    var username by remember { mutableStateOf(viewModel.loginUsername.value) }
    var password by remember { mutableStateOf(viewModel.loginPassword.value) }
    var email by remember { mutableStateOf(viewModel.loginEmail.value) }

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
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HydraButton(
                            text = stringResource(R.string.cancel_button),
                            onClick = onDismiss,
                            backgroundColor = Color.Gray,
                            modifier = Modifier.weight(1f)
                        )
                        HydraButton(
                            text = stringResource(R.string.accept_button),
                            onClick = {
                                viewModel.saveUserProfile(username, password, email)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}
