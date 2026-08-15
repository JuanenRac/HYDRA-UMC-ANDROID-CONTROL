package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hydraumc.control.viewmodel.RobotViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: RobotViewModel) {
    var ipAddress by remember { mutableStateOf(viewModel.ipAddress.value) }
    var port by remember { mutableStateOf(viewModel.port.value) }
    val discoveredServers = viewModel.discoveredServers.value
    val isScanning = viewModel.isScanning.value
    val lastError = viewModel.lastError.value

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Conexión con HYDRA-UMC STUDIO", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("Dirección IP") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Puerto") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            viewModel.ipAddress.value = ipAddress
            viewModel.port.value = port
            viewModel.connect()
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Guardar y Conectar")
        }

        if (lastError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Buscar en la red local", style = MaterialTheme.typography.titleMedium)
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { viewModel.scanNetwork() }) { Text("Escanear") }
            }
        }
        Text(
            "Prueba GET /api/hydra-info en cada IP de tu subred (igual que HYDRA-UMC SUITE).",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (discoveredServers.isEmpty() && !isScanning) {
            Text("Sin resultados todavía.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(discoveredServers) { server ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(server.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${server.host}:${server.port} · ${server.controllerCount} controlador(es), ${server.robotCount} robot(s)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = {
                                ipAddress = server.host
                                port = server.port.toString()
                                viewModel.connectToDiscovered(server)
                            }) { Text("Conectar") }
                        }
                    }
                }
            }
        }
    }
}
