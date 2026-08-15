package com.hydraumc.control.ui

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.hydraumc.control.viewmodel.RobotViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ThreeDScreen(viewModel: RobotViewModel) {
    val ip = viewModel.ipAddress.value
    val port = viewModel.port.value
    
    // For now we just load the main web interface inside the WebView,
    // since building a specific 3D-only route on the CM5 Server requires frontend changes too.
    val url = "http://$ip:$port"

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadUrl(url) 
            }
        },
        update = { webView ->
            if (webView.url != url && !url.contains("localhost")) {
                webView.loadUrl(url)
            }
        }
    )
}
