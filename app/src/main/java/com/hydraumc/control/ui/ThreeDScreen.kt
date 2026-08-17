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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    
    // We target the specific robot in the Studio UI via URL parameters
    val url = "http://$ip:$port/?hideUI=true&robotId=$selectedId"

    Box(modifier = Modifier.fillMaxSize()) {
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
                    
                    // Hardware Acceleration is essential for WebGL
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            },
            update = { webView ->
                // Refresh if the robot target changed
                if (webView.url != url) {
                    webView.loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Identity Label Overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(8.dp)
        ) {
            Text(
                text = "3D VIEWPORT: ${selectedRobot?.name ?: "UNKNOWN"}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF00E5FF) // Metallic Cyan
            )
            Text(
                text = "MODEL: ${selectedRobot?.model ?: "GENERIC"}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
