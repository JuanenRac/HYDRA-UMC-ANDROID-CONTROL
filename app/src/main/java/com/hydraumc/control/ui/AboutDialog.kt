// =============================================================================
// HYDRA-UMC CONTROL - About dialog with application information
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hydraumc.control.R
import com.hydraumc.control.ui.theme.metallicIndustrial

/**
 * A dialog showing information about the HYDRA-UMC CONTROL application.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CERRAR")
            }
        },
        title = {
            Text("Acerca de HYDRA-UMC CONTROL", fontWeight = FontWeight.Bold)
        },
        text = {
            Box(modifier = Modifier.metallicIndustrial()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Versión 1.0.0", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Desarrollado por JuanenRac", fontWeight = FontWeight.Bold)
                    Text("Electro Hobby 3D", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Control nativo profesional para plataformas robóticas HYDRA-UMC.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}
