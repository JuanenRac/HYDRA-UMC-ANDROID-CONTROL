// =============================================================================
// HYDRA-UMC CONTROL - 3D visualization screen using a WebView for the Studio UI
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.hydraumc.control.viewmodel.RobotViewModel

/**
 * Composable that displays the 3D view by embedding the server's web interface in a WebView.
 * 
 * @param viewModel The shared RobotViewModel containing connection info.
 */
@Composable
fun ThreeDScreen(viewModel: RobotViewModel) {
    /** Target server IP address. */
    val ip = viewModel.ipAddress.value
    /** Target server port. */
    val port = viewModel.port.value
    /** Full URL to load the 3D visualization from the server. 
     * We add ?hideUI=true to request the server to hide headers/sidebars
     * for a native-like experience. 
     */
    val url = "http://$ip:$port/?hideUI=true"

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // Set a custom User-Agent to help the server identify mobile control app
                settings.userAgentString = "HYDRA-UMC-ANDROID-CONTROL"
                // Enable WebGL / Hardware Acceleration
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
