// =============================================================================
// HYDRA-UMC CONTROL - Login screen for user authentication
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.hydraumc.control.util.BiometricHelper
import com.hydraumc.control.R
import com.hydraumc.control.viewmodel.RobotViewModel
import com.hydraumc.control.ui.theme.*

/**
 * Login screen allowing user to enter credentials.
 * @param viewModel The shared RobotViewModel.
 */
@Composable
fun LoginScreen(viewModel: RobotViewModel) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var username by remember { mutableStateOf(viewModel.loginUsername.value) }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(viewModel.loginRememberMe.value) }
    val isBiometricEnabled = viewModel.isBiometricEnabled.value
    val lastError = viewModel.lastError.value

    val showBiometric = isBiometricEnabled && BiometricHelper.isBiometricAvailable(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.metallicIndustrial()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
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
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                    )
                    Text(
                        text = stringResource(R.string.remember_me_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                HydraButton(
                    text = stringResource(R.string.login_button_label),
                    onClick = { viewModel.login(username, password, rememberMe) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (showBiometric && activity != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HydraButton(
                        text = stringResource(R.string.biometric_login_button),
                        onClick = {
                            BiometricHelper.showBiometricPrompt(
                                activity = activity,
                                title = context.getString(R.string.biometric_prompt_title),
                                subtitle = context.getString(R.string.biometric_prompt_subtitle),
                                onSuccess = {
                                    // Use stored credentials for biometric login
                                    viewModel.login(
                                        viewModel.loginUsername.value,
                                        viewModel.loginPassword.value,
                                        viewModel.loginRememberMe.value
                                    )
                                },
                                onError = { /* User cancelled or failed */ }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFF455A64) // Grey-Blue
                    )
                }
                
                lastError?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
