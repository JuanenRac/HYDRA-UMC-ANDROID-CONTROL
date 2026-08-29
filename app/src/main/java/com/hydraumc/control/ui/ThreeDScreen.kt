// =============================================================================
// HYDRA-UMC CONTROL - 3D visualization screen using a WebView for the Studio UI
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.hydraumc.control.BuildConfig
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hydraumc.control.viewmodel.RobotViewModel

/**
 * Composable that displays the 3D view by embedding the server's own web
 * interface (the same Three.js viewport HYDRA-UMC STUDIO's browser UI
 * renders) in a WebView. This is the real, working 3D view - the native
 * Filament path (NativeThreeDScreen.kt) needs an actual .glb asset pipeline
 * that doesn't exist yet, so it stays unused rather than half-wired-in.
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

    // Tracks what was last actually pushed into the WebView via loadUrl() -
    // compared against `url` in `update` below (not against webView.url,
    // which the WebView/server can normalize on its own and cause a
    // loadUrl-every-recomposition loop).
    var loadedUrl by remember { mutableStateOf<String?>(null) }

    // Defensive measure from the blank-viewport bug investigation below
    // (the actual root cause was the WebView's own LayoutParams - see the
    // comment on `layoutParams =` in `factory`): calling loadUrl() from
    // inside `factory` starts the page loading before Compose has placed
    // this AndroidView at all, so gating the FIRST loadUrl() on a real
    // nonzero measured size (via onSizeChanged below) means Chromium never
    // even gets a chance to start from a transient zero/placeholder size,
    // whatever LayoutParams end up governing it.
    var webViewSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = Modifier.fillMaxSize()) {
        key(refreshKey) {
            AndroidView(
                factory = { context ->
                    // Real reproduction reports have never been possible to see
                    // past "the robot doesn't show up" - this WebView had no
                    // error surface at all, so a page-load failure (wrong url,
                    // network error, TLS/cert issue) and a page that loads fine
                    // but fails to render the WebGL viewport looked identical
                    // from outside. WebContentsDebugging (debug builds only -
                    // never in a release, since it lets any USB-connected
                    // desktop inspect this WebView's DOM/JS/network, including
                    // the auth token in its URL) lets `chrome://inspect` attach
                    // to this exact WebView for a real console/network trace.
                    // The two callbacks below cover the other half: a real
                    // navigation/HTTP failure now actually reaches logcat
                    // instead of just rendering blank with no signal anywhere.
                    if (BuildConfig.DEBUG) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }

                    WebView(context).apply {
                        // Live-reproduced, and the actual root cause of the
                        // blank-viewport bug - confirmed by loading this
                        // exact URL in a real desktop browser (Edge
                        // headless, same server, same query params): every
                        // element down to the WebGL <canvas> measured a
                        // real, correct height there. STUDIO's own CSS is
                        // fine. Only this WebView, inside Compose's
                        // AndroidView, ever measured zero height - even
                        // after confirming (via onSizeChanged, see below)
                        // that Android had already given it real 1080x1614
                        // bounds before loadUrl() was ever called. Compose's
                        // AndroidView sizes the View through its own
                        // modifier system without necessarily setting real
                        // ViewGroup.LayoutParams on the child before it
                        // attaches - a WebView specifically can establish
                        // its internal Chromium layout viewport from
                        // whatever LayoutParams (or lack of them) were
                        // present at attach time, not from the pixel bounds
                        // it's later resized to. Setting real MATCH_PARENT
                        // LayoutParams explicitly, instead of leaving it to
                        // Compose alone, is the standard fix for this exact
                        // class of "WebView height 0 inside Compose" bug.
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        settings.databaseEnabled = true
                        settings.allowFileAccess = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        // Tried while chasing the blank-viewport bug above
                        // (the real fix ended up being `layoutParams` right
                        // above) - kept anyway since both are correct,
                        // standard settings for a WebView meant to render a
                        // real responsive layout rather than a classic
                        // desktop site: useWideViewPort sizes the layout
                        // viewport to the View's actual bounds instead of a
                        // fixed default (historically 980px); loadWithOverviewMode
                        // keeps that viewport at 1:1 scale.
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        // Appended to (not replacing) the real default Chromium
                        // WebView UA - a previous version replaced it outright,
                        // which drops the browser-engine identification some
                        // WebGL vendor/driver quirk-detection code keys off of.
                        // Server-side/log identification of this embedded
                        // viewport (if ever needed) can match on the appended
                        // token instead of relying on the whole UA string.
                        settings.userAgentString = "${settings.userAgentString} HYDRA-UMC-ANDROID-CONTROL"

                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                super.onReceivedError(view, request, error)
                                if (request.isForMainFrame) {
                                    Log.e("ThreeDScreen", "Main frame load failed for ${request.url}: ${error.description}")
                                }
                            }

                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                if (request.isForMainFrame) {
                                    Log.e("ThreeDScreen", "Main frame HTTP ${errorResponse.statusCode} for ${request.url}")
                                }
                            }
                        }
                        // Real console.error()/warn() calls from the page's own
                        // JS (Three.js, WebGL context creation, React) never
                        // reached logcat at all before this - onReceivedError/
                        // onReceivedHttpError above only cover navigation/HTTP
                        // failures, not a page that loads and runs fine but
                        // fails inside its own JS (e.g. "WebGL not supported"
                        // or a caught renderer exception), which is exactly
                        // the failure mode a blank-but-loaded 3D viewport
                        // points at. Always on, not gated on BuildConfig.DEBUG
                        // like the remote inspector above - this only writes
                        // to this device's own logcat, nothing remote.
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                                Log.println(
                                    when (consoleMessage.messageLevel()) {
                                        ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                                        ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                                        else -> Log.INFO
                                    },
                                    "ThreeDScreenConsole",
                                    "${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})",
                                )
                                return true
                            }
                        }
                        // No loadUrl() here anymore - see webViewSize/update
                        // below for why, and why that's not just a delay but
                        // a real fix for the blank-viewport bug.
                    }
                },
                // `factory` above only runs once per `key(refreshKey)` - without
                // this, a WebView built while ip/port/selectedId/token was still
                // stale (e.g. the JWT not yet propagated to apiClient right after
                // login, or the user picking a different robot/server afterwards)
                // stayed on that stale URL forever, since nothing ever told it to
                // navigate again. Re-issuing loadUrl() only when the computed URL
                // actually changed keeps the embedded page in sync with the
                // ViewModel instead of freezing it at first composition.
                //
                // Also gated on webViewSize being nonzero (see its own comment
                // above) - `update` re-runs on every recomposition, including
                // the one onSizeChanged below triggers the moment the View's
                // real bounds are known, so the very first loadUrl() naturally
                // happens then rather than being scheduled separately.
                update = { webView ->
                    if (loadedUrl != url && webViewSize.height > 0 && webViewSize.width > 0) {
                        webView.loadUrl(url)
                        loadedUrl = url
                    }
                },
                // Without this, leaving this screen (or the key(refreshKey)
                // block above discarding the old instance on a manual
                // refresh) never called WebView.destroy() - the loaded page
                // is STUDIO's own Three.js viewport, which keeps a
                // requestAnimationFrame loop running continuously, so an
                // orphaned WebView kept driving that loop (CPU/GPU/battery)
                // in the background for as long as the GC happened to take
                // to collect it, which WebView gives no real guarantee on.
                onRelease = { webView -> webView.destroy() },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { webViewSize = it }
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
