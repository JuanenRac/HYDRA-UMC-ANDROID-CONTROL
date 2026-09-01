// =============================================================================
// HYDRA-UMC CONTROL - 3D visualization screen using a WebView for the Studio UI
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D) <electrohobby3d@gmail.com>
// GPL-3.0 - see LICENSE
// =============================================================================
package com.hydraumc.control.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hydraumc.control.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.hydraumc.control.R
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

    // Real feature, not a bug fix: a manual fullscreen-landscape mode with
    // the jog joystick overlaid transparently on each side (game-controller
    // style - JoystickXYPad left thumb, JoystickZColumn right thumb), for
    // operating a robot while watching its live 3D view fill the whole
    // screen. Reuses MainScreen.kt's EXISTING isLandscape-triggered
    // fullscreen bypass (Surface(fillMaxSize) { ThreeDScreen(...) }, which
    // already fires whenever the physical device is rotated while on this
    // tab) rather than duplicating that logic - this button just forces the
    // Activity's requested orientation to landscape, which makes
    // LocalConfiguration's own orientation flip reactively and sails
    // straight into that same already-working bypass. No new fullscreen
    // code path to get wrong, only a new way to reach the existing one.
    val context = LocalContext.current
    val activity = context as? Activity
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var jogStep by remember { mutableStateOf(1.0) }

    // Real bug, found by reading this code rather than a live device (no
    // physical Android hardware available this pass - see this repo's own
    // established rigor of not claiming a fix confirmed without one): the
    // fullscreen button above forces requestedOrientation to LANDSCAPE and
    // NEVER released it except through the in-viewport exit "X"
    // (FullscreenJogOverlay's own onExit). Physically rotating the device
    // back to portrait did nothing while that lock was held - the OS
    // honors the app's own forced orientation over the accelerometer, so
    // the screen stayed landscape-locked until the user found that small
    // on-screen control, reading as "orientation is broken" from outside.
    // Releasing the lock back to UNSPECIFIED the moment isLandscape
    // actually becomes true (i.e. the forced rotation already succeeded)
    // means the button only ever NUDGES the device into landscape once -
    // it never fights a later physical rotation back to portrait, which
    // is the real, expected behavior for a forced-orientation button like
    // this.
    LaunchedEffect(isLandscape) {
        if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Real request from live device testing: the system's own bottom
    // gesture-nav bar (visible by default on a Samsung A13 and most other
    // real devices) stayed on screen in fullscreen landscape mode,
    // undercutting the whole point of "the 3D view fills the entire
    // screen". Hides system bars (immersive, swipe-to-reveal-temporarily)
    // for exactly as long as this composable is in the fullscreen-landscape
    // state, and restores them the moment it isn't - tied to a
    // DisposableEffect so leaving this screen/composition entirely (not
    // just rotating back to portrait) can't leave the app's own status/nav
    // bars hidden behind it.
    DisposableEffect(isLandscape, activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (isLandscape) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Key used to force WebView recreation on refresh
    var refreshKey by remember { mutableIntStateOf(0) }

    // We target the specific robot in the Studio UI via URL parameters.
    // Real bug fix: reads viewModel.authTokenState.value (a real Compose
    // State<String?>), NOT viewModel.apiClient?.authToken directly -
    // apiClient is a plain var and HydraApiClient.authToken is a plain var
    // on it too, so a login or host/port reconnect that happens AFTER this
    // screen has already composed once (e.g. the app auto-connects on a
    // saved profile while the user is already sitting on this tab, or the
    // user changes server settings from elsewhere in the app) never
    // triggered a recomposition here - this val kept re-evaluating to
    // whatever apiClient reference/token happened to be current the LAST
    // time something else recomposed this screen, which could be stale or
    // empty. The embedded WebView then loaded STUDIO's own page with a
    // stale/invalid token, so its OWN WebSocket connection (a completely
    // separate session from this app's native REST/WS calls, which read
    // apiClient directly and kept working fine) never authenticated or
    // never opened at all.
    //
    // Note on the wider investigation (see this repo's own CHANGELOG
    // [0.4.6] and HYDRA-UMC-STUDIO's [0.2.2]/[0.2.8]): the live desync +
    // wrong-camera-panel report this fix responds to was already
    // root-caused mostly on the STUDIO/Server side - a hardcoded :3000 in
    // apiBase.ts (fixed in STUDIO 0.2.8) and, more importantly, the CM5's
    // deployed Server was found serving an hours-stale STUDIO build that
    // predated STUDIO 0.2.2's own real Camera-PIP-vs-disabled-camera fix.
    // [0.4.6] explicitly noted that redeploy was "not yet re-confirmed
    // against a live device". This token-staleness bug is independent of
    // that and real on its own merits (a plain, non-Compose-observable
    // var read from a @Composable is objectively wrong regardless of
    // whether it was the exact live symptom's proximate cause) - fixed
    // here as a genuine defensive correctness fix, not as a claim that it
    // was the actual live root cause instead of the stale-build
    // explanation already on record above.
    val token = viewModel.authTokenState.value ?: ""
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
                        // Real, defensible fix against a whole class of
                        // "this WebView shows a stale build" bugs (one
                        // honest candidate for the reported Android/STUDIO
                        // 3D-view desync, not confirmed as THE root cause -
                        // no live device was available to reproduce it this
                        // pass, see store.tsx's own WS onopen/onmessage/
                        // onclose diagnostics for how to actually pin it
                        // down live). WebView's default LOAD_DEFAULT cache
                        // mode can keep serving an old cached index.html/JS
                        // bundle across app restarts even though
                        // express.static's own Cache-Control: max-age=0
                        // SHOULD force revalidation - this forces a real,
                        // always-fresh fetch instead of trusting the cache
                        // at all. This is a live robot-control surface on a
                        // LAN: correctness beats the marginal bandwidth cost
                        // of never caching.
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
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
            if (!isLandscape) {
                // Only offered from the normal portrait view - once already
                // in the fullscreen landscape mode, the corner "exit"
                // button below is the way back, not this same icon again.
                IconButton(onClick = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }) {
                    Icon(Icons.Default.Fullscreen, contentDescription = stringResource(R.string.threed_fullscreen), tint = Color.White)
                }
            }
            IconButton(onClick = { refreshKey++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
            }
        }

        if (isLandscape) {
            // Real feedback from live testing: the XY/Z joysticks inside
            // this overlay stopped responding to touch, while the WebView's
            // own top control bar and (before this same pass removed it)
            // the native console still worked - this overlay is the ONLY
            // place native Compose touch targets sit directly on top of the
            // embedded WebView's own full-screen bounds (ControlScreen.kt's
            // joysticks live on a completely separate screen, no WebView
            // underneath). A default-z-order Compose sibling of an
            // AndroidView isn't always guaranteed to win hit-testing over
            // that AndroidView's own native touch handling (WebView reads
            // raw touch itself, for the 3D canvas's own orbit-drag) -
            // explicit zIndex is the standard, documented fix to make sure
            // this overlay's pointerInput handlers see the touch first.
            Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
                FullscreenJogOverlay(
                    viewModel = viewModel,
                    selectedRobot = selectedRobot,
                    jogStep = jogStep,
                    onJogStepChange = { jogStep = it },
                    onExit = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED },
                )
            }
        }
        // The E-STOP/play/pause/stop console used to be duplicated inside
        // this 3D viewport too (both portrait and the fullscreen-landscape
        // overlay below) - real feedback from live testing: it's identical
        // to, and redundant with, the one ControlScreen.kt's own Robot
        // Control menu already shows. Removed from both orientations; the
        // 3D viewer is jog/view-only now.
    }
}

