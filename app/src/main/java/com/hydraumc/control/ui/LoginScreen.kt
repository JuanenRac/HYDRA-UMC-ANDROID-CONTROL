// =============================================================================
// HYDRA-UMC CONTROL - Login screen for user authentication
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0-or-later - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.hydraumc.control.R
import com.hydraumc.control.ui.theme.*
import com.hydraumc.control.util.BiometricHelper
import com.hydraumc.control.viewmodel.RobotViewModel

/**
 * Login screen allowing user to enter credentials: the same look as the sign-in of the other apps of the family
 * (dark backdrop, the mark, the name, one quiet line, and the fields in a rounded panel).
 * @param viewModel The shared RobotViewModel.
 */
@Composable
fun LoginScreen(viewModel: RobotViewModel) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var username by remember { mutableStateOf(viewModel.loginUsername.value) }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(viewModel.loginRememberMe.value) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    // Server address/port entered right here instead of requiring a trip
    // through Settings first - prefilled from whatever ConnectionPrefs
    // already loaded into the ViewModel (last-used values, or the built-in
    // defaults if this is a first run).
    var ipAddress by remember { mutableStateOf(viewModel.ipAddress.value) }
    var port by remember { mutableStateOf(viewModel.port.value) }
    val isBiometricEnabled = viewModel.isBiometricEnabled.value
    val lastError = viewModel.lastError.value

    val showBiometric = isBiometricEnabled && BiometricHelper.isBiometricAvailable(context)
    val biometricSupportedButDisabled = !isBiometricEnabled && BiometricHelper.isBiometricAvailable(context)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
    )
    val buttonShape = RoundedCornerShape(14.dp)

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A2A33), DarkBackground, DarkBackground))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painter = painterResource(id = R.drawable.ic_hydra_logo), contentDescription = null, modifier = Modifier.size(84.dp))
            Spacer(Modifier.height(14.dp))
            Text("HYDRA-UMC", fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp, color = Color(0xFFEDF7FF))
            Text(stringResource(R.string.splash_tagline), color = IndustrialSteel, fontSize = 13.sp)
            Spacer(Modifier.height(28.dp))

            Box(modifier = Modifier.fillMaxWidth().metallicIndustrial()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.login_subtitle), color = IndustrialSteel, style = MaterialTheme.typography.bodyMedium)

                    // IP/port right on this screen - the owner shouldn't have to
                    // visit Settings first just to point the app at a server.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it },
                            label = { Text(stringResource(R.string.ip_label)) },
                            leadingIcon = { Icon(Icons.Filled.Dns, null) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors,
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text(stringResource(R.string.port_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(96.dp),
                            colors = fieldColors,
                        )
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.username_label)) },
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.password_label)) },
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(if (passwordVisible) R.string.hide_password else R.string.show_password),
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { rememberMe = !rememberMe },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = rememberMe, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.remember_me_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    }

                    Button(
                        onClick = {
                            // login()/connect() both read ipAddress.value/port.value off the
                            // ViewModel rather than taking them as parameters, so this screen's
                            // own fields have to land there first - same values ConnectionPrefs
                            // ends up persisting once connect() succeeds.
                            viewModel.ipAddress.value = ipAddress.trim()
                            viewModel.port.value = port.trim()
                            viewModel.login(username, password, rememberMe)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = buttonShape,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.login_button_label), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    if (showBiometric && activity != null) {
                        FilledTonalButton(
                            onClick = {
                                BiometricHelper.showBiometricPrompt(
                                    activity = activity,
                                    title = context.getString(R.string.biometric_prompt_title),
                                    subtitle = context.getString(R.string.biometric_prompt_subtitle),
                                    onSuccess = {
                                        // Use stored credentials for biometric login, but still
                                        // honor whatever IP/port this screen currently shows.
                                        viewModel.ipAddress.value = ipAddress.trim()
                                        viewModel.port.value = port.trim()
                                        viewModel.login(
                                            viewModel.loginUsername.value,
                                            viewModel.loginPassword.value,
                                            viewModel.loginRememberMe.value,
                                        )
                                    },
                                    onError = { /* User cancelled or failed */ },
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = buttonShape,
                        ) {
                            Icon(Icons.Filled.Fingerprint, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.biometric_login_button))
                        }
                    }

                    if (biometricSupportedButDisabled) {
                        Text(
                            text = stringResource(R.string.biometric_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = IndustrialSteel,
                        )
                    }

                    lastError?.let { error ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
