package com.hydraumc.control.ui

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.hydraumc.control.viewmodel.RobotViewModel

@Composable
fun ThreeDScreen(viewModel: RobotViewModel) {
    val ip = viewModel.ipAddress.value
    val port = viewModel.port.value
    val url = "http://$ip:$port"

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // Habilitamos WebGL / Hardware Acceleration forzando settings
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
