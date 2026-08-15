package com.hydraumc.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hydraumc.control.viewmodel.RobotViewModel

@Composable
fun SettingsScreen(viewModel: RobotViewModel) {
    var ipAddress by remember { mutableStateOf(viewModel.ipAddress.value) }
    var port by remember { mutableStateOf(viewModel.port.value) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Conexión con Compute Module 5", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("Dirección IP (CM5)") },
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
    }
}
