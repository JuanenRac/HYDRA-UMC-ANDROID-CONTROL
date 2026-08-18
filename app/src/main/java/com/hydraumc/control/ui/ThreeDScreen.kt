// =============================================================================
// HYDRA-UMC CONTROL - 3D visualization screen using a WebView for the Studio UI
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hydraumc.control.viewmodel.RobotViewModel

/**
 * Composable that displays the 3D view by embedding the server's web interface.
 * Returns to WebView as the native Filament engine requires .glb assets not yet present.
 * 
 * @param viewModel The shared RobotViewModel containing connection info.
 */
@Composable
fun ThreeDScreen(viewModel: RobotViewModel) {
    val ip = viewModel.ipAddress.value
    val port = viewModel.port.value
    val selectedId = viewModel.selectedRobotId.value ?: 1
    val robots = viewModel.robots.value
    val selectedRobot = robots.find { it.id == selectedId }
    
    // Key used to force WebView recreation on refresh
    var refreshKey by remember { mutableIntStateOf(0) }
    
    // We target the specific robot in the Studio UI via URL parameters
    val token = viewModel.apiClient?.authToken ?: ""
    val url = "http://$ip:$port/?hideUI=true&robotId=$selectedId&token=$token"

    Box(modifier = Modifier.fillMaxSize()) {
        key(refreshKey) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        settings.databaseEnabled = true
                        settings.allowFileAccess = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = "HYDRA-UMC-ANDROID-CONTROL"
                        
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        
                        webViewClient = WebViewClient()
                        loadUrl(url)
                    }
                },
                update = { webView ->
                    // Standard update logic if needed
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Control Bar Overlay
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "3D VIEWPORT: ${selectedRobot?.name ?: "UNKNOWN"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF00E5FF)
                )
                Text(
                    text = "MODEL: ${selectedRobot?.model ?: "GENERIC"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = { refreshKey++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
            }
        }
    }
}