/**
 * The joystick overlay + exit button shown in ThreeDScreen's fullscreen
 * landscape mode - see that composable's own comment for why this mode
 * exists and how it's reached. Semi-transparent so the live 3D view stays
 * fully visible underneath, positioned like a game controller's two
 * thumbsticks (XY pad bottom-left, Z column bottom-right) rather than
 * bunched together the way the portrait Control screen shows them.
 */
@Composable
private fun FullscreenJogOverlay(
    viewModel: RobotViewModel,
    selectedRobot: com.hydraumc.control.viewmodel.RobotState?,
    jogStep: Double,
    onJogStepChange: (Double) -> Unit,
    onExit: () -> Unit,
) {
    val enabled = selectedRobot?.online == true
    val onJog: (Int, Int, Int) -> Unit = { dx, dy, dz ->
        viewModel.jogXYZ("robot", dx, dy, dz, jogStep)
    }

    // Exit button - top-right corner, mirroring the control bar's own
    // top-left placement so it's never confused with the refresh/fullscreen
    // buttons that only make sense in the OTHER mode.
    IconButton(
        onClick = onExit,
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopEnd)
            .padding(12.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
    ) {
        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.threed_exit_fullscreen), tint = Color.White)
    }

    // Step-size chips - top-center, out of the way of both thumb zones.
    Row(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopCenter)
            .padding(top = 12.dp)
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(1.0, 10.0, 50.0).forEach { size ->
            FilterChip(selected = jogStep == size, onClick = { onJogStepChange(size) }, label = { Text("${size.toInt()}mm") })
        }
    }

    // Centered on the screen's own vertical axis (CenterStart/CenterEnd) -
    // real request from live testing: anchored to BottomStart/BottomEnd,
    // both thumb zones sat noticeably low on a real phone held landscape,
    // below where a thumb naturally rests. Vertically centered instead,
    // same horizontal edges as before.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.CenterStart)
            .padding(20.dp)
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            JoystickXYPad(onJog = onJog, enabled = enabled)
            Spacer(modifier = Modifier.height(8.dp))
            // Dedicated base-rotation (J1) buttons, right under the XY pad
            // in this same panel - mirrors STUDIO's own new base-rotation
            // buttons and ControlScreen.kt's own placement.
            BaseRotationButtons(
                enabled = enabled,
                onRotate = { direction -> viewModel.jogJ1(direction, jogStep) },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.CenterEnd)
            .padding(20.dp)
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        JoystickZColumn(onJog = onJog, enabled = enabled)
    }
    // The E-STOP/play/pause/stop console that used to sit bottom-center
    // here was identical to, and redundant with, ControlScreen.kt's own
    // Robot Control menu console - removed per live-testing feedback (see
    // this file's other removal note above). This overlay is jog-only now.
}
